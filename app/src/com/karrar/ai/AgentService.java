package com.karrar.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Background "learning agent": a visible foreground service that reads ONLY the pages the user listed (robots.txt respected, polite delay),
 * stores the cleaned text in the app's private folder and notifies when done. No device-admin, no silent installs, no other apps touched.
 */
public class AgentService extends Service {
    static volatile boolean running = false;
    static volatile boolean stop = false;
    private Thread worker;
    private PowerManager.WakeLock wl;


    // ---------------------------------------------------------------- policy (enforced in code, not by prompting)
    static final String[] BLOCKED_HOSTS = {"youtube.com", "youtu.be", "tiktok.com", "instagram.com", "facebook.com", "fb.com", "x.com", "twitter.com",
            "snapchat.com", "threads.net", "t.me", "telegram.org", "whatsapp.com", "linkedin.com", "reddit.com", "pinterest.com", "discord.com",
            "twitch.tv", "netflix.com", "spotify.com", "play.google.com", "apps.apple.com", "apkpure.com", "uptodown.com"};
    static final String[] BLOCKED_PATH_WORDS = {"login", "signin", "sign-in", "signup", "sign-up", "register", "account", "checkout", "cart", "admin", "password", "oauth", "captcha"};
    static final String[] BLOCKED_EXT = {".apk", ".aab", ".exe", ".msi", ".dmg", ".zip", ".rar", ".7z", ".torrent", ".mp4", ".mkv", ".avi", ".mov", ".mp3", ".wav", ".pdf"};
    static final String[] ALLOWED_HOSTS = {"wikipedia.org", "developer.mozilla.org", "developer.android.com", "kotlinlang.org", "dart.dev", "flutter.dev", "swift.org",
            "react.dev", "nodejs.org", "go.dev", "doc.rust-lang.org", "docs.python.org", "docs.oracle.com", "learn.microsoft.com", "web.dev"};
    static final int MAX_PAGES = 100;

    private static boolean hostMatches(String host, String dom) { return host.equals(dom) || host.endsWith("." + dom); }

    /** returns null when the URL is allowed, otherwise the Arabic reason it was refused. */
    static String check(String url, List<String> ownHosts) {
        try {
            URL u = new URL(url);
            if (!"https".equals(u.getProtocol())) return "غير آمن (ليس https)";
            String h = u.getHost().toLowerCase();
            for (String b : BLOCKED_HOSTS) if (hostMatches(h, b)) return "منصة تمنع الجمع الآلي (" + b + ")";
            String path = (u.getPath() + "?" + (u.getQuery() == null ? "" : u.getQuery())).toLowerCase();
            for (String w : BLOCKED_PATH_WORDS) if (path.contains(w)) return "صفحة حساب أو دخول أو دفع";
            for (String e : BLOCKED_EXT) if (u.getPath().toLowerCase().endsWith(e)) return "ملف تنزيل أو وسائط (" + e + ")";
            for (String a : ALLOWED_HOSTS) if (hostMatches(h, a)) return null;
            if (ownHosts != null) for (String o : ownHosts) if (!o.isEmpty() && hostMatches(h, o)) return null;
            return "غير مدرج في المصادر المسموحة (أضفه في «مواقع أملكها» إن كان لك)";
        } catch (Exception e) { return "رابط غير صالح"; }
    }

    static String policyJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("blocked", new JSONArray(java.util.Arrays.asList(BLOCKED_HOSTS)));
            o.put("allowed", new JSONArray(java.util.Arrays.asList(ALLOWED_HOSTS)));
            o.put("ext", new JSONArray(java.util.Arrays.asList(BLOCKED_EXT)));
            o.put("words", new JSONArray(java.util.Arrays.asList(BLOCKED_PATH_WORDS)));
            o.put("maxPages", MAX_PAGES);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent it, int flags, int startId) {
        if (it != null && "stop".equals(it.getAction())) { stop = true; return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        final String cfg = it == null ? "{}" : it.getStringExtra("cfg");
        startForeground(2001, buildNotif("وكيل التعلّم يعمل في الخلفية", "يجمع الصفحات التي أضفتها فقط", true));
        running = true; stop = false;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bawsala:agent");
        wl.acquire(45 * 60 * 1000L);
        worker = new Thread(new Runnable() { public void run() { work(cfg); } });
        worker.start();
        return START_NOT_STICKY;
    }

    private Notification buildNotif(String title, String text, boolean ongoing) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) {
            String id = ongoing ? "agent" : "agent_done";
            nm.createNotificationChannel(new NotificationChannel(id, ongoing ? "Learning agent" : "Learning agent results",
                    ongoing ? NotificationManager.IMPORTANCE_LOW : NotificationManager.IMPORTANCE_HIGH));
            nb = new Notification.Builder(this, id);
        } else nb = new Notification.Builder(this);
        int small = getResources().getIdentifier("ic_stat", "drawable", getPackageName());
        int big = getResources().getIdentifier("notif_logo", "drawable", getPackageName());
        nb.setSmallIcon(small != 0 ? small : getApplicationInfo().icon).setContentTitle(title).setContentText(text)
          .setStyle(new Notification.BigTextStyle().bigText(text)).setOngoing(ongoing).setAutoCancel(!ongoing).setColor(0xFF4D6BFE);
        if (big != 0) nb.setLargeIcon(android.graphics.BitmapFactory.decodeResource(getResources(), big));
        Intent open = new Intent(this, WebActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        nb.setContentIntent(PendingIntent.getActivity(this, 5, open, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)));
        return nb.build();
    }

    private void progress(int done, int total, long tokens, int saved, String state) {
        try {
            JSONObject o = new JSONObject().put("done", done).put("total", total).put("tokens", tokens).put("saved", saved).put("state", state);
            Store.put("agent_status", o.toString());
            Bridge.pushAgent(o.toString());
        } catch (Exception ignored) { }
    }

    private static boolean allowedByRobots(String url, String ua) {
        try {
            URL u = new URL(url);
            HttpURLConnection c = (HttpURLConnection) new URL(u.getProtocol() + "://" + u.getHost() + "/robots.txt").openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return true;
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String ln; boolean mine = false; String path = u.getPath().isEmpty() ? "/" : u.getPath();
            while ((ln = br.readLine()) != null) {
                String l = ln.trim();
                if (l.toLowerCase().startsWith("user-agent:")) { String a = l.substring(11).trim(); mine = a.equals("*") || ua.toLowerCase().contains(a.toLowerCase()); }
                else if (mine && l.toLowerCase().startsWith("disallow:")) { String d = l.substring(9).trim(); if (!d.isEmpty() && path.startsWith(d)) return false; }
            }
            return true;
        } catch (Exception e) { return true; }
    }

    private static String clean(String html) {
        String s = html.replaceAll("(?is)<(script|style|nav|footer|header|noscript)[^>]*>.*?</\\1>", " ");
        s = s.replaceAll("(?s)<[^>]+>", "\n").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        s = s.replaceAll("[ \\t\\u00a0]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").trim();
        return s;
    }

    private void work(String cfg) {
        int saved = 0, done = 0; long tokens = 0;
        try {
            JSONObject c = new JSONObject(cfg);
            JSONArray urls = c.optJSONArray("urls");
            int delay = Math.max(2, c.optInt("delay", 3));
            long target = c.optLong("target", 2000000000L);
            File dir = new File(getExternalFilesDir(null), "learning");
            dir.mkdirs();
            String ua = "BawsalaLearningBot/1.0 (respects robots.txt)";
            List<String> own = new ArrayList<String>();
            JSONArray oh = c.optJSONArray("own");
            if (oh != null) for (int i = 0; i < oh.length(); i++) own.add(oh.getString(i).trim().toLowerCase());
            List<String> list = new ArrayList<String>();
            int blocked = 0;
            StringBuilder why = new StringBuilder();
            for (int i = 0; urls != null && i < urls.length() && list.size() < MAX_PAGES; i++) {
                String uu = urls.getString(i).trim();
                String r = check(uu, own);
                if (r == null) list.add(uu); else { blocked++; if (why.length() < 1500) why.append(uu).append(" ← ").append(r).append("\n"); }
            }
            Store.put("agent_blocked", why.toString());
            int total = list.size();
            progress(0, total, 0, 0, "running");
            for (String url : list) {
                if (stop || tokens >= target) break;
                try {
                    if (!url.startsWith("http")) { done++; continue; }
                    if (allowedByRobots(url, ua)) {
                        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                        h.setRequestProperty("User-Agent", ua); h.setConnectTimeout(12000); h.setReadTimeout(20000);
                        h.setInstanceFollowRedirects(false);   // never follow a redirect to another site
                        String ct = h.getContentType();
                        if (h.getResponseCode() == 200 && (ct == null || ct.toLowerCase().contains("text/html") || ct.toLowerCase().contains("text/plain"))) {
                            InputStream in = h.getInputStream();
                            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                            byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0 && bo.size() < 3000000) bo.write(b, 0, n);
                            String text = clean(bo.toString("UTF-8"));
                            if (text.length() > 200) {
                                FileOutputStream fo = new FileOutputStream(new File(dir, "page-" + System.currentTimeMillis() + ".txt"));
                                fo.write(("# " + url + "\n\n" + text).getBytes("UTF-8")); fo.close();
                                saved++; tokens += Math.max(1, text.length() / 3);
                            }
                        }
                    }
                } catch (Exception ignored) { }
                done++;
                progress(done, total, tokens, saved, "running");
                try { Thread.sleep(delay * 1000L); } catch (InterruptedException ignored) { }
            }
            String state = stop ? "stopped" : "done";
            progress(done, total, tokens, saved, state);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(2002, buildNotif(stop ? "تم إيقاف وكيل التعلّم" : "تم الشغل بنجاح ✅",
                    "حُفظت " + saved + " صفحة (نحو " + tokens + " توكن) في مجلد التطبيق الخاص. افتح بوصلة للتفاصيل.", false));
        } catch (Exception e) {
            progress(done, 0, tokens, saved, "error");
        } finally {
            running = false; stop = false;
            try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) { }
            stopForeground(true);
            stopSelf();
        }
    }
}

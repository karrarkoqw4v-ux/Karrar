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
            int total = urls == null ? 0 : urls.length();
            List<String> list = new ArrayList<String>();
            for (int i = 0; i < total; i++) list.add(urls.getString(i).trim());
            progress(0, total, 0, 0, "running");
            for (String url : list) {
                if (stop || tokens >= target) break;
                try {
                    if (!url.startsWith("http")) { done++; continue; }
                    if (allowedByRobots(url, ua)) {
                        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                        h.setRequestProperty("User-Agent", ua); h.setConnectTimeout(12000); h.setReadTimeout(20000);
                        if (h.getResponseCode() == 200) {
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

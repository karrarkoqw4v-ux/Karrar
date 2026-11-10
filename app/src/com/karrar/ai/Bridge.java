package com.karrar.ai;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Native services exposed to the HTML/JS UI as window.Android. */
public class Bridge {
    static final int REQ_NOTIF = 9;
    volatile boolean visible = true;
    private boolean askedNotif = false;
    static final int REQ_ALBUM = 1, REQ_CAMERA = 2, REQ_FILE = 3, REQ_VOICE = 4, REQ_PERM = 5, REQ_GOOGLE = 6, REQ_AUDIO = 7, REQ_SAVE = 8;

    private final Activity act;
    private final WebView wv;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final HashMap<Integer, Api.Handle> handles = new HashMap<Integer, Api.Handle>();
    private int idAlbum, idCamera, idFile, idVoice, idGallery, idGoogle, idSave;
    private byte[] pendingSave;
    private SpeechRecognizer sr;
    private int idLive;
    private TextToSpeech tts;
    private boolean ttsReady;

    public Bridge(Activity a, WebView w) {
        act = a;
        wv = w;
    }

    // ------------------------------------------------------------------ helpers
    private void js(final String script) {
        ui.post(new Runnable() { public void run() { wv.evaluateJavascript(script, null); } });
    }

    private void cb(int id, String json) {
        js("window.__cb(" + id + "," + JSONObject.quote(json) + ")");
    }

    private Bitmap decode(Uri uri, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = act.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth / sample > max * 2 || o.outHeight / sample > max * 2) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            in = act.getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private String b64(Bitmap b, int maxDim, int q) {
        float k = Math.min(1f, maxDim / (float) Math.max(b.getWidth(), b.getHeight()));
        Bitmap s = k < 1f ? Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true) : b;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        s.compress(Bitmap.CompressFormat.JPEG, q, bo);
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    private String thumb(Bitmap b) { return "data:image/jpeg;base64," + b64(b, 300, 78); }

    // ------------------------------------------------------------------ prefs
    @JavascriptInterface
    public String getPref(String k, String d) { return Store.get(k, d); }

    @JavascriptInterface
    public void setPref(String k, String v) {
        Store.put(k, v);
        if ("lang".equals(k)) Theme.rtl = "ar".equals(v);
    }

    @JavascriptInterface
    public boolean hasKey() { return Api.hasKey(); }

    @JavascriptInterface
    public String defaults() {
        try {
            JSONObject o = new JSONObject();
            o.put("lang", "ar").put("theme", "dark").put("base_url", "https://api.deepseek.com")
                    .put("model", "deepseek-chat").put("think_model", "deepseek-reasoner").put("temp", "0.7")
                    .put("stream", "1").put("haptic", "1").put("save_hist", "1").put("font_i", "2").put("voice_lang", "ar");
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ------------------------------------------------------------------ AI
    @JavascriptInterface
    public void chat(final int id, String msgsJson, boolean think, String ctx) {
        try {
            JSONArray a = new JSONArray(msgsJson);
            List<Store.Msg> list = new ArrayList<Store.Msg>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Store.Msg m = new Store.Msg();
                m.role = o.optString("role", "user");
                m.content = o.optString("content", "");
                JSONArray im = o.optJSONArray("images");
                if (im != null) for (int j = 0; j < im.length(); j++) m.images.add(im.getString(j));
                list.add(m);
            }
            if (Build.VERSION.SDK_INT >= 33 && !askedNotif && Store.bool("notify", true)
                    && act.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                askedNotif = true;
                act.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
            }
            final StringBuilder acc = new StringBuilder();
            Api.Handle h = Api.chat(list, think, ctx, new Api.Cb() {
                public void onThink(String d) { ev(id, "think", "d", d); }
                public void onText(String d) { acc.append(d); ev(id, "text", "d", d); }
                public void onDone() { ev(id, "done", null, null); if (!visible && acc.length() > 0) notifyReply(acc.toString()); }
                public void onError(String e) { ev(id, "err", "m", e); }
            }, ui);
            handles.put(id, h);
        } catch (Exception e) {
            ev(id, "err", "m", String.valueOf(e.getMessage()));
        }
    }

    /** Heads-up style notification with the app name and icon when the reply finishes while the app is in the background. */
    private void notifyReply(String text) {
        try {
            if (!Store.bool("notify", true)) return;
            if (Build.VERSION.SDK_INT >= 33 && act.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;
            android.app.NotificationManager nm = (android.app.NotificationManager) act.getSystemService(Context.NOTIFICATION_SERVICE);
            String clean = text.replaceAll("(?s)<think>.*?</think>", "").replaceAll("```[\\s\\S]*?```", " ").replaceAll("[#*`>|_]", "").replaceAll("\\s+", " ").trim();
            if (clean.isEmpty()) return;
            android.app.Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel("replies", Theme.rtl ? "\u0631\u062f\u0648\u062f \u0628\u0648\u0635\u0644\u0629" : "Bawsala replies", android.app.NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
                nb = new android.app.Notification.Builder(act, "replies");
            } else nb = new android.app.Notification.Builder(act);
            int small = act.getResources().getIdentifier("ic_stat", "drawable", act.getPackageName());
            int launcher = act.getResources().getIdentifier("ic_launcher", "mipmap", act.getPackageName());
            nb.setSmallIcon(small != 0 ? small : launcher);
            if (launcher != 0) nb.setLargeIcon(android.graphics.BitmapFactory.decodeResource(act.getResources(), launcher));
            nb.setContentTitle(Theme.rtl ? "\u0628\u0648\u0635\u0644\u0629" : "Bawsala");
            nb.setContentText(clean.length() > 140 ? clean.substring(0, 140) + "\u2026" : clean);
            nb.setStyle(new android.app.Notification.BigTextStyle().bigText(clean.length() > 600 ? clean.substring(0, 600) + "\u2026" : clean));
            nb.setAutoCancel(true);
            nb.setColor(0xFF4D6BFE);
            Intent it = new Intent(act, WebActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            nb.setContentIntent(android.app.PendingIntent.getActivity(act, 0, it, android.app.PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0)));
            nm.notify(1001, nb.build());
        } catch (Throwable ignored) { }
    }

    void clearNotif() {
        try { ((android.app.NotificationManager) act.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(1001); } catch (Throwable ignored) { }
    }

    private void ev(int id, String t, String key, String val) {
        try {
            JSONObject o = new JSONObject().put("t", t);
            if (key != null) o.put(key, val);
            cb(id, o.toString());
        } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public void cancel(int id) {
        Api.Handle h = handles.remove(id);
        if (h != null) h.cancel();
    }

    @JavascriptInterface
    public void search(final int id, final String q) {
        new Thread(new Runnable() {
            public void run() { cb(id, Search.run(q).toString()); }
        }).start();
    }

    /** Downloads an image (GET) or posts JSON (POST) and returns {"ok":true,"mime":..,"b64":..} or {"err":..}. */
    @JavascriptInterface
    public void fetchImage(final int id, final String url, final String bearer, final String body) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(100000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Bawsala/2.0");
                    c.setRequestProperty("Accept", "image/*");
                    if (bearer != null && !bearer.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
                    if (body != null && !body.isEmpty()) {
                        c.setRequestMethod("POST");
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json");
                        java.io.OutputStream os = c.getOutputStream();
                        os.write(body.getBytes("UTF-8"));
                        os.close();
                    }
                    int code = c.getResponseCode();
                    String mime = c.getContentType() == null ? "" : c.getContentType().toLowerCase();
                    if (code >= 400 || !mime.startsWith("image/")) {
                        java.io.InputStream es = code >= 400 ? c.getErrorStream() : c.getInputStream();
                        String t = "";
                        try { if (es != null) { byte[] bb = new byte[400]; int n = es.read(bb); if (n > 0) t = new String(bb, 0, n, "UTF-8"); } } catch (Exception ignored) { }
                        cb(id, new JSONObject().put("err", "HTTP " + code + " " + mime + " " + t).toString());
                        return;
                    }
                    java.io.InputStream in = c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); if (bo.size() > 12 * 1024 * 1024) break; }
                    in.close();
                    cb(id, new JSONObject().put("ok", true).put("mime", mime.split(";")[0]).put("b64", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)).toString());
                } catch (Exception e) {
                    try { cb(id, new JSONObject().put("err", e.getClass().getSimpleName() + ": " + e.getMessage()).toString()); } catch (Exception ignored) { }
                }
            }
        }).start();
    }

    /** Captures what the app currently shows (the WebView) as an image attachment. */
    @JavascriptInterface
    public void screenshot(final int id) {
        ui.post(new Runnable() {
            public void run() {
                try {
                    Bitmap b = Bitmap.createBitmap(Math.max(1, wv.getWidth()), Math.max(1, wv.getHeight()), Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas c = new android.graphics.Canvas(b);
                    wv.draw(c);
                    cb(id, new JSONObject().put("th", thumb(b)).put("b64", b64(b, 1280, 82)).toString());
                } catch (Exception e) {
                    cb(id, "null");
                }
            }
        });
    }

    @JavascriptInterface
    public void diag(final int id) {
        new Thread(new Runnable() { public void run() { cb(id, Api.diagnose()); } }).start();
    }

    /** Small HTTP helper for OAuth device flow etc.: returns {"code":..,"body":..}. */
    @JavascriptInterface
    public void http(final int id, final String method, final String url, final String headersJson, final String body) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(25000);
                    c.setRequestMethod(method);
                    c.setRequestProperty("User-Agent", "Bawsala/2.0");
                    try {
                        JSONObject h = new JSONObject(headersJson == null || headersJson.isEmpty() ? "{}" : headersJson);
                        java.util.Iterator<String> it = h.keys();
                        while (it.hasNext()) { String k = it.next(); c.setRequestProperty(k, h.getString(k)); }
                    } catch (Exception ignored) { }
                    if (body != null && !body.isEmpty() && !method.equals("GET")) {
                        c.setDoOutput(true);
                        java.io.OutputStream os = c.getOutputStream();
                        os.write(body.getBytes("UTF-8"));
                        os.close();
                    }
                    int code = c.getResponseCode();
                    java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    if (in != null) { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0 && bo.size() < 200000) bo.write(b, 0, n); in.close(); }
                    cb(id, new JSONObject().put("code", code).put("body", bo.toString("UTF-8")).toString());
                } catch (Exception e) {
                    try { cb(id, new JSONObject().put("code", 0).put("body", "").put("err", e.getClass().getSimpleName() + ": " + e.getMessage()).toString()); } catch (Exception ignored) { }
                }
            }
        }).start();
    }

    @JavascriptInterface
    public void scanUrl(final int id, final String url, final boolean deep) {
        new Thread(new Runnable() {
            public void run() {
                cb(id, Scan.url(url, deep));
            }
        }).start();
    }

    @JavascriptInterface
    public void test(final int id) {
        new Thread(new Runnable() {
            public void run() {
                String r = Api.test();
                cb(id, r == null ? "null" : JSONObject.quote(r));
            }
        }).start();
    }

    // ------------------------------------------------------------------ accounts
    private String res(String err) {
        try { return err == null ? new JSONObject().put("ok", 1).toString() : new JSONObject().put("err", err).toString(); }
        catch (Exception e) { return "{}"; }
    }

    @JavascriptInterface
    public String login(String email, String pass) { return res(Accounts.login(email, pass)); }

    @JavascriptInterface
    public String hasAccount(String e) { return Accounts.exists(e) ? "1" : "0"; }

    @JavascriptInterface
    public String resetPassword(String e, String p, String p2) { return res(Accounts.reset(e, p, p2)); }

    @JavascriptInterface
    public String signup(String n, String e, String p, String p2) { return res(Accounts.signUp(n, e, p, p2)); }

    /** Native Google account picker (the same sheet every app shows). No browser involved. */
    /** Google accounts this app is allowed to see (Android 8+: those the user picked before). */
    @JavascriptInterface
    public String googleAccounts() {
        try {
            android.accounts.Account[] a = android.accounts.AccountManager.get(act).getAccountsByType("com.google");
            JSONArray r = new JSONArray();
            for (android.accounts.Account x : a) r.put(new JSONObject().put("email", x.name).put("name", nameFromEmail(x.name)));
            return r.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private static final String G_CLIENT = "141595198139-dk1njht8r2j8h184e8053d5m070d1ijp.apps.googleusercontent.com";
    private static final String G_SCHEME = "com.googleusercontent.apps.141595198139-dk1njht8r2j8h184e8053d5m070d1ijp";
    private static final String G_REDIRECT = G_SCHEME + ":/oauth2redirect";
    private String gVerifier, gState;
    private boolean gPending;
    private long gStarted;

    private static String b64url(byte[] b) { return android.util.Base64.encodeToString(b, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP); }

    /* Google sign-in in the browser (OAuth 2.0 + PKCE, no secret): Google's page shows the account list and consent, then returns to the app. */
    @JavascriptInterface
    public void google(int id) { googleAuth(id, ""); }

    private String gExtra = "";

    /** Browser OAuth (PKCE) with extra scopes; returns profile + tokens so Google services (Drive, Gmail, Calendar...) can be used. */
    @JavascriptInterface
    public void googleAuth(int id, String extraScopes) {
        try {
            idGoogle = id;
            gExtra = extraScopes == null ? "" : extraScopes.trim();
            java.security.SecureRandom rnd = new java.security.SecureRandom();
            byte[] v = new byte[48]; rnd.nextBytes(v);
            byte[] st = new byte[16]; rnd.nextBytes(st);
            gVerifier = b64url(v); gState = b64url(st);
            byte[] ch = java.security.MessageDigest.getInstance("SHA-256").digest(gVerifier.getBytes("US-ASCII"));
            String url = "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + java.net.URLEncoder.encode(G_CLIENT, "UTF-8")
                + "&redirect_uri=" + java.net.URLEncoder.encode(G_REDIRECT, "UTF-8")
                + "&response_type=code&scope=" + java.net.URLEncoder.encode(("openid email profile " + gExtra).trim(), "UTF-8")
                + "&code_challenge=" + b64url(ch) + "&code_challenge_method=S256&state=" + gState + (gExtra.isEmpty() ? "&prompt=select_account" : "&access_type=offline&prompt=consent+select_account&include_granted_scopes=true");
            gPending = true; gStarted = System.currentTimeMillis();
            act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            gPending = false;
            try { cb(id, new JSONObject().put("err", String.valueOf(e.getMessage())).toString()); } catch (Exception ignored) { }
        }
    }

    /* called when the browser returns to the app via the redirect scheme */
    public void onNewIntent(Intent it) {
        Uri u = it == null ? null : it.getData();
        if (u == null || !G_SCHEME.equals(u.getScheme()) || !gPending) return;
        gPending = false;
        final int id = idGoogle;
        String err = u.getQueryParameter("error"), code = u.getQueryParameter("code"), state = u.getQueryParameter("state");
        if (err != null) { try { cb(id, new JSONObject().put("err", "access_denied".equals(err) ? "cancel" : err).toString()); } catch (Exception ignored) { } return; }
        if (code == null || state == null || !state.equals(gState)) { try { cb(id, new JSONObject().put("err", "bad response").toString()); } catch (Exception ignored) { } return; }
        final String c = code, ver = gVerifier;
        new Thread(new Runnable() { public void run() {
            try {
                String body = "client_id=" + java.net.URLEncoder.encode(G_CLIENT, "UTF-8") + "&code=" + java.net.URLEncoder.encode(c, "UTF-8")
                    + "&code_verifier=" + java.net.URLEncoder.encode(ver, "UTF-8") + "&grant_type=authorization_code&redirect_uri=" + java.net.URLEncoder.encode(G_REDIRECT, "UTF-8");
                java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL("https://oauth2.googleapis.com/token").openConnection();
                h.setRequestMethod("POST"); h.setDoOutput(true); h.setConnectTimeout(12000); h.setReadTimeout(15000);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                h.getOutputStream().write(body.getBytes("UTF-8"));
                JSONObject t = new JSONObject(readAll(h));
                String at = t.optString("access_token", "");
                if (at.isEmpty()) { cb(id, new JSONObject().put("err", t.optString("error_description", t.optString("error", "token"))).toString()); return; }
                java.net.HttpURLConnection q = (java.net.HttpURLConnection) new java.net.URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection();
                q.setConnectTimeout(12000); q.setReadTimeout(15000); q.setRequestProperty("Authorization", "Bearer " + at);
                JSONObject us = new JSONObject(readAll(q));
                String mail = us.optString("email", "");
                if (mail.isEmpty()) { cb(id, new JSONObject().put("err", "no email").toString()); return; }
                cb(id, new JSONObject().put("email", mail).put("name", us.optString("name", nameFromEmail(mail))).put("picture", us.optString("picture", ""))
                    .put("verified", us.optBoolean("email_verified", false)).put("sub", us.optString("sub", ""))
                    .put("access_token", at).put("refresh_token", t.optString("refresh_token", "")).put("expires_in", t.optInt("expires_in", 3600)).put("scope", t.optString("scope", "")).toString());
            } catch (Exception e) { try { cb(id, new JSONObject().put("err", String.valueOf(e.getMessage())).toString()); } catch (Exception ignored) { } }
        } }).start();
    }

    /* user came back from the browser without finishing -> treat as cancel */
    public void onResumeCheck() {
        if (!gPending || System.currentTimeMillis() - gStarted < 1500) return;
        final int id = idGoogle;
        ui.postDelayed(new Runnable() { public void run() {
            if (gPending) { gPending = false; try { cb(id, "{\"err\":\"cancel\"}"); } catch (Exception ignored) { } }
        } }, 1500);
    }

    private static String readAll(java.net.HttpURLConnection c) throws Exception {
        java.io.InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096]; int n; while (in != null && (n = in.read(b)) > 0) bo.write(b, 0, n);
        return bo.toString("UTF-8");
    }

    private static String nameFromEmail(String email) {
        String n = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        n = n.replaceAll("[._\\-]+", " ").replaceAll("\\d+$", "").trim();
        if (n.isEmpty()) return email;
        StringBuilder sb = new StringBuilder();
        for (String w : n.split(" ")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    @JavascriptInterface public void guest() { Accounts.guest(); }
    @JavascriptInterface public void logout() { Accounts.logout(); }
    @JavascriptInterface public void deleteAccount(String email) { Accounts.delete(email); }

    // ------------------------------------------------------------------ media / files
    @JavascriptInterface
    public void pickImages(int id) {
        idAlbum = id;
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("image/*");
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        start(it, REQ_ALBUM, id);
    }

    @JavascriptInterface
    public void camera(int id) {
        idCamera = id;
        start(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQ_CAMERA, id);
    }

    @JavascriptInterface
    public void pickFile(int id) {
        idFile = id;
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("*/*");
        it.addCategory(Intent.CATEGORY_OPENABLE);
        start(it, REQ_FILE, id);
    }

    private void start(final Intent it, final int req, final int id) {
        ui.post(new Runnable() {
            public void run() {
                try { act.startActivityForResult(it, req); }
                catch (Exception e) { cb(id, req == REQ_FILE ? "{}" : "[]"); }
            }
        });
    }

    private boolean hasGalleryPerm() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        return act.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    @JavascriptInterface
    public void gallery(final int id) {
        idGallery = id;
        if (!hasGalleryPerm()) {
            final String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
            ui.post(new Runnable() { public void run() { act.requestPermissions(new String[]{p}, REQ_PERM); } });
            return;
        }
        loadGallery(id);
    }

    private void loadGallery(final int id) {
        new Thread(new Runnable() {
            public void run() {
                JSONArray items = new JSONArray();
                try {
                    Cursor c = act.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Images.Media._ID}, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
                    if (c != null) {
                        while (c.moveToNext() && items.length() < 14) {
                            Uri u = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(c.getLong(0)));
                            Bitmap bm = decode(u, 260);
                            if (bm != null) items.put(new JSONObject().put("uri", u.toString()).put("th", thumb(bm)));
                        }
                        c.close();
                    }
                    cb(id, new JSONObject().put("perm", true).put("items", items).toString());
                } catch (Exception e) {
                    cb(id, "{\"perm\":true,\"items\":[]}");
                }
            }
        }).start();
    }

    @JavascriptInterface
    public void imageData(final int id, final String uri) {
        new Thread(new Runnable() {
            public void run() {
                Bitmap b = decode(Uri.parse(uri), 1024);
                cb(id, "{\"b64\":\"" + (b == null ? "" : b64(b, 1024, 82)) + "\"}");
            }
        }).start();
    }

    @JavascriptInterface
    public void voice(int id) {
        idVoice = id;
        Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en".equals(Store.get("voice_lang", "ar")) ? "en-US" : "ar-SA");
        start(it, REQ_VOICE, id);
    }

    // ------------------------------------------------------------------ live voice (voice conversation screen)
    @JavascriptInterface
    public void voiceStart(final int id) {
        idLive = id;
        ui.post(new Runnable() {
            public void run() {
                if (!SpeechRecognizer.isRecognitionAvailable(act)) { ev(id, "err", "m", "unavailable"); return; }
                if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
                    return;
                }
                startRec();
            }
        });
    }

    private void startRec() {
        destroyRec();
        final int id = idLive;
        sr = SpeechRecognizer.createSpeechRecognizer(act);
        sr.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { }
            public void onBeginningOfSpeech() { }
            public void onBufferReceived(byte[] b) { }
            public void onEndOfSpeech() { }
            public void onEvent(int t, Bundle p) { }
            public void onRmsChanged(float v) {
                try { cb(id, new JSONObject().put("t", "rms").put("v", (double) v).toString()); } catch (Exception ignored) { }
            }
            public void onPartialResults(Bundle b) {
                ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (r != null && !r.isEmpty()) ev(id, "partial", "d", r.get(0));
            }
            public void onResults(Bundle b) {
                ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                ev(id, "final", "d", r != null && !r.isEmpty() ? r.get(0) : "");
            }
            public void onError(int code) {
                try { cb(id, new JSONObject().put("t", "err").put("c", code).toString()); } catch (Exception ignored) { }
            }
        });
        Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en".equals(Store.get("voice_lang", "ar")) ? "en-US" : "ar-SA");
        it.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        it.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        sr.startListening(it);
    }

    private void destroyRec() {
        if (sr != null) {
            try { sr.cancel(); sr.destroy(); } catch (Exception ignored) { }
            sr = null;
        }
    }

    @JavascriptInterface
    public void voiceStop() {
        ui.post(new Runnable() { public void run() { destroyRec(); } });
    }

    /** Called by the activity. */
    public void onResult(int req, int res, final Intent data) {
        final int ok = Activity.RESULT_OK;
        try {
            if (req == REQ_ALBUM) {
                final int id = idAlbum;
                if (res != ok || data == null) { cb(id, "[]"); return; }
                final List<Uri> uris = new ArrayList<Uri>();
                if (data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
                } else if (data.getData() != null) uris.add(data.getData());
                new Thread(new Runnable() {
                    public void run() {
                        JSONArray a = new JSONArray();
                        for (Uri u : uris) {
                            Bitmap full = decode(u, 1024);
                            if (full == null) continue;
                            try { a.put(new JSONObject().put("th", thumb(full)).put("b64", b64(full, 1024, 82))); } catch (Exception ignored) { }
                        }
                        cb(id, a.toString());
                    }
                }).start();
            } else if (req == REQ_CAMERA) {
                int id = idCamera;
                if (res != ok || data == null || data.getExtras() == null) { cb(id, "[]"); return; }
                Bitmap bm = (Bitmap) data.getExtras().get("data");
                JSONArray a = new JSONArray();
                if (bm != null) a.put(new JSONObject().put("th", thumb(bm)).put("b64", b64(bm, 1024, 85)));
                cb(id, a.toString());
            } else if (req == REQ_FILE) {
                final int id = idFile;
                if (res != ok || data == null || data.getData() == null) { cb(id, "{}"); return; }
                final Uri u = data.getData();
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            String name = "file";
                            long size = -1;
                            Cursor c = act.getContentResolver().query(u, null, null, null, null);
                            if (c != null) {
                                if (c.moveToFirst()) {
                                    int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                                    if (ix >= 0) name = c.getString(ix);
                                    int sx = c.getColumnIndex(OpenableColumns.SIZE);
                                    if (sx >= 0 && !c.isNull(sx)) size = c.getLong(sx);
                                }
                                c.close();
                            }
                            final int LIMIT = 6 * 1024 * 1024;
                            if (size > LIMIT) { cb(id, new JSONObject().put("name", name).put("size", size).put("tooBig", true).toString()); return; }
                            InputStream in = act.getContentResolver().openInputStream(u);
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int n, total = 0;
                            while ((n = in.read(buf)) > 0) {
                                total += n;
                                if (total > LIMIT) { in.close(); cb(id, new JSONObject().put("name", name).put("size", (long) total).put("tooBig", true).toString()); return; }
                                bo.write(buf, 0, n);
                            }
                            in.close();
                            byte[] all = bo.toByteArray();
                            cb(id, new JSONObject().put("name", name).put("size", (long) all.length)
                                    .put("b64", Base64.encodeToString(all, Base64.NO_WRAP)).toString());
                        } catch (Exception e) {
                            cb(id, "{}");
                        }
                    }
                }).start();
            } else if (req == REQ_SAVE) {
                int id = idSave;
                if (res == ok && data != null && data.getData() != null && pendingSave != null) {
                    try {
                        java.io.OutputStream os = act.getContentResolver().openOutputStream(data.getData());
                        os.write(pendingSave);
                        os.close();
                        cb(id, JSONObject.quote("ok:" + data.getData().toString() + "|" + data.getData().toString()));
                    } catch (Exception e) { cb(id, JSONObject.quote("err:" + e.getMessage())); }
                } else cb(id, JSONObject.quote("err:cancel"));
                pendingSave = null;
            } else if (req == REQ_GOOGLE) {
                int id = idGoogle;
                cb(id, "{\"err\":\"cancel\"}");
            } else if (req == REQ_VOICE) {
                int id = idVoice;
                String t = "";
                if (res == ok && data != null) {
                    ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (r != null && !r.isEmpty()) t = r.get(0);
                }
                cb(id, JSONObject.quote(t));
            }
        } catch (Exception ignored) { }
    }

    public void onPermission(int req, int[] res) {
        if (req == REQ_AUDIO) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startRec();
            else ev(idLive, "err", "m", "denied");
            return;
        }
        if (req != REQ_PERM) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) loadGallery(idGallery);
        else cb(idGallery, "{\"perm\":false,\"items\":[]}");
    }

    // ------------------------------------------------------------------ speech, clipboard, misc
    @JavascriptInterface
    public void speak(final String text) {
        ui.post(new Runnable() {
            public void run() {
                if (tts == null) {
                    tts = new TextToSpeech(act, new TextToSpeech.OnInitListener() {
                        public void onInit(int status) {
                            ttsReady = status == TextToSpeech.SUCCESS;
                            if (ttsReady) {
                                tts.setLanguage(Theme.rtl ? new Locale("ar") : Locale.US);
                                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                                    public void onStart(String id) { }
                                    public void onDone(String id) { js("window.__ttsDone&&window.__ttsDone()"); }
                                    public void onError(String id) { js("window.__ttsDone&&window.__ttsDone()"); }
                                });
                                doSpeak(text);
                            }
                        }
                    });
                } else if (ttsReady) doSpeak(text);
            }
        });
    }

    private void doSpeak(String t) {
        String clean = t.replace("```", " ").replace("*", "").replace("#", "");
        if (clean.length() > 3500) clean = clean.substring(0, 3500);
        try { tts.setSpeechRate(Float.parseFloat(Store.get("tts_rate", "1.0"))); } catch (Exception ignored) { }
        try { tts.setPitch(Float.parseFloat(Store.get("tts_pitch", "1.0"))); } catch (Exception ignored) { }
        try {
            String vn = Store.get("tts_voice", "");
            boolean set = false;
            if (!vn.isEmpty() && tts.getVoices() != null)
                for (android.speech.tts.Voice v : tts.getVoices()) if (v.getName().equals(vn)) { tts.setVoice(v); set = true; break; }
            if (!set) {
                String lt = Store.get("tts_locale", "");
                if (!lt.isEmpty()) tts.setLanguage(Locale.forLanguageTag(lt));
            }
        } catch (Exception ignored) { }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "karrar");
    }

    @JavascriptInterface
    public void stopSpeak() {
        ui.post(new Runnable() { public void run() { if (tts != null) tts.stop(); } });
    }

    public void destroy() {
        destroyRec();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }

    @JavascriptInterface
    public void copy(final String t) {
        ui.post(new Runnable() {
            public void run() {
                ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("karrar", t));
            }
        });
    }

    /** System "Save as" picker (works on every Android version). Returns "ok:<uri>|<uri>" or "err:cancel". */
    @JavascriptInterface
    public void saveAs(final int id, final String name, final String mime, final String b64) {
        try {
            idSave = id;
            pendingSave = Base64.decode(b64, Base64.DEFAULT);
            Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
            it.putExtra(Intent.EXTRA_TITLE, name);
            start(it, REQ_SAVE, id);
        } catch (Exception e) {
            cb(id, JSONObject.quote("err:" + e.getMessage()));
        }
    }

    @JavascriptInterface
    public void openUri(final String uri, final String mime) {
        ui.post(new Runnable() {
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.parse(uri), mime == null || mime.isEmpty() ? "*/*" : mime);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(Intent.createChooser(i, "Open"));
                } catch (Exception e) {
                    Toast.makeText(act, "No app can open this file", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /** Installed text-to-speech voices: [{name, locale, net}] */
    @JavascriptInterface
    public void ttsVoices(final int id) {
        ui.post(new Runnable() {
            public void run() {
                final Runnable send = new Runnable() {
                    public void run() {
                        try {
                            JSONArray a = new JSONArray();
                            java.util.Set<android.speech.tts.Voice> vs = tts.getVoices();
                            if (vs != null) for (android.speech.tts.Voice v : vs)
                                a.put(new JSONObject().put("name", v.getName()).put("locale", v.getLocale().toLanguageTag()).put("net", v.isNetworkConnectionRequired()));
                            cb(id, a.toString());
                        } catch (Exception e) { cb(id, "[]"); }
                    }
                };
                if (tts != null && ttsReady) { send.run(); return; }
                tts = new TextToSpeech(act, new TextToSpeech.OnInitListener() {
                    public void onInit(int status) {
                        ttsReady = status == TextToSpeech.SUCCESS;
                        if (ttsReady) {
                            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                                public void onStart(String u) { }
                                public void onDone(String u) { js("window.__ttsDone&&window.__ttsDone()"); }
                                public void onError(String u) { js("window.__ttsDone&&window.__ttsDone()"); }
                            });
                            send.run();
                        } else cb(id, "[]");
                    }
                });
            }
        });
    }

    /** Saves a file (base64) into Downloads/Bawsala. Returns "ok:<where>" or "err:<reason>". */
    @JavascriptInterface
    public String saveFile(String name, String mime, String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                boolean img = mime != null && mime.startsWith("image/");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, img ? "Pictures/Bawsala" : "Download/Bawsala");
                Uri u = act.getContentResolver().insert(img ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) return "err:insert";
                java.io.OutputStream os = act.getContentResolver().openOutputStream(u);
                os.write(data);
                os.close();
                return "ok:" + (img ? "Pictures" : "Download") + "/Bawsala/" + safe + "|" + u.toString();
            }
            java.io.File dir = act.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) return "err:nodir";
            java.io.File f = new java.io.File(dir, safe);
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(data);
            fo.close();
            return "ok:" + f.getAbsolutePath() + "|";
        } catch (Exception e) {
            return "err:" + e.getMessage();
        }
    }

    @JavascriptInterface
    public void share(final String t) {
        ui.post(new Runnable() {
            public void run() {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, t);
                act.startActivity(Intent.createChooser(i, "Share"));
            }
        });
    }

    @JavascriptInterface
    public void open(final String url) {
        ui.post(new Runnable() {
            public void run() {
                try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
            }
        });
    }

    @JavascriptInterface
    public void toast(final String m) {
        ui.post(new Runnable() { public void run() { Toast.makeText(act, m, Toast.LENGTH_SHORT).show(); } });
    }

    @JavascriptInterface
    public void haptic() {
        ui.post(new Runnable() { public void run() { wv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); } });
    }

    @JavascriptInterface
    public void setBars(final String bg, final boolean darkIcons) {
        ui.post(new Runnable() {
            public void run() {
                try {
                    int c = android.graphics.Color.parseColor(bg);
                    act.getWindow().setStatusBarColor(0);
                    act.getWindow().setNavigationBarColor(0);
                    View d = act.getWindow().getDecorView();
                    int f = d.getSystemUiVisibility();
                    if (darkIcons) {
                        f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    } else {
                        f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        if (Build.VERSION.SDK_INT >= 26) f &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                    d.setSystemUiVisibility(f);
                    d.setBackgroundColor(c);
                } catch (Exception ignored) { }
            }
        });
    }

    @JavascriptInterface public void exitApp() { act.finish(); }
    @JavascriptInterface public String deviceModel() { return Build.MODEL; }
}

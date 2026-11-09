package com.karrar.ai;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Google sign-in: OAuth 2.0 authorization-code flow with PKCE through the system browser,
 * using a loopback redirect (http://127.0.0.1:PORT) as allowed for Google "Desktop" clients.
 */
public final class GoogleAuth {
    public static final String CLIENT_ID = "243958943928-q2mp56mk10gmpsnga6vuma4909b3d8nm.apps.googleusercontent.com";

    public interface Cb { void done(String err, String name, String email); }

    private static String b64(byte[] b) { return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }
    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    public static void start(final Activity a, final Cb cb) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                String err = null, name = null, email = null;
                ServerSocket ss = null;
                try {
                    SecureRandom rnd = new SecureRandom();
                    byte[] vb = new byte[48];
                    rnd.nextBytes(vb);
                    final String verifier = b64(vb);
                    final String challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("US-ASCII")));
                    byte[] sb = new byte[16];
                    rnd.nextBytes(sb);
                    final String state = b64(sb);

                    ss = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                    ss.setSoTimeout(180000);
                    final String redirect = "http://127.0.0.1:" + ss.getLocalPort();
                    final String url = "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + enc(CLIENT_ID)
                            + "&redirect_uri=" + enc(redirect) + "&response_type=code&scope=" + enc("openid email profile")
                            + "&code_challenge=" + challenge + "&code_challenge_method=S256&state=" + state + "&prompt=select_account";
                    ui.post(new Runnable() {
                        public void run() {
                            try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
                        }
                    });

                    Socket s = ss.accept();
                    BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
                    String line = r.readLine();
                    String q = "";
                    if (line != null) {
                        String[] p = line.split(" ");
                        if (p.length > 1 && p[1].contains("?")) q = p[1].substring(p[1].indexOf('?') + 1);
                    }
                    String code = param(q, "code"), st = param(q, "state"), e = param(q, "error");
                    boolean ok = code != null && state.equals(st);
                    String html = "<html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'></head>"
                            + "<body style='background:#17181A;color:#EDEDEF;font-family:sans-serif;text-align:center;padding-top:90px' dir=rtl><h2>"
                            + (ok ? "تم تسجيل الدخول ✓" : "تعذّر تسجيل الدخول") + "</h2><p>ارجع إلى تطبيق كرّار AI</p></body></html>";
                    byte[] body = html.getBytes("UTF-8");
                    OutputStream os = s.getOutputStream();
                    os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                    os.write(body);
                    os.flush();
                    s.close();
                    if (!ok) {
                        err = e != null ? e : "state";
                    } else {
                        String post = "code=" + enc(code) + "&client_id=" + enc(CLIENT_ID) + "&code_verifier=" + enc(verifier)
                                + "&redirect_uri=" + enc(redirect) + "&grant_type=authorization_code";
                        String secret = Store.get("google_secret", "").trim();
                        if (!secret.isEmpty()) post += "&client_secret=" + enc(secret);
                        JSONObject tok = http("https://oauth2.googleapis.com/token", post, null);
                        if (tok.has("error")) {
                            err = tok.optString("error_description", tok.optString("error"));
                        } else {
                            JSONObject me = http("https://openidconnect.googleapis.com/v1/userinfo", null, tok.getString("access_token"));
                            email = me.optString("email", "");
                            name = me.optString("name", "");
                            if (email.isEmpty()) err = "no email";
                        }
                    }
                } catch (java.net.SocketTimeoutException t) {
                    err = "timeout";
                } catch (Exception t) {
                    err = t.getClass().getSimpleName() + ": " + t.getMessage();
                } finally {
                    try { if (ss != null) ss.close(); } catch (Exception ignored) { }
                }
                final String fe = err, fn = name, fm = email;
                ui.post(new Runnable() { public void run() { cb.done(fe, fn, fm); } });
            }
        }).start();
    }

    private static String param(String q, String k) throws Exception {
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(k)) return URLDecoder.decode(kv.substring(i + 1), "UTF-8");
        }
        return null;
    }

    private static JSONObject http(String url, String post, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (post != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream os = c.getOutputStream();
            os.write(post.getBytes("UTF-8"));
            os.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        try { return new JSONObject(sb.toString()); } catch (Exception e) { return new JSONObject().put("error", "HTTP " + code); }
    }
}

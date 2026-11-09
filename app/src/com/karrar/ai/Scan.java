package com.karrar.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/** Read-only website inspection: follows redirects, reads TLS certificate, headers and a bit of the page. */
public class Scan {
    private static final int MAX_BODY = 400 * 1024;

    public static String url(String start, boolean deep) {
        JSONObject out = new JSONObject();
        try {
            JSONArray chain = new JSONArray();
            String cur = start;
            JSONObject tls = null;
            JSONObject headers = new JSONObject();
            String body = "";
            String ctype = "";
            int status = 0;
            long t0 = System.currentTimeMillis();
            for (int hop = 0; hop < 8; hop++) {
                URL u = new URL(cur);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(7000);
                c.setReadTimeout(8000);
                c.setRequestMethod("GET");
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Bawsala-Scan/1.0");
                c.setRequestProperty("Accept", "text/html,*/*;q=0.8");
                try {
                    status = c.getResponseCode();
                } catch (javax.net.ssl.SSLException e) {
                    out.put("ssl_error", String.valueOf(e.getMessage()));
                    chain.put(new JSONObject().put("url", cur).put("status", 0));
                    break;
                }
                chain.put(new JSONObject().put("url", cur).put("status", status));
                if (c instanceof HttpsURLConnection) {
                    try {
                        HttpsURLConnection h = (HttpsURLConnection) c;
                        Certificate[] cs = h.getServerCertificates();
                        if (cs.length > 0 && cs[0] instanceof X509Certificate) {
                            X509Certificate x = (X509Certificate) cs[0];
                            tls = new JSONObject();
                            tls.put("subject", x.getSubjectX500Principal().getName());
                            tls.put("issuer", x.getIssuerX500Principal().getName());
                            tls.put("from", x.getNotBefore().getTime());
                            tls.put("to", x.getNotAfter().getTime());
                            tls.put("self", x.getSubjectX500Principal().equals(x.getIssuerX500Principal()));
                            tls.put("cipher", h.getCipherSuite());
                        }
                    } catch (Exception ignored) { }
                }
                if (status >= 300 && status < 400) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null) break;
                    cur = new URL(u, loc).toString();
                    continue;
                }
                Map<String, List<String>> hm = c.getHeaderFields();
                for (Map.Entry<String, List<String>> e : hm.entrySet()) {
                    if (e.getKey() == null) continue;
                    String k = e.getKey().toLowerCase();
                    if (k.equals("server") || k.equals("x-powered-by") || k.equals("content-type") || k.equals("strict-transport-security")
                            || k.equals("content-security-policy") || k.equals("x-frame-options") || k.equals("x-content-type-options")
                            || k.equals("referrer-policy") || k.equals("permissions-policy") || k.equals("set-cookie") || k.equals("content-length"))
                        { StringBuilder jb = new StringBuilder(); for (String v : e.getValue()) { if (jb.length() > 0) jb.append(" | "); jb.append(v); } headers.put(k, jb.toString()); }
                }
                ctype = c.getContentType() == null ? "" : c.getContentType();
                if (ctype.toLowerCase().contains("html") || ctype.toLowerCase().contains("text")) {
                    InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                    if (in != null) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0 && bo.size() < MAX_BODY) bo.write(buf, 0, n);
                        in.close();
                        body = bo.toString("UTF-8");
                    }
                }
                c.disconnect();
                break;
            }
            out.put("ok", true);
            out.put("chain", chain);
            out.put("status", status);
            out.put("final", cur);
            out.put("ms", System.currentTimeMillis() - t0);
            out.put("headers", headers);
            if (tls != null) out.put("tls", tls);
            try {
                String host = new URL(cur).getHost();
                JSONArray ips = new JSONArray();
                for (InetAddress a : InetAddress.getAllByName(host)) ips.put(a.getHostAddress());
                out.put("ips", ips);
            } catch (Exception ignored) { }
            if (!body.isEmpty()) out.put("page", page(body, cur));
        } catch (Exception e) {
            try { out.put("ok", false).put("error", e.getClass().getSimpleName() + ": " + e.getMessage()); } catch (Exception ignored) { }
        }
        return out.toString();
    }

    private static JSONObject page(String html, String base) throws Exception {
        JSONObject p = new JSONObject();
        Matcher m = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (m.find()) p.put("title", m.group(1).trim().replaceAll("\\s+", " "));
        String low = html.toLowerCase();
        p.put("forms", count(low, "<form"));
        p.put("password", count(low, "type=\"password\"") + count(low, "type='password'") + count(low, "type=password"));
        p.put("iframes", count(low, "<iframe"));
        p.put("hiddenIframes", count(low, "display:none") > 0 && count(low, "<iframe") > 0 ? 1 : 0);
        p.put("eval", count(low, "eval(") + count(low, "unescape(") + count(low, "fromcharcode"));
        p.put("atob", count(low, "atob("));
        p.put("metaRefresh", low.contains("http-equiv=\"refresh\"") || low.contains("http-equiv='refresh'"));
        p.put("mixed", count(low, "src=\"http://") + count(low, "src='http://"));
        String bh = "";
        try { bh = new URL(base).getHost(); } catch (Exception ignored) { }
        JSONArray sc = new JSONArray();
        Matcher s = Pattern.compile("<script[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        while (s.find() && sc.length() < 25) {
            try {
                String h = new URL(new URL(base), s.group(1)).getHost();
                if (!h.equals(bh) && seen.add(h)) sc.put(h);
            } catch (Exception ignored) { }
        }
        p.put("extScripts", sc);
        JSONArray act = new JSONArray();
        Matcher f = Pattern.compile("<form[^>]+action=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        while (f.find() && act.length() < 6) {
            try { act.put(new URL(new URL(base), f.group(1)).getHost()); } catch (Exception ignored) { }
        }
        p.put("formHosts", act);
        return p;
    }

    private static int count(String s, String t) {
        int n = 0, i = 0;
        while ((i = s.indexOf(t, i)) >= 0) { n++; i += t.length(); }
        return n;
    }
}

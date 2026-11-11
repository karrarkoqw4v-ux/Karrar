package com.karrar.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keyless web search through DuckDuckGo's HTML endpoint. Returns [{title,url,snippet}]. */
public final class Search {
    public static JSONArray run(String query) {
        JSONArray out = new JSONArray();
        try {
            URL u = new URL("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8"));
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            String html = sb.toString();
            Pattern pa = Pattern.compile("class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
            Pattern ps = Pattern.compile("class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL);
            Matcher ma = pa.matcher(html);
            Matcher ms = ps.matcher(html);
            while (ma.find() && out.length() < 6) {
                String href = ma.group(1);
                String snippet = "";
                if (ms.find(ma.end())) snippet = clean(ms.group(1));
                int i = href.indexOf("uddg=");
                if (i >= 0) {
                    String enc = href.substring(i + 5);
                    int amp = enc.indexOf('&');
                    if (amp > 0) enc = enc.substring(0, amp);
                    href = URLDecoder.decode(enc, "UTF-8");
                } else if (href.startsWith("//")) {
                    href = "https:" + href;
                }
                JSONObject o = new JSONObject();
                o.put("title", clean(ma.group(2)));
                o.put("url", href);
                o.put("snippet", snippet);
                out.put(o);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String clean(String s) {
        return s.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    public static String asContext(JSONArray a) {
        StringBuilder sb = new StringBuilder("Web search results (cite them when useful):\n");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            sb.append(i + 1).append(". ").append(o.optString("title")).append(" - ")
                    .append(o.optString("snippet")).append(" (").append(o.optString("url")).append(")\n");
        }
        return sb.toString();
    }
}

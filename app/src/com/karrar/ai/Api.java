package com.karrar.ai;

import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** OpenAI-compatible streaming client (works with DeepSeek, OpenAI, OpenRouter, Groq, Ollama...). */
public final class Api {
    public interface Cb {
        void onThink(String d);
        void onText(String d);
        void onDone();
        void onError(String e);
    }

    public static class Handle {
        public volatile boolean cancelled = false;
        HttpURLConnection conn;
        public void cancel() {
            cancelled = true;
            try { if (conn != null) conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    public static boolean hasKey() {
        String k = Store.get("api_key", "").trim();
        if (!k.isEmpty()) return true;
        // local servers (Ollama / LM Studio) don't need a key
        String u = Store.baseUrl();
        return u.contains("localhost") || u.contains("127.0.0.1") || u.contains("192.168.");
    }

    public static Handle chat(final List<Store.Msg> history, final boolean think, final String extraCtx,
                              final Cb cb, final Handler ui) {
        final Handle h = new Handle();
        final boolean stream = Store.bool("stream", true);
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!hasKey()) { demo(h, think, history, cb, ui); return; }
                    String model = think ? Store.get("think_model", "deepseek-reasoner") : Store.get("model", "deepseek-chat");
                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("stream", true);
                    if (!think) {
                        try { body.put("temperature", Double.parseDouble(Store.get("temp", "0.7"))); } catch (Exception ignored) { }
                    }
                    JSONArray msgs = new JSONArray();
                    String sys = Store.get("system", "").trim();
                    if (extraCtx != null && extraCtx.length() > 0) sys = (sys.isEmpty() ? "" : sys + "\n\n") + extraCtx;
                    if (!sys.isEmpty()) msgs.put(new JSONObject().put("role", "system").put("content", sys));
                    for (Store.Msg m : history) {
                        if (m.content.isEmpty() && m.role.equals("assistant")) continue;
                        if (m.images != null && !m.images.isEmpty()) {
                            JSONArray parts = new JSONArray();
                            parts.put(new JSONObject().put("type", "text").put("text", m.content.isEmpty() ? "?" : m.content));
                            for (String b64 : m.images)
                                parts.put(new JSONObject().put("type", "image_url")
                                        .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + b64)));
                            msgs.put(new JSONObject().put("role", m.role).put("content", parts));
                        } else {
                            msgs.put(new JSONObject().put("role", m.role).put("content", m.content));
                        }
                    }
                    body.put("messages", msgs);

                    URL u = new URL(Store.baseUrl() + "/chat/completions");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    h.conn = c;
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(120000);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("Accept", "text/event-stream");
                    String key = Store.get("api_key", "").trim();
                    if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
                    OutputStream os = c.getOutputStream();
                    os.write(body.toString().getBytes("UTF-8"));
                    os.close();

                    int code = c.getResponseCode();
                    if (code >= 400) {
                        String err = readAll(c.getErrorStream());
                        String msg = "HTTP " + code;
                        try { msg += ": " + new JSONObject(err).getJSONObject("error").getString("message"); }
                        catch (Exception e) { if (err.length() > 0) msg += ": " + err; }
                        post(ui, cb, null, null, msg, h);
                        return;
                    }
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (h.cancelled) break;
                        if (!line.startsWith("data:")) continue;
                        String d = line.substring(5).trim();
                        if (d.equals("[DONE]")) break;
                        try {
                            JSONObject j = new JSONObject(d);
                            JSONArray ch = j.optJSONArray("choices");
                            if (ch == null || ch.length() == 0) continue;
                            JSONObject delta = ch.getJSONObject(0).optJSONObject("delta");
                            if (delta == null) continue;
                            String rc = delta.optString("reasoning_content", "");
                            if (rc.equals("null")) rc = "";
                            String ct = delta.optString("content", "");
                            if (ct.equals("null")) ct = "";
                            if (!rc.isEmpty()) post(ui, cb, rc, null, null, h);
                            if (!ct.isEmpty()) post(ui, cb, null, ct, null, h);
                        } catch (Exception ignored) { }
                    }
                    r.close();
                    post(ui, cb, null, null, "", h);
                } catch (Exception e) {
                    if (!h.cancelled) post(ui, cb, null, null, e.getClass().getSimpleName() + ": " + e.getMessage(), h);
                    else post(ui, cb, null, null, "", h);
                }
            }
        }).start();
        return h;
    }

    /** err == null -> chunk, err == "" -> done, otherwise error text */
    private static void post(Handler ui, final Cb cb, final String think, final String text, final String err, final Handle h) {
        ui.post(new Runnable() {
            public void run() {
                if (err != null) {
                    if (err.isEmpty()) cb.onDone(); else cb.onError(err);
                } else {
                    if (think != null) cb.onThink(think);
                    if (text != null) cb.onText(text);
                }
            }
        });
    }

    private static String readAll(InputStream in) {
        if (in == null) return "";
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** One-shot connectivity test. Returns null when OK, otherwise an error message. */
    public static String test() {
        try {
            JSONObject body = new JSONObject();
            body.put("model", Store.get("model", "deepseek-chat"));
            body.put("max_tokens", 5);
            body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "hi")));
            HttpURLConnection c = (HttpURLConnection) new URL(Store.baseUrl() + "/chat/completions").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            String key = Store.get("api_key", "").trim();
            if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            int code = c.getResponseCode();
            if (code < 400) return null;
            String err = readAll(c.getErrorStream());
            try { return "HTTP " + code + ": " + new JSONObject(err).getJSONObject("error").getString("message"); }
            catch (Exception e) { return "HTTP " + code; }
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------ demo mode
    private static void demo(final Handle h, boolean think, List<Store.Msg> history, Cb cb, Handler ui) throws Exception {
        boolean ar = Theme.rtl;
        String q = "";
        for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).role.equals("user")) { q = history.get(i).content; break; }
        if (q.length() > 80) q = q.substring(0, 80) + "…";
        if (think) {
            String t = ar
                    ? "المستخدم يسأل: «" + q + "».\nسأحلّل السؤال أولاً، ثم أحدد النقاط الأساسية، وبعدها أرتّب الإجابة بشكل واضح ومختصر.\nبما أنني في الوضع التجريبي، سأعرض شكل الرد فقط."
                    : "The user asks: \"" + q + "\".\nFirst I'll analyse the question, pick the key points, then structure a clear answer.\nSince this is demo mode I'll only show how a reply looks.";
            stream(h, t, true, cb, ui, 14, 30);
        }
        String a = ar
                ? "## وضع تجريبي\nهذا رد تجريبي يوضّح **شكل الإجابات** في التطبيق. سؤالك كان:\n> " + q + "\n\n### للحصول على ردود حقيقية\n1. افتح **الإعدادات** ← **التطبيقات والخدمات المتصلة**.\n2. اختر الخدمة (DeepSeek أو OpenAI أو غيرها).\n3. أدخل **مفتاح API** واضغط اختبار الاتصال.\n\nمثال على كود:\n```python\ndef hello(name):\n    return f\"مرحباً {name}\"\n\nprint(hello(\"كرّار\"))\n```\n- يدعم التطبيق **التفكير العميق** وعرض خطوات التفكير.\n- ويدعم **البحث في الويب** مع عرض المصادر.\n- ويحفظ سجل المحادثات على جهازك."
                : "## Demo mode\nThis is a sample reply showing **how answers look** in the app. You asked:\n> " + q + "\n\n### To get real answers\n1. Open **Settings** → **Connected apps & services**.\n2. Pick a service (DeepSeek, OpenAI, ...).\n3. Enter your **API key** and run the connection test.\n\nExample code:\n```python\ndef hello(name):\n    return f\"Hello {name}\"\n\nprint(hello(\"Karrar\"))\n```\n- Supports **DeepThink** with visible reasoning.\n- Supports **web search** with sources.\n- Keeps your chat history on-device.";
        stream(h, a, false, cb, ui, 10, 18);
        post(ui, cb, null, null, "", h);
    }

    private static void stream(Handle h, String text, boolean think, Cb cb, Handler ui, int step, long delay) throws Exception {
        for (int i = 0; i < text.length() && !h.cancelled; i += 3) {
            String part = text.substring(i, Math.min(text.length(), i + 3));
            post(ui, cb, think ? part : null, think ? null : part, null, h);
            Thread.sleep(delay);
        }
    }
}

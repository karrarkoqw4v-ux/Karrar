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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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


    /** One place a chat request can go (OpenAI-compatible). */
    static class Prov {
        String name, base, url, key = "", model, think;
        boolean keyless;
        Prov(String name, String base, String key, String model, String think) {
            this.name = name; this.base = base; this.key = key == null ? "" : key; this.model = model; this.think = think;
        }
        String endpoint() { return url != null ? url : base + "/chat/completions"; }
        String modelsUrl() { return url != null ? "https://text.pollinations.ai/models" : base + "/models"; }
    }

    private static class Res { boolean got; }

    /** Free tiers of open-model hosts; each is used automatically when the user has pasted its key (Settings). */
    static final String[][] FREE = {
            {"groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "openai/gpt-oss-20b"},
            {"openrouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", "deepseek/deepseek-r1:free"},
            {"hf", "https://router.huggingface.co/v1", "meta-llama/Llama-3.3-70B-Instruct", "deepseek-ai/DeepSeek-R1"},
            {"cerebras", "https://api.cerebras.ai/v1", "llama-3.3-70b", "qwen-3-32b"},
            {"mistral", "https://api.mistral.ai/v1", "open-mistral-nemo", "magistral-small-latest"},
            {"gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemma-3-27b-it", "gemma-3-27b-it"},
            {"github", "https://models.github.ai/inference", "meta/Llama-3.3-70B-Instruct", "deepseek/DeepSeek-R1"}};

    private static final Pattern REPAIR = Pattern.compile("decommission|deprecat|no longer supported|not found|does not exist|unknown model|invalid model|not supported",
            Pattern.CASE_INSENSITIVE);

    static List<Prov> chain() {
        List<Prov> l = new ArrayList<Prov>();
        String key = Store.get("api_key", "").trim();
        String base = Store.baseUrl();
        boolean local = base.contains("localhost") || base.contains("127.0.0.1") || base.contains("192.168.");
        if (!key.isEmpty() || local)
            l.add(new Prov("manual", base, key, Store.get("model", "deepseek-chat"), Store.get("think_model", "deepseek-reasoner")));
        for (String[] f : FREE) {
            String k = Store.get("fk_" + f[0], "").trim();
            if (k.isEmpty()) continue;
            String cached = Store.get("auto_model_" + f[0], "").trim();
            l.add(new Prov(f[0], f[1], k, cached.isEmpty() ? f[2] : cached, f[3]));
        }
        if (Store.bool("auto", true)) {
            // Keyless public gateway, several open-weights models tried one after another
            String[][] pol = {{"pollinations-llama", "llama", "deepseek-reasoning"}, {"pollinations-mistral", "mistral", "mistral"},
                    {"pollinations-deepseek", "deepseek", "deepseek-reasoning"}, {"pollinations-qwen", "qwen-coder", "qwen-coder"}};
            for (String[] q : pol) {
                Prov p = new Prov(q[0], "https://text.pollinations.ai", "", q[1], q[2]);
                p.url = "https://text.pollinations.ai/openai";
                p.keyless = true;
                l.add(p);
            }
        }
        return l;
    }

    public static boolean hasKey() {
        return !chain().isEmpty();
    }

    public static Handle chat(final List<Store.Msg> history, final boolean think, final String extraCtx,
                              final Cb cb, final Handler ui) {
        final Handle h = new Handle();
        new Thread(new Runnable() {
            public void run() {
                try {
                    List<Prov> ch = chain();
                    if (ch.isEmpty()) { demo(h, think, history, cb, ui); return; }
                    JSONArray msgs = new JSONArray();
                    final boolean plan = !think && Store.bool("plan", true);
                    String sys = Store.get("system", "").trim();
                    if (extraCtx != null && extraCtx.length() > 0) sys = (sys.isEmpty() ? "" : sys + "\n\n") + extraCtx;
                    sys = BASE + (plan ? PLAN : "") + (sys.isEmpty() ? "" : "\n\n" + sys);
                    if (!sys.isEmpty()) msgs.put(new JSONObject().put("role", "system").put("content", sys));
                    int lastUser = -1;
                    for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).role.equals("user")) { lastUser = i; break; }
                    for (int hi = 0; hi < history.size(); hi++) {
                        Store.Msg m = history.get(hi);
                        if (m.content.isEmpty() && m.role.equals("assistant")) continue;
                        if (hi == lastUser && FILE_REQ.matcher(m.content).find())
                        { Store.Msg c2 = new Store.Msg(); c2.role = m.role; c2.content = m.content + FILE_HINT; c2.images = m.images; m = c2; }
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
                    String firstErr = null, lastErr = null;
                    for (Prov p : ch) {
                        if (h.cancelled) { post(ui, cb, null, null, "", h); return; }
                        String model = think ? p.think : p.model;
                        if (model == null || model.isEmpty()) model = pickOpenModel(p, "", think);
                        if (model == null) { lastErr = "no model"; continue; }
                        Res r = new Res();
                        String err = attempt(p, model, msgs, think, plan, ui, cb, h, true, r);
                        if (err == null || r.got || h.cancelled) { post(ui, cb, null, null, "", h); return; }
                        if (firstErr == null) firstErr = err;
                        lastErr = err;
                    }
                    post(ui, cb, null, null, firstErr != null ? firstErr : lastErr, h);
                } catch (Exception e) {
                    if (!h.cancelled) post(ui, cb, null, null, e.getClass().getSimpleName() + ": " + e.getMessage(), h);
                    else post(ui, cb, null, null, "", h);
                }
            }
        }).start();
        return h;
    }

    /** One tiny request per configured provider: [{name, model, ok, ms, msg}]. */
    public static String diagnose() {
        JSONArray out = new JSONArray();
        try {
            for (Prov p : chain()) {
                JSONObject r = new JSONObject();
                r.put("name", p.name);
                String model = p.model == null ? "" : p.model;
                r.put("model", model);
                long t0 = System.currentTimeMillis();
                try {
                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("stream", false);
                    body.put("max_tokens", 16);
                    body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "Say hi")));
                    Handle h = new Handle();
                    HttpURLConnection c = open(p, body, false, h);
                    int code = c.getResponseCode();
                    String txt = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
                    if (code >= 400) { r.put("ok", false).put("msg", errText(code, txt)); }
                    else {
                        StringBuilder e = new StringBuilder();
                        boolean ok = false;
                        try {
                            JSONObject j = new JSONObject(txt.trim());
                            JSONArray ch = j.optJSONArray("choices");
                            ok = ch != null && ch.length() > 0 && ch.getJSONObject(0).optJSONObject("message") != null
                                    && ch.getJSONObject(0).getJSONObject("message").optString("content", "").length() > 0;
                            if (!ok && j.has("error")) e.append(j.get("error").toString());
                        } catch (Exception ex) { e.append(txt.length() > 160 ? txt.substring(0, 160) : txt); }
                        r.put("ok", ok);
                        if (!ok) r.put("msg", e.length() > 0 ? e.toString() : "empty reply");
                    }
                } catch (Exception e) {
                    r.put("ok", false).put("msg", e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                r.put("ms", System.currentTimeMillis() - t0);
                out.put(r);
            }
        } catch (Exception ignored) { }
        return out.toString();
    }

    private static HttpURLConnection open(Prov p, JSONObject body, boolean sse, Handle h) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(p.endpoint()).openConnection();
        h.conn = c;
        c.setRequestMethod("POST");
        c.setConnectTimeout(8000);
        c.setReadTimeout(sse ? 35000 : 60000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", sse ? "text/event-stream" : "application/json");
        if (!p.key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + p.key);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        return c;
    }

    private static String errText(int code, String body) {
        String msg = "HTTP " + code;
        try { msg += ": " + new JSONObject(body).getJSONObject("error").getString("message"); }
        catch (Exception e) { if (body.length() > 0) msg += ": " + (body.length() > 300 ? body.substring(0, 300) : body); }
        return msg;
    }

    /** Returns null when the provider answered (or the user cancelled), otherwise the error text. */
    private static String attempt(Prov p, String model, JSONArray msgs, boolean think, boolean plan, Handler ui, Cb cb, Handle h,
                                  boolean allowRepair, Res res) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", true);
            if (!think) {
                try { body.put("temperature", Double.parseDouble(Store.get("temp", "0.7"))); } catch (Exception ignored) { }
            }
            try {
                int mt = Integer.parseInt(Store.get("max_tokens", "0"));
                if (mt > 0) body.put("max_tokens", mt);
            } catch (Exception ignored) { }
            body.put("messages", msgs);
            HttpURLConnection c = open(p, body, true, h);
            int code = c.getResponseCode();
            if (code >= 400) {
                String msg = errText(code, readAll(c.getErrorStream()));
                if (allowRepair && (code == 400 || code == 404 || code == 422) && REPAIR.matcher(msg).find()) {
                    String alt = pickOpenModel(p, model, think);
                    if (alt != null && !alt.equals(model)) {
                        String r2 = attempt(p, alt, msgs, think, plan, ui, cb, h, false, res);
                        if (r2 == null) {
                            if (!p.keyless && !p.name.equals("manual")) Store.put("auto_model_" + p.name, alt);
                            else if (p.name.equals("manual")) { if (think) Store.put("think_model", alt); else Store.put("model", alt); }
                            return null;
                        }
                    }
                }
                return msg;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            final ThinkSplit split = new ThinkSplit();
            String line;
            StringBuilder raw = new StringBuilder();
            while ((line = r.readLine()) != null) {
                if (h.cancelled) break;
                if (!line.startsWith("data:")) { if (raw.length() < 200000) raw.append(line).append('\n'); continue; }
                String d = line.substring(5).trim();
                if (d.equals("[DONE]")) break;
                try {
                    JSONObject j = new JSONObject(d);
                    JSONArray chs = j.optJSONArray("choices");
                    if (chs == null || chs.length() == 0) continue;
                    JSONObject delta = chs.getJSONObject(0).optJSONObject("delta");
                    if (delta == null) continue;
                    String rc = delta.optString("reasoning_content", delta.optString("reasoning", ""));
                    if (rc.equals("null")) rc = "";
                    String ct = delta.optString("content", "");
                    if (ct.equals("null")) ct = "";
                    if (!rc.isEmpty() || !ct.isEmpty()) res.got = true;
                    if (!rc.isEmpty()) post(ui, cb, rc, null, null, h);
                    if (!ct.isEmpty()) { if (plan) split.feed(ct, ui, cb, h); else post(ui, cb, null, ct, null, h); }
                } catch (Exception ignored) { }
            }
            r.close();
            if (plan) split.flush(ui, cb, h);
            if (res.got || h.cancelled) return null;
            // The provider ignored streaming: read a plain JSON body, or ask again without streaming.
            StringBuilder jerr = new StringBuilder();
            if (jsonReply(raw.toString(), plan, ui, cb, h, jerr)) { res.got = true; return null; }
            if (jerr.length() > 0) return jerr.toString();
            body.put("stream", false);
            HttpURLConnection c2 = open(p, body, false, h);
            int code2 = c2.getResponseCode();
            String txt = readAll(code2 >= 400 ? c2.getErrorStream() : c2.getInputStream());
            if (code2 >= 400) return errText(code2, txt);
            if (jsonReply(txt, plan, ui, cb, h, jerr)) { res.got = true; return null; }
            if (jerr.length() > 0) return jerr.toString();
            return "empty reply" + (txt.isEmpty() ? "" : ": " + (txt.length() > 200 ? txt.substring(0, 200) : txt));
        } catch (Exception e) {
            if (h.cancelled) return null;
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /** Picks an open-weights chat model from the provider's own model list (used when a model is retired). */
    private static String pickOpenModel(Prov p, String bad, boolean think) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(p.modelsUrl()).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            if (!p.key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + p.key);
            if (c.getResponseCode() >= 400) return null;
            String txt = readAll(c.getInputStream()).trim();
            List<String> ids = new ArrayList<String>();
            if (txt.startsWith("[")) {
                JSONArray a = new JSONArray(txt);
                for (int i = 0; i < a.length(); i++) {
                    Object o = a.get(i);
                    if (o instanceof JSONObject) { JSONObject jo = (JSONObject) o; ids.add(jo.optString("id", jo.optString("name", ""))); }
                    else ids.add(String.valueOf(o));
                }
            } else {
                JSONObject j = new JSONObject(txt);
                JSONArray a = j.optJSONArray("data");
                if (a == null) a = j.optJSONArray("models");
                if (a != null) for (int i = 0; i < a.length(); i++) {
                    Object o = a.get(i);
                    if (o instanceof JSONObject) { JSONObject jo = (JSONObject) o; ids.add(jo.optString("id", jo.optString("name", ""))); }
                    else ids.add(String.valueOf(o));
                }
            }
            Pattern skip = Pattern.compile("whisper|tts|embed|guard|image|audio|moderation|orpheus|prompt|vision-only|rerank|transcri|speech|safeguard",
                    Pattern.CASE_INSENSITIVE);
            String[] pref = think
                    ? new String[]{"gpt-oss", "r1", "qwen3|qwen-3", "qwq|reasoning|magistral", "deepseek"}
                    : new String[]{"llama-?3\\.3.*70b", "llama-?3\\.1.*(70|8)b", "llama", "gpt-oss", "qwen", "gemma", "mistral", "deepseek"};
            for (String pt : pref) {
                Pattern q = Pattern.compile(pt, Pattern.CASE_INSENSITIVE);
                for (String id : ids) if (!id.isEmpty() && !id.equals(bad) && q.matcher(id).find() && !skip.matcher(id).find()) return id;
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Parses a non-streaming chat completion (or an error body). Returns true when it posted text or an error. */
    private static boolean jsonReply(String body, boolean plan, Handler ui, Cb cb, Handle h, StringBuilder errOut) {
        try {
            String t = body.trim();
            if (t.isEmpty() || t.charAt(0) != '{') return false;
            JSONObject j = new JSONObject(t);
            if (j.has("error")) {
                Object e = j.get("error");
                errOut.append(e instanceof JSONObject ? ((JSONObject) e).optString("message", e.toString()) : String.valueOf(e));
                return false;
            }
            JSONArray ch = j.optJSONArray("choices");
            if (ch == null || ch.length() == 0) return false;
            JSONObject m = ch.getJSONObject(0).optJSONObject("message");
            if (m == null) return false;
            String rc = m.optString("reasoning_content", m.optString("reasoning", ""));
            if (rc.equals("null")) rc = "";
            String ct = m.optString("content", "");
            if (ct.equals("null")) ct = "";
            if (rc.isEmpty() && ct.isEmpty()) return false;
            if (!rc.isEmpty()) post(ui, cb, rc, null, null, h);
            if (!ct.isEmpty()) {
                if (plan) { ThinkSplit sp = new ThinkSplit(); sp.feed(ct, ui, cb, h); sp.flush(ui, cb, h); }
                else post(ui, cb, null, ct, null, h);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
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

    private static final java.util.regex.Pattern FILE_REQ = java.util.regex.Pattern.compile(
            "(\\u0645\\u0644\\u0641|\\bfiles?\\b|\\bzip\\b|\\bdocx?\\b|\\bxlsx?\\b|\\bcsv\\b|\\bpdf\\b|\\bapk\\b|\\u062a\\u062d\\u0645\\u064a\\u0644|download)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern APP_REQ = java.util.regex.Pattern.compile(
            "(\\u062a\\u0637\\u0628\\u064a\\u0642|\\u0644\\u0639\\u0628\\u0629|\\u0645\\u0648\\u0642\\u0639|\\bapp\\b|\\bgame\\b|\\bwebsite\\b|calculator|dashboard)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final String FILE_HINT = "\n\n[System note: the user wants a downloadable file. Deliver it as ONE fenced block whose first line is exactly ```file:<filename.ext> "
            + "(for example ```file:notes.txt) with the complete file content inside, then close with ```. For a .zip put several files separated by lines like === path/file.ext ===. "
            + "Do not describe the file instead of sending it. If the format is binary (apk, pdf, image, video) say you cannot create it and offer a .zip with the source.]";
    private static final String BASE = "You are Bawsala (\u0628\u0648\u0635\u0644\u0629), a helpful assistant. Always answer in the user's language. "
            + "Give detailed, accurate, well-structured answers: start with a direct answer, then explain with ## headings, bullet or numbered lists, "
            + "a table when comparing things, code blocks tagged with the language for code, and short concrete examples; finish with a brief summary or next step when useful. "
            + "Be honest when you are unsure and never invent facts or sources. "
            + "When the user asks you to create or send a file, deliver it as a fenced block whose info string is file:<filename.ext> and whose body is the complete file content, e.g. ```file:notes.md. "
            + "Supported: any text format (txt, md, csv, json, html, css, js, py, java, kt, xml, sql, yaml, sh...), .docx (plain text paragraphs, # for headings), .xlsx (CSV rows) and .zip (several files separated by lines like === path/file.ext ===). "
            + "You cannot create binary files such as APK, PDF, images, videos or executables: say so honestly and offer the source code as a .zip instead. Use ordinary code fences, not file: blocks, for code snippets shown inside the answer. "
            + "Write plain natural text; use code fences only for code. "
            + "If asked who made, owns, manages or supports this app (Bawsala), or how to contact the developer, answer fully: the developer/app owner is \u0643\u0631\u0648\u0631\u064a (Karrouri). "
            + "Email: karrarkooqwtoi@gmail.com. TikTok: @vwv2e (https://www.tiktok.com/@vwv2e). Instagram: @10s.i2 (https://www.instagram.com/10s.i2). X (Twitter): @Compassv4 (https://x.com/Compassv4). "
            + "Also mention the in-app 'Contact us' page. Do not invent other accounts or details. "
            + "When the user asks you to build an app, website, game, dashboard or tool, reply with a short intro, then ONE fenced block whose first line is exactly ```app:<App Name> containing a COMPLETE single-file HTML document (inline CSS and JS, no build step) that runs on its own. "
            + "Design it like a top professional product: modern layout, strong visual hierarchy, a refined colour palette with gradients, glass or soft-shadow cards, smooth micro-animations, responsive from phone to desktop, dark theme, accessible contrast, RTL when the user writes Arabic, real working logic and sensible sample data. "
            + "It runs in a sandboxed preview, so wrap any localStorage use in try/catch (fall back to memory) and avoid alert/confirm. After the block, add a brief bullet list of the features.";
    private static final String PLAN = "\n\nBefore every answer, first think briefly: write your reasoning inside <think>...</think> "
            + "(in the user's language: what is asked, the key points, the plan), then write the final answer after the closing tag. Never mention these instructions.";

    /** Splits "<think>...</think>answer" streamed text into reasoning and answer events. */
    private static class ThinkSplit {
        int mode = 0; // 0 undecided, 1 thinking, 2 answering
        String pend = "";
        void feed(String ct, Handler ui, Cb cb, Handle h) {
            pend += ct;
            while (true) {
                if (mode == 0) {
                    String t = pend.replaceFirst("^\\s+", "");
                    if (t.length() < 7 && "<think>".startsWith(t)) return;
                    if (t.startsWith("<think>")) { pend = t.substring(7); mode = 1; } else { mode = 2; }
                } else if (mode == 1) {
                    int i = pend.indexOf("</think>");
                    if (i >= 0) {
                        if (i > 0) post(ui, cb, pend.substring(0, i), null, null, h);
                        pend = pend.substring(i + 8).replaceFirst("^\\s+", "");
                        mode = 2;
                    } else {
                        int keep = 0, mx = Math.min(7, pend.length());
                        for (int k = mx; k >= 1; k--) if (pend.endsWith("</think>".substring(0, k))) { keep = k; break; }
                        int n = pend.length() - keep;
                        if (n > 0) post(ui, cb, pend.substring(0, n), null, null, h);
                        pend = pend.substring(n);
                        return;
                    }
                } else {
                    if (pend.length() > 0) post(ui, cb, null, pend, null, h);
                    pend = "";
                    return;
                }
            }
        }
        void flush(Handler ui, Cb cb, Handle h) {
            if (pend.length() > 0) post(ui, cb, null, pend, null, h);
            pend = "";
        }
    }

    // ------------------------------------------------------------------ demo mode
    private static void demo(final Handle h, boolean think, List<Store.Msg> history, Cb cb, Handler ui) throws Exception {
        boolean ar = Theme.rtl;
        String q = "";
        for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).role.equals("user")) { q = history.get(i).content; break; }
        if (q.length() > 80) q = q.substring(0, 80) + "…";
        {
            String t = ar
                    ? "المستخدم يسأل: «" + q + "».\nسأحلّل السؤال أولاً، ثم أحدد النقاط الأساسية، وبعدها أرتّب الإجابة بشكل واضح ومختصر.\nبما أنني في الوضع التجريبي، سأعرض شكل الرد فقط."
                    : "The user asks: \"" + q + "\".\nFirst I'll analyse the question, pick the key points, then structure a clear answer.\nSince this is demo mode I'll only show how a reply looks.";
            stream(h, t, true, cb, ui, 14, 30);
        }
        if (APP_REQ.matcher(q).find()) {
            String code = "";
            try {
                java.io.InputStream in = Store.appCtx.getAssets().open("demo_app.html");
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] bb = new byte[4096];
                int n;
                while ((n = in.read(bb)) > 0) bo.write(bb, 0, n);
                in.close();
                code = bo.toString("UTF-8");
            } catch (Exception ignored) { }
            if (!code.isEmpty()) {
                String intro = ar ? "\u0647\u0630\u0627 \u0645\u062b\u0627\u0644 \u062a\u0637\u0628\u064a\u0642 \u062c\u0627\u0647\u0632 \u2014 \u0627\u0636\u063a\u0637 \u00ab\u0639\u0631\u0636\u00bb \u0644\u062a\u062c\u0631\u0628\u062a\u0647 \u0639\u0644\u0649 \u062c\u0647\u0627\u0632 \u0643\u0627\u0645\u0644 (\u0631\u062f \u062a\u062c\u0631\u064a\u0628\u064a):\n\n"
                        : "Here is a ready sample app \u2014 tap \u201cPreview\u201d to try it on a full device (demo reply):\n\n";
                stream(h, intro + "```app:" + (ar ? "\u0645\u0647\u0627\u0645\u064a" : "My Tasks") + "\n" + code + "\n```\n\n" + (ar ? "- \u0642\u0627\u0626\u0645\u0629 \u0645\u0647\u0627\u0645 \u062a\u0641\u0627\u0639\u0644\u064a\u0629\n- \u062f\u0627\u0626\u0631\u0629 \u062a\u0642\u062f\u0645 \u0645\u062a\u062d\u0631\u0643\u0629\n- \u062a\u0635\u0645\u064a\u0645 \u0632\u062c\u0627\u062c\u064a \u062f\u0627\u0643\u0646" : "- Interactive task list\n- Animated progress ring\n- Dark glass design"), false, cb, ui, 10, 3);
                post(ui, cb, null, null, "", h);
                return;
            }
        }
        if (FILE_REQ.matcher(q).find()) {
            String fa = ar
                    ? "\u0623\u0643\u064a\u062f\u060c \u0647\u0630\u0627 \u0645\u0644\u0641 \u062c\u0627\u0647\u0632 \u0644\u0644\u062a\u062d\u0645\u064a\u0644 \ud83d\udc47 (\u0631\u062f \u062a\u062c\u0631\u064a\u0628\u064a)\n\n```file:notes.txt\n\u0623\u0641\u0643\u0627\u0631 \u062a\u0637\u0628\u064a\u0642\u0627\u062a:\n1. \u062a\u0637\u0628\u064a\u0642 \u0645\u0647\u0627\u0645 \u0628\u0648\u0627\u062c\u0647\u0629 \u062f\u0627\u0643\u0646\u0629\n2. \u062a\u0637\u0628\u064a\u0642 \u0645\u0635\u0627\u0631\u064a\u0641 \u0634\u062e\u0635\u064a\u0629\n3. \u062a\u0637\u0628\u064a\u0642 \u062a\u0639\u0644\u0651\u0645 \u0644\u063a\u0627\u062a\n```\n\n\u0627\u0636\u063a\u0637 \u00ab\u062a\u062d\u0645\u064a\u0644\u00bb \u0644\u062d\u0641\u0638\u0647 \u0641\u064a \u0627\u0644\u062a\u0646\u0632\u064a\u0644\u0627\u062a. \u0644\u0644\u0645\u0644\u0641\u0627\u062a \u0627\u0644\u062d\u0642\u064a\u0642\u064a\u0629 \u0623\u0636\u0641 \u0645\u0641\u062a\u0627\u062d API \u0645\u0646 \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a."
                    : "Sure, here is a downloadable file \ud83d\udc47 (demo reply)\n\n```file:notes.txt\nApp ideas:\n1. A to-do app with a dark UI\n2. A personal budget tracker\n3. A language-learning app\n```\n\nTap Download to save it to Downloads. For real files add an API key in Settings.";
            stream(h, fa, false, cb, ui, 10, 14);
            post(ui, cb, null, null, "", h);
            return;
        }
        String a = ar
                ? "## وضع تجريبي\nهذا رد تجريبي يوضّح **شكل الإجابات** في «بوصلة». سؤالك كان:\n> ℹ️ " + q + "\n\n---\n\n## كيف تحصل على ردود حقيقية\n1. افتح **الإعدادات** ← **التطبيقات والخدمات المتصلة**.\n2. اختر الخدمة (DeepSeek أو OpenAI أو غيرها).\n3. أدخل **مفتاح API** ثم اضغط **اختبار الاتصال**.\n\n> 💡 بعد إدخال المفتاح تظهر خطوات تفكير حقيقية قبل كل رد.\n\n### مقارنة سريعة\n| الميزة | الحالة |\n|---|---|\n| التفكير قبل الرد | مفعّل |\n| البحث في الويب | مع المصادر |\n| حفظ المحادثات | على جهازك |\n\n### مثال كود\n```python\ndef hello(name):\n    return f\"مرحباً {name}\"\n\nprint(hello(\"بوصلة\"))\n```\n\n> ⚠️ لا تشارك مفتاح API مع أحد.\n\n**الخلاصة:** أضف المفتاح وسيصبح كل رد مفكَّرًا فيه ومفصّلًا."
                : "## Demo mode\nThis is a sample reply showing **how answers look** in Bawsala. You asked:\n> ℹ️ " + q + "\n\n---\n\n## How to get real answers\n1. Open **Settings** → **Connected apps & services**.\n2. Pick a service (DeepSeek, OpenAI, ...).\n3. Enter your **API key** and run **Test connection**.\n\n> 💡 Once the key is set you will see real reasoning before every reply.\n\n### Quick overview\n| Feature | Status |\n|---|---|\n| Thinking before replies | On |\n| Web search | With sources |\n| Chat history | On your device |\n\n### Example code\n```python\ndef hello(name):\n    return f\"Hello {name}\"\n\nprint(hello(\"Bawsala\"))\n```\n\n> ⚠️ Never share your API key with anyone.\n\n**Summary:** add your key and every reply will be thought through and detailed.";
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

package com.karrar.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Real task runner: each task is researched through OFFICIAL APIs only (Wikipedia, MDN, Stack Exchange), the model summarises the collected sources,
 * and the result is saved on the device as knowledge that the chat can use later. Nothing is simulated; if no model is available the sources are saved without a summary.
 */
final class TaskAgent {
    private TaskAgent() { }

    static final String UA = "BawsalaLearningAgent/1.0 (personal use; official APIs only)";

    // ------------------------------------------------------------ storage
    static File dir(Context c) { File d = new File(c.getFilesDir(), "agent"); d.mkdirs(); return d; }

    static synchronized JSONArray load(Context c, String name) {
        try {
            File f = new File(dir(c), name);
            if (!f.exists()) return new JSONArray();
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            return new JSONArray(bo.toString("UTF-8"));
        } catch (Throwable e) { return new JSONArray(); }
    }

    static synchronized void save(Context c, String name, JSONArray a) {
        try {
            FileOutputStream fo = new FileOutputStream(new File(dir(c), name));
            fo.write(a.toString().getBytes("UTF-8")); fo.close();
        } catch (Throwable ignored) { }
    }

    static synchronized String addTask(Context c, String text) {
        try {
            JSONArray a = load(c, "tasks.json");
            String id = "t" + System.currentTimeMillis();
            a.put(new JSONObject().put("id", id).put("text", text).put("status", "pending").put("created", System.currentTimeMillis()));
            save(c, "tasks.json", a);
            return id;
        } catch (Exception e) { return ""; }
    }

    static synchronized void patch(Context c, String id, JSONObject extra) {
        try {
            JSONArray a = load(c, "tasks.json");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (id.equals(o.optString("id"))) {
                    java.util.Iterator<String> it = extra.keys();
                    while (it.hasNext()) { String k = it.next(); o.put(k, extra.get(k)); }
                }
            }
            save(c, "tasks.json", a);
        } catch (Exception ignored) { }
    }

    static synchronized JSONObject nextPending(Context c) {
        try {
            JSONArray a = load(c, "tasks.json");
            for (int i = 0; i < a.length(); i++) if ("pending".equals(a.getJSONObject(i).optString("status"))) return a.getJSONObject(i);
        } catch (Exception ignored) { }
        return null;
    }

    // ------------------------------------------------------------ network (official APIs only)
    static String get(String url) throws Exception {
        String why = AgentService.check(url, null);
        if (why != null) throw new Exception("blocked: " + why);
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestProperty("User-Agent", UA); h.setRequestProperty("Accept", "application/json");
        h.setConnectTimeout(12000); h.setReadTimeout(25000); h.setInstanceFollowRedirects(false);
        if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
        InputStream in = h.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0 && bo.size() < 4000000) bo.write(b, 0, n);
        return bo.toString("UTF-8");
    }

    static String strip(String html) {
        String s = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ").replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("\\s+", " ").trim();
    }

    static boolean hasArabic(String t) { for (int i = 0; i < t.length(); i++) if (t.charAt(i) >= 0x0600 && t.charAt(i) <= 0x06FF) return true; return false; }

    static List<JSONObject> wikipedia(String q, boolean ar) throws Exception {
        String lang = ar ? "ar" : "en";
        String url = "https://" + lang + ".wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrlimit=3&prop=extracts&explaintext=1&exlimit=3&exchars=5000&gsrsearch=" + URLEncoder.encode(q, "UTF-8");
        JSONObject r = new JSONObject(get(url));
        List<JSONObject> out = new ArrayList<JSONObject>();
        JSONObject pages = r.optJSONObject("query") == null ? null : r.getJSONObject("query").optJSONObject("pages");
        if (pages == null) return out;
        java.util.Iterator<String> it = pages.keys();
        while (it.hasNext()) {
            JSONObject p = pages.getJSONObject(it.next());
            String text = p.optString("extract", "");
            if (text.length() < 100) continue;
            out.add(new JSONObject().put("title", p.optString("title")).put("url", "https://" + lang + ".wikipedia.org/wiki/" + URLEncoder.encode(p.optString("title").replace(' ', '_'), "UTF-8"))
                    .put("license", "CC BY-SA 4.0 (Wikipedia)").put("text", text));
        }
        return out;
    }

    static List<JSONObject> mdn(String q) throws Exception {
        JSONObject r = new JSONObject(get("https://developer.mozilla.org/api/v1/search?locale=en-US&size=3&q=" + URLEncoder.encode(q, "UTF-8")));
        JSONArray docs = r.optJSONArray("documents");
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (int i = 0; docs != null && i < docs.length(); i++) {
            JSONObject d = docs.getJSONObject(i);
            String text = d.optString("summary", "");
            try {
                JSONObject full = new JSONObject(get("https://developer.mozilla.org" + d.optString("mdn_url") + "/index.json")).optJSONObject("doc");
                JSONArray body = full == null ? null : full.optJSONArray("body");
                StringBuilder sb = new StringBuilder(text).append("\n");
                for (int j = 0; body != null && j < body.length() && sb.length() < 5000; j++) {
                    JSONObject v = body.getJSONObject(j).optJSONObject("value");
                    if (v != null) sb.append(strip(v.optString("content", ""))).append("\n");
                }
                text = sb.toString();
            } catch (Exception ignored) { }
            if (text.length() < 40) continue;
            out.add(new JSONObject().put("title", d.optString("title")).put("url", "https://developer.mozilla.org" + d.optString("mdn_url"))
                    .put("license", "CC BY-SA 2.5 (MDN)").put("text", text.length() > 5000 ? text.substring(0, 5000) : text));
        }
        return out;
    }

    static List<JSONObject> stack(String q) throws Exception {
        JSONObject r = new JSONObject(get("https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&accepted=True&pagesize=3&site=stackoverflow&filter=withbody&q=" + URLEncoder.encode(q, "UTF-8")));
        JSONArray items = r.optJSONArray("items");
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            String ans = "";
            try {
                JSONObject ar = new JSONObject(get("https://api.stackexchange.com/2.3/answers/" + it.optLong("accepted_answer_id") + "?site=stackoverflow&filter=withbody"));
                JSONArray ai = ar.optJSONArray("items");
                if (ai != null && ai.length() > 0) ans = strip(ai.getJSONObject(0).optString("body", ""));
            } catch (Exception ignored) { }
            String text = "Question: " + strip(it.optString("body", "")) + "\nAccepted answer: " + ans;
            if (ans.isEmpty()) continue;
            out.add(new JSONObject().put("title", strip(it.optString("title"))).put("url", it.optString("link")).put("license", "CC BY-SA (Stack Overflow, attribution: link)")
                    .put("text", text.length() > 5000 ? text.substring(0, 5000) : text));
        }
        return out;
    }

    // ------------------------------------------------------------ model call (the app's own provider chain)
    static String ask(final String system, final String user, int seconds) {
        final StringBuilder out = new StringBuilder();
        final StringBuilder err = new StringBuilder();
        final CountDownLatch done = new CountDownLatch(1);
        List<Store.Msg> h = new ArrayList<Store.Msg>();
        Store.Msg m = new Store.Msg(); m.role = "user"; m.content = user; h.add(m);
        try {
            Api.chat(h, false, system, new Api.Cb() {
                public void onThink(String d) { }
                public void onText(String d) { out.append(d); }
                public void onDone() { done.countDown(); }
                public void onError(String e) { err.append(e); done.countDown(); }
            }, new Handler(Looper.getMainLooper()));
            done.await(seconds, TimeUnit.SECONDS);
        } catch (Throwable ignored) { }
        String s = out.toString().replaceAll("(?s)<think>.*?</think>", "").trim();
        return s;
    }

    static List<String> queries(String task) {
        List<String> qs = new ArrayList<String>();
        String reply = ask("Extract search queries. Reply with ONLY a JSON array of up to 3 short search queries (2-6 words each) that would find official documentation or encyclopedia articles for the task. Use English for programming topics.", task, 40);
        try {
            int a = reply.indexOf('['), b = reply.lastIndexOf(']');
            if (a >= 0 && b > a) { JSONArray arr = new JSONArray(reply.substring(a, b + 1)); for (int i = 0; i < arr.length() && i < 3; i++) qs.add(arr.getString(i)); }
        } catch (Exception ignored) { }
        if (qs.isEmpty()) qs.add(task.replaceAll("(?i)^(ابحث عن|اشرح|لخص|لخّص|تعلم|تعلّم|اكتب)\\s+", "").trim());
        return qs;
    }

    // ------------------------------------------------------------ one task
    static void run(Context c, JSONObject task) {
        String id = task.optString("id"), text = task.optString("text");
        try {
            patch(c, id, new JSONObject().put("status", "running").put("step", "التخطيط"));
            List<String> qs = queries(text);
            boolean ar = hasArabic(text);
            String low = text.toLowerCase();
            boolean prog = low.matches("(?s).*(code|api|flutter|kotlin|python|javascript|java|swift|react|node|sql|برمج|كود|دالة|خطأ|error|exception|css|html|android|اندرويد).*");
            boolean web = low.matches("(?s).*(javascript|css|html|web|fetch|dom|react|موقع|ويب).*");
            JSONArray sources = new JSONArray();
            for (String q : qs) {
                patch(c, id, new JSONObject().put("step", "البحث: " + q));
                try { for (JSONObject s : wikipedia(q, ar)) sources.put(s); } catch (Exception ignored) { }
                if (!ar || true) {
                    try { for (JSONObject s : wikipedia(q, false)) if (ar) sources.put(s); } catch (Exception ignored) { }
                }
                if (web) try { for (JSONObject s : mdn(q)) sources.put(s); } catch (Exception ignored) { }
                if (prog) try { for (JSONObject s : stack(q)) sources.put(s); } catch (Exception ignored) { }
                if (sources.length() >= 8) break;
            }
            if (sources.length() == 0) {
                patch(c, id, new JSONObject().put("status", "failed").put("error", "لم أجد مصادر رسمية مناسبة للمهمة (أو لا يوجد اتصال).").put("done", System.currentTimeMillis()));
                return;
            }
            patch(c, id, new JSONObject().put("step", "قراءة " + sources.length() + " مصدراً وكتابة المسودة"));
            StringBuilder ctx = new StringBuilder();
            for (int i = 0; i < sources.length() && ctx.length() < 14000; i++) {
                JSONObject s = sources.getJSONObject(i);
                ctx.append("[").append(i + 1).append("] ").append(s.optString("title")).append(" (").append(s.optString("url")).append(")\n").append(s.optString("text")).append("\n\n");
            }
            String result = ask("You are a careful research assistant. Complete the user's task using ONLY the numbered sources provided. Cite sources like [1]. "
                    + "If the sources are insufficient say exactly what is missing; never invent facts. Reply in the same language as the task. Be precise and structured.",
                    "TASK:\n" + text + "\n\nSOURCES:\n" + ctx, 150);
            boolean verified = false;
            if (!result.isEmpty()) {
                patch(c, id, new JSONObject().put("step", "التحقق من المسودة مقابل المصادر"));
                String checked = ask("You are a strict fact-checker. You receive a DRAFT answer and the numbered SOURCES it was built from. Remove or rewrite every claim that the sources do not support, "
                        + "keep correct citations like [1], add a final line starting with 'غير مؤكد:' (or 'Unverified:' for English tasks) listing anything uncertain or missing, and return the corrected final answer only. Same language as the draft.",
                        "TASK:\n" + text + "\n\nDRAFT:\n" + result + "\n\nSOURCES:\n" + ctx, 150);
                if (!checked.isEmpty()) { result = checked; verified = true; }
            }
            boolean noModel = result.isEmpty();
            if (noModel) result = "تعذّر التلخيص لأنه لا يوجد نموذج ذكاء اصطناعي متاح الآن. حُفظت المصادر الرسمية (" + sources.length() + ") فقط ويمكنك تلخيصها لاحقاً.";
            String title = text.length() > 60 ? text.substring(0, 60) + "…" : text;
            JSONArray slim = new JSONArray();
            for (int i = 0; i < sources.length(); i++) { JSONObject s = sources.getJSONObject(i); slim.put(new JSONObject().put("title", s.optString("title")).put("url", s.optString("url")).put("license", s.optString("license"))); }
            // knowledge note (used later by the chat)
            JSONArray kn = load(c, "knowledge.json");
            kn.put(new JSONObject().put("id", id).put("title", title).put("task", text).put("result", result).put("sources", slim).put("text", ctx.length() > 12000 ? ctx.substring(0, 12000) : ctx.toString()).put("ts", System.currentTimeMillis()));
            save(c, "knowledge.json", kn);
            patch(c, id, new JSONObject().put("status", "done").put("result", result).put("sources", slim).put("summarised", !noModel).put("verified", verified).put("done", System.currentTimeMillis()).put("step", ""));
        } catch (Exception e) {
            try { patch(c, id, new JSONObject().put("status", "failed").put("error", String.valueOf(e.getMessage())).put("done", System.currentTimeMillis())); } catch (Exception ignored) { }
        }
    }

    // ------------------------------------------------------------ knowledge lookup for the chat
    static String knowledge(Context c, String query) {
        try {
            JSONArray kn = load(c, "knowledge.json");
            String[] words = query.toLowerCase().split("[^\\p{L}\\p{N}]+");
            List<Object[]> scored = new ArrayList<Object[]>();
            for (int i = 0; i < kn.length(); i++) {
                JSONObject k = kn.getJSONObject(i);
                String hay = (k.optString("title") + " " + k.optString("task") + " " + k.optString("result")).toLowerCase();
                int n = 0;
                for (String w : words) if (w.length() >= 3 && hay.contains(w)) n++;
                if (n >= 2) scored.add(new Object[]{n, k});
            }
            java.util.Collections.sort(scored, new java.util.Comparator<Object[]>() { public int compare(Object[] a, Object[] b) { return (Integer) b[0] - (Integer) a[0]; } });
            JSONArray out = new JSONArray();
            for (int i = 0; i < scored.size() && i < 3; i++) {
                JSONObject k = (JSONObject) scored.get(i)[1];
                String r = k.optString("result");
                out.put(new JSONObject().put("title", k.optString("title")).put("text", r.length() > 1800 ? r.substring(0, 1800) : r));
            }
            return out.toString();
        } catch (Exception e) { return "[]"; }
    }
}

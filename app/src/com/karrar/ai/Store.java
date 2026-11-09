package com.karrar.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Settings + conversation persistence (SharedPreferences / JSON). */
public final class Store {
    public static class Msg {
        public String role = "user";
        public String content = "";
        public String reasoning = "";
        public int thinkSecs = 0;
        public String sources = "[]";
        public int vote = 0;
        public boolean demo = false;
    }

    public static class Conv {
        public String id = UUID.randomUUID().toString();
        public String title = "";
        public long ts = System.currentTimeMillis();
        public List<Msg> msgs = new ArrayList<Msg>();
    }

    public static SharedPreferences sp;
    public static List<Conv> convs = new ArrayList<Conv>();
    /** Bumped whenever a setting that changes the UI (theme/lang/font) changes. */
    public static int version = 0;

    public static void init(Context c) {
        if (sp != null) return;
        sp = c.getApplicationContext().getSharedPreferences("karrar_ai", Context.MODE_PRIVATE);
        load();
    }

    public static String get(String k, String d) { return sp.getString(k, d); }
    public static void put(String k, String v) { sp.edit().putString(k, v).apply(); }
    public static boolean bool(String k, boolean d) { return sp.getBoolean(k, d); }
    public static void putBool(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }

    public static void applyTheme() {
        Theme.rtl = "ar".equals(get("lang", "ar"));
        Theme.apply(!"light".equals(get("theme", "dark")));
        String f = get("font", "m");
        Theme.font = "s".equals(f) ? 0.9f : "l".equals(f) ? 1.12f : "xl".equals(f) ? 1.25f : 1f;
    }

    public static String baseUrl() {
        String u = get("base_url", "https://api.deepseek.com").trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    public static void load() {
        convs.clear();
        try {
            JSONArray a = new JSONArray(sp.getString("convs", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Conv c = new Conv();
                c.id = o.getString("id");
                c.title = o.optString("title", "");
                c.ts = o.optLong("ts", 0);
                JSONArray ms = o.getJSONArray("m");
                for (int j = 0; j < ms.length(); j++) {
                    JSONObject mo = ms.getJSONObject(j);
                    Msg m = new Msg();
                    m.role = mo.optString("r", "user");
                    m.content = mo.optString("c", "");
                    m.reasoning = mo.optString("t", "");
                    m.thinkSecs = mo.optInt("s", 0);
                    m.sources = mo.optString("src", "[]");
                    m.vote = mo.optInt("v", 0);
                    m.demo = mo.optBoolean("d", false);
                    c.msgs.add(m);
                }
                convs.add(c);
            }
        } catch (Exception e) {
            convs.clear();
        }
    }

    public static void save() {
        if (!bool("save_hist", true)) {
            sp.edit().putString("convs", "[]").apply();
            return;
        }
        try {
            JSONArray a = new JSONArray();
            for (Conv c : convs) {
                if (c.msgs.isEmpty()) continue;
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("title", c.title);
                o.put("ts", c.ts);
                JSONArray ms = new JSONArray();
                for (Msg m : c.msgs) {
                    JSONObject mo = new JSONObject();
                    mo.put("r", m.role);
                    mo.put("c", m.content);
                    mo.put("t", m.reasoning);
                    mo.put("s", m.thinkSecs);
                    mo.put("src", m.sources);
                    mo.put("v", m.vote);
                    mo.put("d", m.demo);
                    ms.put(mo);
                }
                o.put("m", ms);
                a.put(o);
            }
            sp.edit().putString("convs", a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static Conv find(String id) {
        if (id == null) return null;
        for (Conv c : convs) if (c.id.equals(id)) return c;
        return null;
    }
}

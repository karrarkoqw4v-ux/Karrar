package com.karrar.ai;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Preferences shared with the web UI (all values are strings; booleans are "1"/"0"). */
public final class Store {
    /** Message passed to the AI client. */
    public static class Msg {
        public String role = "user";
        public String content = "";
        public List<String> images = new ArrayList<String>(); // base64 JPEG
    }

    public static SharedPreferences sp;

    public static void init(Context c) {
        if (sp != null) return;
        sp = c.getApplicationContext().getSharedPreferences("karrar_ai", Context.MODE_PRIVATE);
        Theme.rtl = "ar".equals(get("lang", "ar"));
    }

    public static String get(String k, String d) {
        try { return sp.getString(k, d); } catch (ClassCastException e) { return d; }
    }

    public static void put(String k, String v) { sp.edit().putString(k, v).apply(); }

    public static boolean bool(String k, boolean d) {
        String v = get(k, null);
        if (v == null) return d;
        return v.equals("1") || v.equals("true");
    }

    public static void putBool(String k, boolean v) { put(k, v ? "1" : "0"); }

    public static String baseUrl() {
        String u = get("base_url", "https://api.deepseek.com").trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}

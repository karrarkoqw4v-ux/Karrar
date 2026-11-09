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
    public static Context appCtx;

    public static void init(Context c) {
        if (sp != null) return;
        appCtx = c.getApplicationContext();
        sp = c.getApplicationContext().getSharedPreferences("karrar_ai", Context.MODE_PRIVATE);
        Theme.rtl = "ar".equals(get("lang", "ar"));
        loadDefaultProvider(c);
    }

    /** Optional private-build defaults (assets/default_provider.json): only fills settings the user has not set. */
    private static void loadDefaultProvider(Context c) {
        try {
            java.io.InputStream in = c.getAssets().open("default_provider.json");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[2048];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            org.json.JSONObject j = new org.json.JSONObject(bo.toString("UTF-8"));
            String[] keys = {"api_key", "base_url", "model", "think_model"};
            int rev = j.optInt("rev", 0);
            boolean apply = rev > 0 && !String.valueOf(rev).equals(get("defaults_rev", "0"));
            for (String k : keys) {
                if (!j.has(k)) continue;
                if (apply || get(k, "").trim().isEmpty()) put(k, j.getString(k));
            }
            if (apply) put("defaults_rev", String.valueOf(rev));
        } catch (Exception ignored) { }
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

package com.karrar.ai;

import android.util.Base64;

import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** On-device accounts (email + password, salted PBKDF2). Returned strings are T keys of errors, null = success. */
public final class Accounts {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private static JSONObject all() {
        try { return new JSONObject(Store.get("accounts", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    private static String hash(String pass, byte[] salt) throws Exception {
        {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] h = f.generateSecret(new PBEKeySpec(pass.toCharArray(), salt, 12000, 256)).getEncoded();
            return Base64.encodeToString(h, Base64.NO_WRAP);
        }
    }

    public static String signUp(String name, String email, String pass, String pass2) {
        name = name.trim();
        email = email.trim().toLowerCase();
        if (name.length() < 2) return "e_name";
        if (!EMAIL.matcher(email).matches()) return "e_email";
        if (pass.length() < 6) return "e_pass_short";
        if (!pass.equals(pass2)) return "e_pass_match";
        try {
            JSONObject a = all();
            if (a.has(email)) return "e_exists";
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            JSONObject u = new JSONObject();
            u.put("n", name);
            u.put("s", Base64.encodeToString(salt, Base64.NO_WRAP));
            u.put("h", hash(pass, salt));
            a.put(email, u);
            Store.put("accounts", a.toString());
            begin(email, name, false);
            return null;
        } catch (Exception e) {
            return "e_bad";
        }
    }

    public static String login(String email, String pass) {
        email = email.trim().toLowerCase();
        if (!EMAIL.matcher(email).matches()) return "e_email";
        if (pass.isEmpty()) return "e_empty";
        try {
            JSONObject u = all().optJSONObject(email);
            if (u == null) return "e_nouser";
            byte[] salt = Base64.decode(u.getString("s"), Base64.NO_WRAP);
            if (!hash(pass, salt).equals(u.getString("h"))) return "e_wrong";
            begin(email, u.getString("n"), false);
            return null;
        } catch (Exception e) {
            return "e_bad";
        }
    }

    public static boolean exists(String email) { return all().has(email.trim().toLowerCase()); }

    public static String reset(String email, String pass, String pass2) {
        email = email.trim().toLowerCase();
        if (pass.length() < 6) return "e_pass_short";
        if (!pass.equals(pass2)) return "e_pass_match";
        try {
            JSONObject a = all();
            JSONObject u = a.optJSONObject(email);
            if (u == null) return "e_nouser";
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            u.put("s", Base64.encodeToString(salt, Base64.NO_WRAP));
            u.put("h", hash(pass, salt));
            a.put(email, u);
            Store.put("accounts", a.toString());
            return null;
        } catch (Exception e) {
            return "e_bad";
        }
    }

    public static void google(String name, String email) {
        begin(email.toLowerCase(), name == null || name.isEmpty() ? email : name, true);
    }

    public static void guest() {
        begin("", "Guest", false);
    }

    private static void begin(String email, String name, boolean g) {
        Store.put("user_email", email);
        Store.put("name", name);
        Store.putBool("user_google", g);
        Store.putBool("logged", true);
        Store.put("cur", "");
    }

    public static void delete(String email) {
        try {
            JSONObject a = all();
            a.remove(email.trim().toLowerCase());
            Store.put("accounts", a.toString());
        } catch (Exception ignored) { }
        logout();
    }

    public static void logout() {
        Store.putBool("logged", false);
        Store.put("user_email", "");
        Store.put("cur", "");
    }
}

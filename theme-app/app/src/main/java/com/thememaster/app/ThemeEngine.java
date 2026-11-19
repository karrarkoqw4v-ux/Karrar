package com.thememaster.app;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * محرّك الثيمات: يفكّ ملف ZIP بأمان، يقرأ theme.json، ويطبّق ما هو حقيقي فعلاً —
 * خلفية الشاشة الرئيسية وخلفية شاشة القفل. لا يلمس محرّك الثيمات الخاص بأي شركة
 * (شاومي/هواوي/هونر) لأن أي تطبيق خارجي لا يصل إليه أصلاً.
 */
final class ThemeEngine {
    private ThemeEngine() {}

    static final class Theme {
        String name = "ثيم بلا اسم", author = "";
        File home, lock;              // ملفات الصور المستخرجة (قد تكون null)
    }

    static final class ApplyResult {
        boolean homeApplied, lockApplied; String error;
        String summary() {
            if (error != null) return "تعذّر التطبيق: " + error;
            StringBuilder sb = new StringBuilder();
            if (homeApplied) sb.append("✔ خلفية الشاشة الرئيسية");
            if (lockApplied) sb.append(sb.length() > 0 ? " · " : "").append("✔ خلفية شاشة القفل");
            if (sb.length() == 0) sb.append("لم يحتوِ الملف على صور قابلة للتطبيق (ضَع wallpaper.jpg و/أو lockscreen.jpg داخل الـZIP)");
            return sb.toString();
        }
    }

    /** يفكّ الـZIP في مجلّد خاص بالتطبيق بأمان (يمنع Zip Slip) ويُرجع وصف الثيم. */
    static Theme extract(Context c, Uri zipUri) throws IOException {
        File dir = new File(c.getCacheDir(), "theme_unpack");
        deleteDir(dir); dir.mkdirs();
        try (InputStream raw = c.getContentResolver().openInputStream(zipUri); ZipInputStream zin = new ZipInputStream(raw)) {
            if (raw == null) throw new IOException("تعذّر فتح الملف");
            ZipEntry e; byte[] buf = new byte[8192]; int n = 0; long total = 0;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = new File(e.getName()).getName();          // نتجاهل أي مسار فرعي/هروب (Zip Slip)
                if (name.isEmpty()) continue;
                File out = new File(dir, name);
                if (!out.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) continue;
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    int r; while ((r = zin.read(buf)) > 0) { os.write(buf, 0, r); total += r; if (total > 80L * 1024 * 1024) throw new IOException("الملف كبير جداً (الحد 80MB)"); }
                }
                n++;
                if (n > 200) throw new IOException("عدد ملفات كبير جداً داخل الـZIP");
            }
        }
        Theme t = new Theme();
        File manifest = new File(dir, "theme.json");
        if (manifest.exists()) {
            try {
                JSONObject j = new JSONObject(readAll(manifest));
                if (j.has("name")) t.name = j.optString("name", t.name);
                if (j.has("author")) t.author = j.optString("author", t.author);
            } catch (Exception ignored) {}
        }
        File home = firstExisting(dir, "wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png", "home.jpg", "home.png");
        File lock = firstExisting(dir, "lockscreen.jpg", "lockscreen.jpeg", "lockscreen.png", "lock.jpg", "lock.png");
        t.home = home; t.lock = lock;
        return t;
    }

    /** يطبّق الثيم فعلياً عبر WallpaperManager (API عامة، تعمل على كل الأجهزة والشركات). */
    static ApplyResult apply(Context c, Theme t) {
        ApplyResult r = new ApplyResult();
        WallpaperManager wm = WallpaperManager.getInstance(c);
        try {
            if (t.home != null) {
                Bitmap b = decode(t.home);
                if (b != null) {
                    if (Build.VERSION.SDK_INT >= 24) wm.setBitmap(b, null, true, WallpaperManager.FLAG_SYSTEM);
                    else wm.setBitmap(b);
                    r.homeApplied = true;
                }
            }
            if (t.lock != null && Build.VERSION.SDK_INT >= 24) {
                Bitmap b = decode(t.lock);
                if (b != null) { wm.setBitmap(b, null, true, WallpaperManager.FLAG_LOCK); r.lockApplied = true; }
            }
        } catch (Exception e) { r.error = e.getMessage() == null ? e.toString() : e.getMessage(); }
        return r;
    }

    private static Bitmap decode(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 1;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (b != null && (long) b.getWidth() * b.getHeight() > 16_000_000L) {   // صورة ضخمة: صغّرها لتفادي نفاد الذاكرة
            o.inJustDecodeBounds = true; BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1; while ((o.outWidth / sample) * (o.outHeight / sample) > 16_000_000L) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options(); o2.inSampleSize = sample;
            b = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        }
        return b;
    }
    private static File firstExisting(File dir, String... names) { for (String n : names) { File f = new File(dir, n); if (f.exists()) return f; } return null; }
    private static String readAll(File f) throws IOException {
        StringBuilder sb = new StringBuilder(); byte[] buf = new byte[4096]; int n;
        try (InputStream in = new java.io.FileInputStream(f)) { while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8)); }
        return sb.toString();
    }
    private static void deleteDir(File d) { File[] fs = d.listFiles(); if (fs != null) for (File f : fs) { if (f.isDirectory()) deleteDir(f); else f.delete(); } d.delete(); }
}

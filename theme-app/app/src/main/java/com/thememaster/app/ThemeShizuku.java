package com.thememaster.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/**
 * وصول shell عبر Shizuku (صلاحية على مستوى adb shell، وليست صلاحية root).
 * يُستخدم فقط لمحاولة نسخ ملف الثيم إلى مسار شاومي المحلي وتنبيه فاحص الوسائط،
 * وكمسار بديل لفتح تطبيق الثيمات. هذا لا يتجاوز تأكيد التثبيت داخل تطبيق MIUI
 * للثيمات نفسه — تلك الخطوة يملكها تطبيق شاومي وحده، ولا توجد صلاحية (Shizuku أو
 * غيرها) تتخطّاها دون الوصول لصلاحية root فعلية أو معرفة داخلية غير موثّقة بواجهة MIUI.
 */
final class ThemeShizuku {
    private ThemeShizuku() {}
    static final String PKG = "moe.shizuku.privileged.api";

    static boolean installed(Context c) {
        try { c.getPackageManager().getPackageInfo(PKG, 0); return true; } catch (Exception e) { return false; }
    }
    static boolean running() { try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; } }
    static boolean granted() {
        try { return Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { return false; }
    }
    static void request(int code) { try { Shizuku.requestPermission(code); } catch (Throwable ignored) {} }
    static Intent managerIntent(Context c) { return c.getPackageManager().getLaunchIntentForPackage(PKG); }
    static Intent installIntent() { return new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases/latest")); }

    /** متزامن؛ لا تستدعه من خيط الواجهة. */
    static synchronized String sh(String cmd) {
        if (!granted()) return "";
        try {
            Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            Process p = (Process) m.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) { String l; while ((l = r.readLine()) != null) sb.append(l).append('\n'); }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()))) { String l; while ((l = r.readLine()) != null) sb.append(l).append('\n'); }
            p.waitFor();
            return sb.toString().trim();
        } catch (Throwable t) { return ""; }
    }

    /**
     * ينسخ ملف الثيم عبر shell إلى مجلّد شاومي المحلي للثيمات، بدون الحاجة لصلاحية
     * «إدارة كل الملفات» (shell غالباً له وصول كتابة أوسع على /sdcard من تطبيق عادي
     * بلا تلك الصلاحية)، ثم يبلّغ فاحص الوسائط بالملف الجديد ليظهر أسرع داخل تطبيق الثيمات.
     * يُرجع المسار النهائي أو null عند الفشل.
     */
    static String copyToMiuiLocal(File src) {
        if (!granted()) return null;
        String dst = "/sdcard/MIUI/theme/" + src.getName();
        String r = sh("mkdir -p /sdcard/MIUI/theme && cp '" + src.getAbsolutePath() + "' '" + dst + "' && echo OK");
        if (!r.endsWith("OK")) return null;
        sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://" + dst);
        return dst;
    }

    /** مسار بديل لفتح تطبيق الثيمات عبر shell (نفس أثر الـIntent من داخل التطبيق، وليس تجاوزاً له). */
    static boolean launchThemeManager(String localPath) {
        if (!granted()) return false;
        String r = sh("am start -n com.android.thememanager/.ThemeManagerApp -a android.intent.action.VIEW -d file://" + localPath + " -t application/zip");
        return !r.toLowerCase(java.util.Locale.US).contains("error") && !r.toLowerCase(java.util.Locale.US).contains("exception");
    }

    static String whoami() { return sh("id"); }
}

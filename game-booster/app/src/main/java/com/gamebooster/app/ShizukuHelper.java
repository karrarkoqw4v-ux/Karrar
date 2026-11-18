package com.gamebooster.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

/** كل ما يحتاج صلاحيات shell عبر Shizuku. كل دالة تُرجع نتيجة الأمر الحقيقية. */
final class ShizukuHelper {
    private ShizukuHelper() {}
    static final String PKG = "moe.shizuku.privileged.api";
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Result { void done(String out); }

    // ---------- الحالة ----------
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

    // ---------- تنفيذ أمر shell ----------
    /** متزامن: لا تستدعه من خيط الواجهة. */
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
    /** غير متزامن: النتيجة تعود على خيط الواجهة. */
    static void async(final String cmd, final Result cb) {
        EX.execute(() -> { final String o = sh(cmd); if (cb != null) MAIN.post(() -> cb.done(o)); });
    }
    static void run(Runnable r) { EX.execute(r); }

    // ---------- الأدوات الحقيقية ----------
    /** الحزمة الظاهرة في الواجهة الآن (اللعبة المفتوحة). */
    static String topPackage() {
        String o = sh("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -1");
        Matcher m = Pattern.compile("u\\d+ ([A-Za-z0-9_.]+)/").matcher(o);
        return m.find() ? m.group(1) : null;
    }
    /** وضع اللعبة (Game Mode) لحزمة: performance / standard / battery. @return نص ناتج الأمر */
    static String gameMode(String pkg, String mode) {
        String a = sh("cmd game mode " + mode + " " + pkg);
        if (a.isEmpty() || a.toLowerCase().contains("unknown") || a.toLowerCase().contains("error") || a.toLowerCase().contains("exception")) {
            String n = mode.equals("performance") ? "2" : mode.equals("battery") ? "3" : "1";
            String b = sh("cmd game set --mode " + n + " --user 0 " + pkg);
            return b.isEmpty() ? "تم" : b;
        }
        return a;
    }
    static String trimCaches() { String o = sh("pm trim-caches 999G"); return o.isEmpty() ? "تم" : o; }
    /** إغلاق قسري لكل التطبيقات الخارجية عدا المستثناة. */
    static void forceStopOthers(Context c, String keep) {
        StringBuilder skip = new StringBuilder(c.getPackageName());
        if (keep != null) skip.append("|").append(keep);
        for (android.content.pm.ApplicationInfo ai : c.getPackageManager().getInstalledApplications(0)) if (Booster.isProtected(c, ai.packageName)) skip.append("|").append(ai.packageName);
        sh("for p in $(pm list packages -3 | cut -d: -f2); do case \"$p\" in " + skip + ") ;; *) am force-stop $p;; esac; done");
    }
    static String fixedPerformance(boolean on) { String o = sh("cmd power set-fixed-performance-mode-enabled " + on); return o.isEmpty() ? "تم" : o; }
    static void touchBoost(boolean on) {
        sh("settings put secure long_press_timeout " + (on ? 250 : 400));
        sh("settings put secure multi_press_timeout " + (on ? 200 : 300));
    }
    static String screenshot() {
        String f = "/sdcard/Pictures/GameBooster/shot_" + System.currentTimeMillis() / 1000 + ".png";
        String o = sh("mkdir -p /sdcard/Pictures/GameBooster && screencap -p " + f + " && echo OK");
        return o.endsWith("OK") ? f : null;
    }
    static void settingsPut(String ns, String key, String val) { sh("settings put " + ns + " " + key + " " + val); }

    // ---------- أرقام حقيقية ----------
    private static long[] lastCpu;
    /** نسبة استخدام المعالج (0..100) من /proc/stat، أو -1. */
    static synchronized float cpuUsage() {
        String l = sh("head -1 /proc/stat");
        if (!l.startsWith("cpu")) return -1;
        String[] t = l.trim().split("\\s+");
        try {
            long user = Long.parseLong(t[1]), nice = Long.parseLong(t[2]), sys = Long.parseLong(t[3]), idle = Long.parseLong(t[4]), io = Long.parseLong(t[5]),
                    irq = Long.parseLong(t[6]), soft = Long.parseLong(t[7]);
            long total = user + nice + sys + idle + io + irq + soft, busy = total - idle - io;
            float r = -1;
            if (lastCpu != null && total > lastCpu[0]) r = 100f * (busy - lastCpu[1]) / (total - lastCpu[0]);
            lastCpu = new long[]{total, busy};
            return r;
        } catch (Exception e) { return -1; }
    }
    /** نسبة استخدام المعالج الرسومي (إن كان المسار متاحاً)، أو -1. */
    static float gpuBusy() {
        String o = sh("cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null");
        try { return Float.parseFloat(o.replace("%", "").trim()); } catch (Exception e) { return -1; }
    }
    static String whoami() { return sh("id"); }
}

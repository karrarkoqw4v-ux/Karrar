package com.gamebooster.app;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;

/** كل عمليات التسريع والصلاحيات في مكان واحد. */
final class Booster {
    private Booster() {}

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("booster", Context.MODE_PRIVATE); }

    // ---------- إعدادات المستخدم ----------
    static boolean flag(Context c, String k, boolean d) { return prefs(c).getBoolean("s:" + k, d); }
    static void setFlag(Context c, String k, boolean v) { prefs(c).edit().putBoolean("s:" + k, v).apply(); }
    static int num(Context c, String k, int d) { return prefs(c).getInt("n:" + k, d); }
    static void setNum(Context c, String k, int v) { prefs(c).edit().putInt("n:" + k, v).apply(); }
    static boolean isProtected(Context c, String pkg) { return prefs(c).getBoolean("p:" + pkg, false); }
    static void setProtected(Context c, String pkg, boolean v) { prefs(c).edit().putBoolean("p:" + pkg, v).apply(); }

    /** تردد المعالج الحالي بالـGHz (أعلى نواة) أو -1 إذا النظام يمنع القراءة. */
    static float cpuGHz() {
        float best = -1;
        for (int i = 0; i < 16; i++) {
            java.io.File f = new java.io.File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (!f.canRead()) continue;
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
                String l = r.readLine(); if (l != null) best = Math.max(best, Long.parseLong(l.trim()) / 1_000_000f);
            } catch (Exception ignored) {}
        }
        return best;
    }

    // ---------- الصلاحيات ----------
    static boolean hasDnd(Context c) { return ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).isNotificationPolicyAccessGranted(); }
    static boolean hasOverlay(Context c) { return Settings.canDrawOverlays(c); }
    static boolean hasWriteSettings(Context c) { return Settings.System.canWrite(c); }
    static boolean hasPostNotifications(Context c) {
        return Build.VERSION.SDK_INT < 33 || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }
    static boolean hasBatteryExempt(Context c) { return ((android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(c.getPackageName()); }
    static Intent batteryIntent(Context c) { return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.getPackageName())); }
    static boolean hasUsage(Context c) {
        android.app.AppOpsManager a = (android.app.AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int m = Build.VERSION.SDK_INT >= 29 ? a.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName()) : a.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName());
        return m == android.app.AppOpsManager.MODE_ALLOWED;
    }
    static Intent usageIntent() { return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS); }
    static boolean hasSecure(Context c) { return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED; }
    /** يمنح كل الصلاحيات الممكنة تلقائياً عبر Shizuku (استدعِه من خيط خلفي). */
    static String grantAllViaShizuku(Context c) {
        String k = c.getPackageName();
        return ShizukuHelper.sh("pm grant " + k + " android.permission.POST_NOTIFICATIONS; pm grant " + k + " android.permission.WRITE_SECURE_SETTINGS; appops set " + k + " SYSTEM_ALERT_WINDOW allow; appops set " + k + " WRITE_SETTINGS allow; appops set " + k + " GET_USAGE_STATS allow; cmd notification allow_dnd " + k + "; cmd deviceidle whitelist +" + k);
    }
    /** الحزمة الظاهرة الآن (اللعبة المفتوحة): عبر Shizuku، وإلا عبر «بيانات الاستخدام». استدعِه من خيط خلفي. */
    static String topPackage(Context c) {
        if (ShizukuHelper.granted()) { String t = ShizukuHelper.topPackage(); if (t != null) return t; }
        if (hasUsage(c)) {
            android.app.usage.UsageStatsManager u = (android.app.usage.UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE); long now = System.currentTimeMillis();
            android.app.usage.UsageEvents ev = u.queryEvents(now - 20000, now); android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event(); String last = null;
            while (ev.hasNextEvent()) { ev.getNextEvent(e); if (e.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.getPackageName(); }
            return last;
        }
        return null;
    }
    static Intent dndSettings() { return new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS); }
    static Intent overlaySettings(Context c) { return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.getPackageName())); }
    static Intent writeSettings(Context c) { return new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.getPackageName())); }

    // ---------- كتم الإشعارات والمكالمات ----------
    static boolean dndOn(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        int f = nm.getCurrentInterruptionFilter();
        return hasDnd(c) && (f == NotificationManager.INTERRUPTION_FILTER_NONE || f == NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    }
    /** @return false إذا لا توجد صلاحية */
    static boolean setDnd(Context c, boolean on) {
        if (!hasDnd(c)) return false;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.setInterruptionFilter(on ? (flag(c, "blockCalls", true) ? NotificationManager.INTERRUPTION_FILTER_NONE : NotificationManager.INTERRUPTION_FILTER_PRIORITY) : NotificationManager.INTERRUPTION_FILTER_ALL);
        return true;
    }

    /** مرشح المقاطعات الحالي (ALL=1, PRIORITY=2, NONE=3, ALARMS=4). */
    static int filter(Context c) { return ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).getCurrentInterruptionFilter(); }
    static boolean setFilter(Context c, int f) {
        if (!hasDnd(c)) return false;
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).setInterruptionFilter(f); return true;
    }

    // ---------- إغلاق تطبيقات الخلفية ----------
    static long freeMem(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return mi.availMem;
    }
    static long totalMem(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return mi.totalMem;
    }
    /** يغلق عمليات الخلفية لغير النظامية ويستثني keepPkg. @return الذاكرة المحرَّرة بالـMB (تقريبي) */
    static long killBackground(Context c, String keepPkg) {
        long before = freeMem(c);
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        for (ApplicationInfo ai : c.getPackageManager().getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (ai.packageName.equals(c.getPackageName()) || ai.packageName.equals(keepPkg) || isProtected(c, ai.packageName)) continue;
            am.killBackgroundProcesses(ai.packageName);
        }
        return Math.max(0, freeMem(c) - before) / (1024 * 1024);
    }

    // ---------- الصوت ----------
    static int mediaVolumePct(Context c) {
        AudioManager a = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        return a.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / Math.max(1, a.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }
    static void setMediaVolumePct(Context c, int pct) {
        AudioManager a = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        a.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(a.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * pct / 100f), 0);
    }
    static boolean gameSoundOn(Context c) { return prefs(c).getBoolean("gameSound", false); }
    /** وضع صوت الألعاب: صوت الوسائط 80% ورنين صامت (إن سمحت الصلاحية) */
    static void setGameSound(Context c, boolean on) {
        AudioManager a = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        SharedPreferences p = prefs(c);
        if (on) {
            p.edit().putBoolean("gameSound", true).putInt("prevVol", mediaVolumePct(c)).putInt("prevRinger", a.getRingerMode()).apply();
            setMediaVolumePct(c, 80);
            if (hasDnd(c)) a.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        } else {
            setMediaVolumePct(c, p.getInt("prevVol", 60));
            if (hasDnd(c)) a.setRingerMode(p.getInt("prevRinger", AudioManager.RINGER_MODE_NORMAL));
            p.edit().putBoolean("gameSound", false).apply();
        }
    }

    // ---------- السطوع ----------
    static boolean brightOn(Context c) { return prefs(c).getBoolean("bright", false); }
    static boolean setBright(Context c, boolean on) {
        if (!hasWriteSettings(c)) return false;
        android.content.ContentResolver r = c.getContentResolver();
        SharedPreferences p = prefs(c);
        try {
            if (on) {
                p.edit().putBoolean("bright", true).putInt("prevB", Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS, 128))
                        .putInt("prevMode", Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, 1)).apply();
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, 255);
            } else {
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, p.getInt("prevB", 128));
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, p.getInt("prevMode", 1));
                p.edit().putBoolean("bright", false).apply();
            }
        } catch (Exception e) { return false; }
        return true;
    }

    // ---------- معلومات الجهاز ----------
    static float batteryTemp(Context c) {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
    }
    static int batteryPct(Context c) {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return 0;
        return b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
    }

    static boolean isCharging(Context c) {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return false;
        int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
    }

    // ---------- الألعاب ----------
    static java.util.List<android.content.pm.ResolveInfo> launchable(Context c) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        java.util.List<android.content.pm.ResolveInfo> l = c.getPackageManager().queryIntentActivities(q, 0);
        java.util.List<android.content.pm.ResolveInfo> out = new java.util.ArrayList<>();
        for (android.content.pm.ResolveInfo r : l) if (!r.activityInfo.packageName.equals(c.getPackageName())) out.add(r);
        final PackageManager pm = c.getPackageManager();
        java.util.Collections.sort(out, (a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        return out;
    }
    static boolean isGame(Context c, android.content.pm.ResolveInfo r) {
        ApplicationInfo ai = r.activityInfo.applicationInfo;
        boolean auto = (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0 || (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME);
        return prefs(c).getBoolean("g:" + ai.packageName, auto);
    }
    static void setGame(Context c, String pkg, boolean game) { prefs(c).edit().putBoolean("g:" + pkg, game).apply(); }
    static java.util.List<android.content.pm.ResolveInfo> games(Context c) {
        java.util.List<android.content.pm.ResolveInfo> out = new java.util.ArrayList<>();
        for (android.content.pm.ResolveInfo r : launchable(c)) if (isGame(c, r)) out.add(r);
        return out;
    }

    // ---------- قفل التدوير ----------
    static boolean setRotationLock(Context c, boolean on) {
        if (!hasWriteSettings(c)) return false;
        android.content.ContentResolver r = c.getContentResolver();
        SharedPreferences p = prefs(c);
        try {
            if (on) {
                p.edit().putInt("prevAuto", Settings.System.getInt(r, Settings.System.ACCELEROMETER_ROTATION, 1)).putInt("prevRot", Settings.System.getInt(r, Settings.System.USER_ROTATION, 0)).apply();
                Settings.System.putInt(r, Settings.System.ACCELEROMETER_ROTATION, 0);
                Settings.System.putInt(r, Settings.System.USER_ROTATION, 1);
            } else {
                Settings.System.putInt(r, Settings.System.USER_ROTATION, p.getInt("prevRot", 0));
                Settings.System.putInt(r, Settings.System.ACCELEROMETER_ROTATION, p.getInt("prevAuto", 1));
            }
        } catch (Exception e) { return false; }
        return true;
    }

    /** بداية جلسة لعب حسب إعدادات المستخدم (يُنفَّذ على خيط خلفي إذا استُخدم Shizuku). */
    static String startSession(final Context c, final String pkg) {
        StringBuilder sb = new StringBuilder();
        final boolean sz = ShizukuHelper.granted();
        final boolean dndNow = gameFlag(c, pkg, "dnd", flag(c, "dndOnStart", true));
        if (gameFlag(c, pkg, "clean", flag(c, "cleanBefore", true))) sb.append("✔ تحرّر ~").append(killBackground(c, pkg)).append(" MB");
        if (dndNow) { if (setDnd(c, true)) sb.append(" · كتم الإشعارات"); else sb.append(" · (امنح صلاحية عدم الإزعاج)"); }
        if (flag(c, "lockBright", false) && setBright(c, true)) sb.append(" · سطوع مقفول");
        if (flag(c, "lockRotation", false) && setRotationLock(c, true)) sb.append(" · تدوير مقفول");
        if (sz) {
            final boolean perf = gameFlag(c, pkg, "perf", flag(c, "gameModePerf", true)), trim = flag(c, "trimCaches", true), fs = flag(c, "forceStop", true),
                    fixed = flag(c, "fixedPerf", false), touch = flag(c, "touchBoost", false);
            if (perf) sb.append(" · وضع الأداء");
            if (fs) sb.append(" · إغلاق قسري");
            ShizukuHelper.run(() -> {
                if (fs) ShizukuHelper.forceStopOthers(c, pkg);
                if (trim) ShizukuHelper.trimCaches();
                if (perf) ShizukuHelper.gameMode(pkg, "performance");
                if (fixed) ShizukuHelper.fixedPerformance(true);
                if (touch) ShizukuHelper.touchBoost(true);
            });
        }
        prefs(c).edit().putBoolean("sessionOn", true).putString("sessionPkg", pkg).putBoolean("sessionDnd", dndNow).apply();
        return sb.length() == 0 ? "تم" : sb.toString();
    }
    /** إنهاء الجلسة وإرجاع الإعدادات (يُستدعى عند الرجوع للتطبيق). */
    static void endSessionIfActive(final Context c) {
        if (!prefs(c).getBoolean("sessionOn", false) || !flag(c, "restoreOnReturn", true)) return;
        final String pkg = prefs(c).getString("sessionPkg", null);
        prefs(c).edit().putBoolean("sessionOn", false).apply();
        if (prefs(c).getBoolean("sessionDnd", false)) setDnd(c, false);
        if (brightOn(c)) setBright(c, false);
        if (flag(c, "lockRotation", false)) setRotationLock(c, false);
        if (ShizukuHelper.granted()) ShizukuHelper.run(() -> {
            if (pkg != null) ShizukuHelper.gameMode(pkg, "standard");
            if (flag(c, "fixedPerf", false)) ShizukuHelper.fixedPerformance(false);
            if (flag(c, "touchBoost", false)) ShizukuHelper.touchBoost(false);
        });
    }
    /** خيار خاص بلعبة معيّنة (يرجع للافتراضي العام إن لم يُحدَّد). */
    static boolean gameFlag(Context c, String pkg, String k, boolean def) { return prefs(c).getBoolean("gf:" + pkg + ":" + k, def); }
    static void setGameFlag(Context c, String pkg, String k, boolean v) { prefs(c).edit().putBoolean("gf:" + pkg + ":" + k, v).apply(); }

    /** التسريع الكامل: تنظيف الخلفية + كتم (إن مُنحت الصلاحية) + سطوع. */
    static String boostAll(Context c, String keepPkg) {
        long freed = killBackground(c, keepPkg);
        StringBuilder sb = new StringBuilder("✔ تحرّر ~" + freed + " MB");
        if (setDnd(c, true)) sb.append(" · كُتمت الإشعارات والمكالمات");
        if (setBright(c, true)) sb.append(" · سطوع عالٍ");
        return sb.toString();
    }
}

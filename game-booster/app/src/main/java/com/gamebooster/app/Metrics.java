package com.gamebooster.app;

import android.content.Context;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** قراءات حقيقية من الهاتف لشريط «وضع المعرفة». */
final class Metrics {
    private Metrics() {}
    private static long lastBytes = -1, lastT; private static float emaHours = -1;

    static String clock() { return new SimpleDateFormat("HH:mm", Locale.US).format(new Date()); }

    /** سرعة الشبكة (تحميل+رفع) آخر فترة، بصيغة 3.95 KB/S أو 1.20 MB/S */
    static String net() {
        long b = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes(), t = SystemClock.elapsedRealtime();
        if (TrafficStats.getTotalRxBytes() == TrafficStats.UNSUPPORTED) return "-- KB/S";
        String r = "0.00 KB/S";
        if (lastBytes >= 0 && t > lastT) {
            float kbs = (b - lastBytes) / 1024f / ((t - lastT) / 1000f);
            r = kbs >= 1024 ? String.format(Locale.US, "%.2f MB/S", kbs / 1024f) : String.format(Locale.US, "%.2f KB/S", kbs);
        }
        lastBytes = b; lastT = t; return r;
    }

    /** المدة المتبقية: وقت الشحن إن كان يشحن، وإلا تقدير التشغيل من التيار الحالي (بالساعات). */
    static String hours(Context c) {
        BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
        if (Booster.isCharging(c)) {
            if (Build.VERSION.SDK_INT >= 28) { long ms = bm.computeChargeTimeRemaining(); if (ms > 0) return String.format(Locale.US, "%.1fh", ms / 3600000f); }
            return "شحن";
        }
        long cur = Math.abs(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)), cnt = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        if (cur <= 0 || cnt <= 0 || cnt == Long.MIN_VALUE) return "--h";
        if (cur < 20000) cur *= 1000;                         // بعض الأجهزة تعيد mA بدل µA
        float h = cnt / (float) cur;
        emaHours = emaHours < 0 ? h : emaHours * .8f + h * .2f;
        return String.format(Locale.US, "%.1fh", Math.min(emaHours, 99f));
    }
}

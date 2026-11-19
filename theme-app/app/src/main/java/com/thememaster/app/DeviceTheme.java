package com.thememaster.app;

import android.os.Build;

/** يكتشف الشركة المصنّعة ليختار كيف يُسلَّم ملف الثيم لتطبيق الثيمات الرسمي في النظام. */
final class DeviceTheme {
    private DeviceTheme() {}

    static final int MIUI = 1, HUAWEI = 2, REALME = 3, SAMSUNG = 4, OTHER = 0;
    /** مدعوم فعلياً الآن (الطلب الحالي: شاومي/ريدمي/بوكو فقط). الباقي يُكتشف لكنه غير مفعَّل بعد. */
    static final int[] SUPPORTED = {MIUI};

    static int brand() {
        String b = (Build.BRAND + " " + Build.MANUFACTURER).toLowerCase(java.util.Locale.US);
        if (b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")) return MIUI;
        if (b.contains("huawei") || b.contains("honor")) return HUAWEI;
        if (b.contains("realme") || b.contains("oppo")) return REALME;
        if (b.contains("samsung")) return SAMSUNG;
        return OTHER;
    }
    static String brandName(int t) {
        switch (t) {
            case MIUI: return "شاومي / ريدمي / بوكو (MIUI)";
            case HUAWEI: return "هواوي / هونر";
            case REALME: return "ريلمي / أوبو";
            case SAMSUNG: return "سامسونج";
            default: return Build.MANUFACTURER;
        }
    }
    /** اسم حزمة تطبيق الثيمات الرسمي في النظام (null إن لم يكن معروفاً/مدعوماً بعد). */
    static String themeAppPackage(int t) {
        if (t == MIUI) return "com.android.thememanager";
        if (t == HUAWEI) return "com.huawei.android.thememanager";
        return null;
    }
    static boolean isSupportedNow(int t) { for (int s : SUPPORTED) if (s == t) return true; return false; }
}

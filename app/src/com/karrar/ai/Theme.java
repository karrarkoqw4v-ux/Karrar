package com.karrar.ai;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.Window;

/** Colour tokens + drawable helpers. Dark theme is the default (matches the reference design). */
public final class Theme {
    public static boolean dark = true;
    public static boolean rtl = true;
    public static float font = 1f;

    public static int bg, surface, surface2, text, text2, text3, accent, accentSoft,
            border, userBubble, codeBg, danger, scrim, onAccent;

    public static void apply(boolean d) {
        dark = d;
        accent = 0xFF4D6BFE;
        danger = 0xFFEF5350;
        onAccent = 0xFFFFFFFF;
        if (d) {
            bg = 0xFF17181A;
            surface = 0xFF242528;
            surface2 = 0xFF2F3034;
            text = 0xFFEDEDEF;
            text2 = 0xFFA6A7AD;
            text3 = 0xFF70717A;
            accentSoft = 0x264D6BFE;
            border = 0xFF38393F;
            userBubble = 0xFF2A2B30;
            codeBg = 0xFF1E1F23;
            scrim = 0x99000000;
        } else {
            bg = 0xFFFFFFFF;
            surface = 0xFFF3F4F7;
            surface2 = 0xFFE8EAEE;
            text = 0xFF1B1C1F;
            text2 = 0xFF5F6168;
            text3 = 0xFF9A9CA4;
            accentSoft = 0x1F4D6BFE;
            border = 0xFFE1E3E8;
            userBubble = 0xFFEFF1F5;
            codeBg = 0xFFF5F6F8;
            scrim = 0x66000000;
        }
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rect(Context c, int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(Math.max(1, dp(c, strokeDp)), strokeColor);
        return g;
    }

    public static GradientDrawable rect(Context c, int color, float radiusDp) {
        return rect(c, color, radiusDp, 0, 0);
    }

    public static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Background with a ripple press effect on top of the given drawable. */
    public static Drawable ripple(Drawable content) {
        int rc = dark ? 0x33FFFFFF : 0x22000000;
        return new RippleDrawable(ColorStateList.valueOf(rc), content, null);
    }

    public static Drawable rippleRect(Context c, int color, float radiusDp) {
        return ripple(rect(c, color, radiusDp));
    }

    public static void styleWindow(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        w.getDecorView().setBackgroundColor(bg);
        if (Build.VERSION.SDK_INT >= 23) {
            int f = w.getDecorView().getSystemUiVisibility();
            if (dark) f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (dark) f &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            w.getDecorView().setSystemUiVisibility(f);
        }
    }

    public static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}

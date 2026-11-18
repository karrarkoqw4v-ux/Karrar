package com.gamebooster.app;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** نوافذ إضافية فوق اللعبة: لوحة البيانات، مؤشر المركز، لون الشاشة، ومعدّل التحديث. */
final class Extras {
    private Extras() {}
    static boolean hudOn, crossOn; static int tint;           // tint: 0 إيقاف، 1 دافئ، 2 بارد، 3 حيوي
    private static View hud, cross, tintV; private static TextView hudText;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final Runnable HUD_TICK = new Runnable() {
        @Override public void run() {
            if (!hudOn || hudText == null) return;
            Context c = hudText.getContext();
            long tot = Booster.totalMem(c), free = Booster.freeMem(c);
            final String base = String.format(Locale.US, "RAM %d%%   %d°C   %d%%", Math.round(100f * (1 - (float) free / tot)), Math.round(Booster.batteryTemp(c)), Booster.batteryPct(c));
            hudText.setText(base);
            if (ShizukuHelper.granted()) ShizukuHelper.run(() -> { final float u = ShizukuHelper.cpuUsage(), g = ShizukuHelper.gpuBusy();
                H.post(() -> { if (hudText != null && hudOn) hudText.setText(base + (u >= 0 ? "\nCPU " + Math.round(u) + "%" : "") + (g >= 0 ? "   GPU " + Math.round(g) + "%" : "")); }); });
            H.postDelayed(this, 1000);
        }
    };

    private static int type() { return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE; }
    private static WindowManager wm(Context c) { return (WindowManager) c.getSystemService(Context.WINDOW_SERVICE); }
    private static WindowManager.LayoutParams lp(int w, int h, int gravity, int x, int y) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h, type(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        p.gravity = gravity; p.x = x; p.y = y; return p;
    }

    static void toggleHud(Context c) {
        c = c.getApplicationContext();
        if (hudOn) { hudOn = false; try { wm(c).removeView(hud); } catch (Exception ignored) {} hud = null; hudText = null; return; }
        TextView t = new TextView(c); t.setTextColor(Color.WHITE); t.setTextSize(13); t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); t.setPadding(18, 10, 18, 10);
        t.setBackground(UiUtil.round(Color.argb(170, 0, 0, 0), 20, Color.rgb(200, 40, 30), 2)); hud = t; hudText = t; hudOn = true;
        wm(c).addView(t, lp(-2, -2, Gravity.TOP | Gravity.START, 220, 20)); H.post(HUD_TICK);
    }
    static void toggleCross(Context c) {
        c = c.getApplicationContext();
        if (crossOn) { crossOn = false; try { wm(c).removeView(cross); } catch (Exception ignored) {} cross = null; return; }
        View v = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas cv) {
                float cx = getWidth() / 2f, cy = getHeight() / 2f; p.setStyle(Paint.Style.STROKE); p.setColor(Color.argb(230, 255, 30, 30)); p.setStrokeWidth(3);
                cv.drawCircle(cx, cy, 14, p); cv.drawLine(cx - 24, cy, cx - 6, cy, p); cv.drawLine(cx + 6, cy, cx + 24, cy, p); cv.drawLine(cx, cy - 24, cx, cy - 6, p); cv.drawLine(cx, cy + 6, cx, cy + 24, p);
                p.setStyle(Paint.Style.FILL); cv.drawCircle(cx, cy, 3, p);
            }
        };
        cross = v; crossOn = true; wm(c).addView(v, lp(120, 120, Gravity.CENTER, 0, 0));
    }
    /** يدوّر بين: إيقاف ← دافئ ← بارد ← حيوي */
    static void cycleTint(Context c) {
        c = c.getApplicationContext(); tint = (tint + 1) % 4;
        try { if (tintV != null) wm(c).removeView(tintV); } catch (Exception ignored) {} tintV = null;
        if (tint == 0) return;
        int col = tint == 1 ? Color.argb(60, 255, 150, 40) : tint == 2 ? Color.argb(55, 60, 130, 255) : Color.argb(45, 255, 40, 120);
        View v = new View(c); v.setBackgroundColor(col); tintV = v;
        wm(c).addView(v, lp(-1, -1, Gravity.TOP | Gravity.START, 0, 0));
    }
    static void clear(Context c) {
        c = c.getApplicationContext(); hudOn = false; crossOn = false; tint = 0;
        for (View v : new View[]{hud, cross, tintV}) try { if (v != null) wm(c).removeView(v); } catch (Exception ignored) {}
        hud = cross = tintV = null; hudText = null;
    }

    // ---- معدّل التحديث ----
    static List<Integer> hzList(Context c) {
        TreeSet<Integer> s = new TreeSet<>();
        try { Display d = wm(c).getDefaultDisplay(); for (Display.Mode m : d.getSupportedModes()) s.add(Math.round(m.getRefreshRate())); } catch (Exception ignored) {}
        if (s.isEmpty()) s.add(60);
        return new ArrayList<>(s);
    }
    static int hzNow(Context c) { try { return Math.round(wm(c).getDefaultDisplay().getRefreshRate()); } catch (Exception e) { return 60; } }
    /** يبدّل إلى المعدّل التالي (يحتاج Shizuku). @return المعدّل الجديد */
    static int nextHz(Context c) {
        List<Integer> l = hzList(c); int cur = hzNow(c), idx = Math.max(0, l.indexOf(cur)), nx = l.get((idx + 1) % l.size());
        Booster.prefs(c).edit().putInt("hzForced", nx).apply();
        ShizukuHelper.settingsPut("system", "min_refresh_rate", String.valueOf(nx)); ShizukuHelper.settingsPut("system", "peak_refresh_rate", String.valueOf(nx));
        return nx;
    }
}

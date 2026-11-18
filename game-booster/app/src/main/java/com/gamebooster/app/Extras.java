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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** نوافذ إضافية فوق اللعبة: لوحة البيانات، مؤشر المركز، لون الشاشة، ومعدّل التحديث. */
final class Extras {
    private Extras() {}
    static boolean hudOn, crossOn; static int tint;
    /** أسماء مرشّحات لون الشاشة؛ الفهرس 0 = إيقاف. فلاتر شفافة حقيقية (SRC_OVER) فوق أي تطبيق — ليست معايرة عتاد الشاشة. */
    static final String[] TINT_NAMES = {"إيقاف", "دافئ سينمائي", "بارد حيوي", "مشبّع نابض", "ليلي (راحة للعين)"};
    private static View cross, tintV; private static InfoBar hud; private static String hudPkg; private static int hudTick;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final Runnable HUD_TICK = new Runnable() {
        @Override public void run() {
            if (!hudOn || hud == null) return;
            final Context c = hud.getContext(); final boolean sz = ShizukuHelper.granted(); final int n = hudTick++;
            ShizukuHelper.run(() -> {
                if (hudPkg == null || n % 6 == 0) { String t = Booster.topPackage(c); if (t != null && !t.equals(c.getPackageName())) hudPkg = t; }
                final float f = sz ? ShizukuHelper.fps(hudPkg) : -1;
                final String net = Metrics.net(), hrs = Metrics.hours(c), clk = Metrics.clock(); final int bat = Booster.batteryPct(c);
                H.post(() -> { if (hud != null && hudOn) hud.set(clk, f >= 0 ? Math.round(f) + "FPS" : "--FPS", net, bat + "%", hrs); });
            });
            H.postDelayed(this, 1000);
        }
    };

    private static int type() { return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE; }
    private static WindowManager wm(Context c) { return (WindowManager) c.getSystemService(Context.WINDOW_SERVICE); }
    private static WindowManager.LayoutParams lp(int w, int h, int gravity, int x, int y) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h, type(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        p.gravity = gravity; p.x = x; p.y = y; return p;
    }

    /** «وضع المعرفة»: شريط المعلومات فوق اللعبة (قابل للسحب، ويحفظ مكانه). */
    static void toggleHud(Context c) {
        c = c.getApplicationContext();
        if (hudOn) { hudOn = false; try { wm(c).removeView(hud); } catch (Exception ignored) {} hud = null; return; }
        final InfoBar bar = new InfoBar(c); hud = bar; hudOn = true; hudPkg = null; hudTick = 0;
        final WindowManager w = wm(c); final android.content.SharedPreferences pr = Booster.prefs(c);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2, type(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START; lp.x = pr.getInt("hudX", (int) (c.getResources().getDisplayMetrics().widthPixels * .3f)); lp.y = pr.getInt("hudY", (int) (c.getResources().getDisplayMetrics().heightPixels * .08f));
        bar.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN: sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y; return true;
                    case android.view.MotionEvent.ACTION_MOVE: lp.x = ox + (int) (e.getRawX() - sx); lp.y = oy + (int) (e.getRawY() - sy); try { w.updateViewLayout(bar, lp); } catch (Exception ignored) {} return true;
                    case android.view.MotionEvent.ACTION_UP: pr.edit().putInt("hudX", lp.x).putInt("hudY", lp.y).apply(); return true;
                }
                return false;
            }
        });
        w.addView(bar, lp); H.post(HUD_TICK);
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
    /** مرشّح فوق اللعبة بتدرّج لوني واقعي (ليس تلويناً مسطّحاً) + تظليل خفيف على الأطراف. يدوّر بين 5 أوضاع. @return الفهرس الجديد */
    static int cycleTint(Context c) {
        c = c.getApplicationContext(); tint = (tint + 1) % TINT_NAMES.length;
        try { if (tintV != null) wm(c).removeView(tintV); } catch (Exception ignored) {} tintV = null;
        if (tint == 0) return tint;
        final int preset = tint;
        View v = new View(c) {
            final Paint p = new Paint();
            @Override protected void onDraw(Canvas cv) {
                float w = getWidth(), hh = getHeight();
                int top, bottom, vign; float opacity;
                switch (preset) {
                    case 1: top = Color.argb(46, 255, 176, 96); bottom = Color.argb(70, 255, 120, 30); vign = Color.argb(60, 90, 40, 0); opacity = 1f; break;   // دافئ سينمائي
                    case 2: top = Color.argb(60, 130, 200, 255); bottom = Color.argb(40, 40, 90, 190); vign = Color.argb(70, 0, 20, 60); opacity = 1f; break;  // بارد حيوي
                    case 3: top = Color.argb(34, 255, 70, 140); bottom = Color.argb(34, 255, 190, 40); vign = Color.argb(40, 60, 0, 40); opacity = 1f; break;  // مشبّع نابض
                    default: top = Color.argb(78, 255, 120, 60); bottom = Color.argb(100, 180, 30, 20); vign = Color.argb(90, 40, 0, 0); opacity = 1f; break;  // ليلي
                }
                p.setShader(new LinearGradient(0, 0, 0, hh, top, bottom, Shader.TileMode.CLAMP)); cv.drawRect(0, 0, w, hh, p); p.setShader(null);
                p.setShader(new RadialGradient(w / 2, hh / 2, Math.max(w, hh) * .72f, 0x00000000, vign, Shader.TileMode.CLAMP)); cv.drawRect(0, 0, w, hh, p); p.setShader(null);
            }
        };
        tintV = v;
        wm(c).addView(v, lp(-1, -1, Gravity.TOP | Gravity.START, 0, 0));
        return tint;
    }
    static void clear(Context c) {
        c = c.getApplicationContext(); hudOn = false; crossOn = false; tint = 0;
        for (View v : new View[]{hud, cross, tintV}) try { if (v != null) wm(c).removeView(v); } catch (Exception ignored) {}
        hud = null; cross = tintV = null;
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

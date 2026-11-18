package com.gamebooster.app;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.*;

/** لوحة اللعبة: لوحتان بحافة مائلة وشريط أحمر مقطّع، عدّادات هولوغرافية، وأدوات حقيقية. كل شيء يُرسم على لوحة واحدة. */
public class PanelView extends View {
    interface Closer { void close(); }
    private final Context ctx; private final Closer closer;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private final Path path = new Path(); private final RectF rf = new RectF();
    private final long t0 = SystemClock.uptimeMillis(); private long closeT = -1;
    private float W, H, U;
    // ===== بيانات حيّة (هدف + عرض متحرك) =====
    private float cpuNum = 0, cpuFrac = 0, ramFrac = 0, dCpuNum, dCpuFrac, dRamFrac; private boolean cpuIsGhz;
    private String gamePkg; private boolean running = true;
    private final Map<String, Float> tw = new HashMap<>();              // انتقال اللون لكل أداة
    private final Map<String, Long> pressAt = new HashMap<>();
    private final Map<String, Boolean> st = new HashMap<>();
    private float scroll = 0; private boolean listMode = true; private long seg0 = -1000;
    private float briV = .6f, volV; private float press_x, press_y;
    private final List<Object[]> hits = new ArrayList<>();                 // {RectF, String}
    private String downKey; private float downY, downScroll; private boolean dragged;
    private List<Drawable> appIcons = new ArrayList<>(); private List<String> appPkgs = new ArrayList<>();

    private static final String[][] LEFT = {{"data", "لوحة البيانات"}, {"cross", "مؤشر مركز الشاشة"}, {"color", "لون الشاشة"}, {"magn", "أداة تكبير الشاشة"}, {"fix", "أداء ثابت"}, {"touch", "لمس أسرع"}};
    private static final String[] LSHORT = {"البيانات", "المؤشر", "اللون", "التكبير", "أداء", "لمس"};
    private static final String[][] RIGHT = {{"shot", "لقطة"}, {"rotate", "تدوير"}, {"bellx", "الإشعارات"}, {"auto", "إضاءة"}, {"hz", "معدل"}, {"wifi", "واي فاي"}, {"rocket", "معزز"}, {"moon", "عدم الإزعاج"}, {"broom", "المهملات"}};

    public PanelView(Context c, Closer closer) {
        super(c); this.ctx = c; this.closer = closer;
        volV = Booster.mediaVolumePct(c) / 100f;
        try { briV = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f; } catch (Exception ignored) {}
        PackageManager pm = c.getPackageManager(); List<ResolveInfo> g = Booster.games(c);
        for (int i = 0; i < Math.min(4, g.size()); i++) { appIcons.add(g.get(i).loadIcon(pm)); appPkgs.add(g.get(i).activityInfo.packageName); }
        refreshStates(); tick();
        ShizukuHelper.run(() -> { final String pk = ShizukuHelper.topPackage(); h.post(() -> { if (pk != null && !pk.equals(ctx.getPackageName())) gamePkg = pk; refreshStates(); }); });
    }

    // ===== الحالات الحقيقية =====
    private void refreshStates() {
        st.put("data", Extras.hudOn); st.put("cross", Extras.crossOn); st.put("color", Extras.tint > 0);
        st.put("magn", Booster.prefs(ctx).getBoolean("magnLive", false)); st.put("fix", Booster.prefs(ctx).getBoolean("fixedLive", false)); st.put("touch", Booster.prefs(ctx).getBoolean("touchLive", false));
        st.put("rotate", Booster.prefs(ctx).getBoolean("rotLockOn", false)); st.put("bellx", Booster.filter(ctx) == NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        st.put("moon", Booster.filter(ctx) == NotificationManager.INTERRUPTION_FILTER_NONE); st.put("auto", Booster.brightOn(ctx));
        st.put("hz", Booster.prefs(ctx).getInt("hzForced", 0) > 0); st.put("perf", gamePkg != null && Booster.gameFlag(ctx, gamePkg, "perfLive", false)); st.put("autoClean", Booster.prefs(ctx).getBoolean("autoClean", false));
    }
    private boolean on(String k) { Boolean b = st.get(k); return b != null && b; }

    private void tick() {
        if (!running) return;
        long tot = Booster.totalMem(ctx), free = Booster.freeMem(ctx); ramFrac = 1f - (float) free / tot;
        final float ghz = Booster.cpuGHz();
        if (ShizukuHelper.granted()) ShizukuHelper.run(() -> { final float u = ShizukuHelper.cpuUsage(); h.post(() -> { if (ghz > 0) { cpuIsGhz = true; cpuNum = ghz; cpuFrac = u >= 0 ? u / 100f : ghz / 3.5f; } else { cpuIsGhz = false; cpuNum = Math.max(0, u); cpuFrac = Math.max(0, u) / 100f; } }); });
        else if (ghz > 0) { cpuIsGhz = true; cpuNum = ghz; cpuFrac = ghz / 3.5f; } else { cpuIsGhz = false; cpuNum = Booster.batteryTemp(ctx); cpuFrac = cpuNum / 50f; }
        h.postDelayed(this::tick, 1000);
    }

    // ===== أدوات الرسم =====
    private static float cl(float x) { return Math.max(0, Math.min(1, x)); }
    private static float eob(float x) { float c1 = 1.70158f, c3 = c1 + 1, k = x - 1; return 1 + c3 * k * k * k + c1 * k * k; }
    private static int lerpC(int a, int b, float k) { return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * k), (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * k), (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k)); }
    private float openK(long now) { return eob(cl((now - t0) / 520f)); }
    private float edgeX(float y, boolean left) {   // حافة اللوحة بالبكسل عند ارتفاع y
        float xt = 235 * U, xv = 299 * U, xb = 170 * U, yv = 105 * U, x = y <= yv ? xt + (xv - xt) * (y / yv) : xv + (xb - xv) * ((y - yv) / (H - yv));
        return left ? x : W - x;
    }

    private void band(Canvas c, boolean left, float shimmer, float slide) {
        float[][] pts = {{235 * U, 0}, {299 * U, 105 * U}, {170 * U, H}};
        float total = 0; float[] leg = new float[2];
        for (int i = 0; i < 2; i++) { leg[i] = (float) Math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]); total += leg[i]; }
        float segLen = 40 * U, gap = 4 * U, th = 24 * U, acc = 0;
        for (int i = 0; i < 2; i++) {
            float dx = (pts[i + 1][0] - pts[i][0]) / leg[i], dy = (pts[i + 1][1] - pts[i][1]) / leg[i], nx = dy, ny = -dx;
            for (float s = 0; s + segLen * .4f < leg[i]; s += segLen + gap) {
                float s1 = Math.min(leg[i], s + segLen), f = (acc + (s + s1) / 2) / total, bright = Math.max(0, 1 - Math.abs(f - shimmer) * 5f);
                int col = lerpC(lerpC(Color.rgb(244, 130, 110), Color.rgb(196, 44, 30), f), Color.rgb(255, 215, 170), bright * .75f);
                float ax = pts[i][0] + dx * s, ay = pts[i][1] + dy * s, bx = pts[i][0] + dx * s1, by = pts[i][1] + dy * s1;
                float[] q = {ax, ay, bx, by, bx + nx * th, by + ny * th, ax + nx * th, ay + ny * th};
                path.reset(); for (int k = 0; k < 4; k++) { float x = left ? q[k * 2] : W - q[k * 2]; if (k == 0) path.moveTo(x, q[k * 2 + 1]); else path.lineTo(x, q[k * 2 + 1]); } path.close();
                p.setStyle(Paint.Style.FILL); p.setColor(Color.argb((int) (70 + 60 * bright), 255, 60, 40)); p.setStrokeWidth(5 * U); p.setStyle(Paint.Style.STROKE); c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL); p.setColor(col); c.drawPath(path, p);
            }
            acc += leg[i];
        }
        // خط الحافة الداخلية الأحمر الداكن
        path.reset(); for (int i = 0; i < 3; i++) { float x = left ? pts[i][0] : W - pts[i][0]; if (i == 0) path.moveTo(x, pts[i][1]); else path.lineTo(x, pts[i][1]); }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * U); p.setColor(Color.rgb(110, 14, 14)); c.drawPath(path, p);
    }

    private void panelFill(Canvas c, boolean left) {
        path.reset(); float x0 = left ? 0 : W;
        path.moveTo(x0, 0); path.lineTo(left ? 235 * U : W - 235 * U, 0); path.lineTo(left ? 299 * U : W - 299 * U, 105 * U); path.lineTo(left ? 170 * U : W - 170 * U, H); path.lineTo(x0, H); path.close();
        p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(0, 0, 0, H, Color.rgb(4, 4, 6), Color.rgb(20, 5, 8), Shader.TileMode.CLAMP)); c.drawPath(path, p); p.setShader(null);
    }

    private void disk(Canvas c, float cx, float cy, float rx, float ry, float spin, float frac, String label) {
        float t = SystemClock.uptimeMillis() / 1000f;
        p.setStyle(Paint.Style.FILL); p.setShader(new RadialGradient(cx, cy + 6 * U, rx * 1.3f, Color.argb(90, 255, 40, 40), 0x00000000, Shader.TileMode.CLAMP)); c.drawCircle(cx, cy + 6 * U, rx * 1.3f, p); p.setShader(null);
        // جانب الأسطوانة
        rf.set(cx - rx, cy - ry + 12 * U, cx + rx, cy + ry + 12 * U); p.setColor(Color.rgb(60, 8, 10)); c.drawOval(rf, p);
        p.setColor(Color.rgb(60, 8, 10)); c.drawRect(cx - rx, cy, cx + rx, cy + 12 * U, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f * U); p.setColor(Color.rgb(255, 60, 60)); rf.set(cx - rx, cy - ry + 12 * U, cx + rx, cy + ry + 12 * U); c.drawArc(rf, 0, 180, false, p);
        // السطح العلوي
        rf.set(cx - rx, cy - ry, cx + rx, cy + ry); p.setStyle(Paint.Style.FILL); p.setShader(new RadialGradient(cx, cy, rx, Color.rgb(110, 14, 18), Color.rgb(34, 6, 8), Shader.TileMode.CLAMP)); c.drawOval(rf, p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * U); p.setColor(Color.rgb(255, 70, 70)); c.drawOval(rf, p);
        for (int i = 1; i <= 3; i++) { float k = 1 - i * .2f; rf.set(cx - rx * k, cy - ry * k, cx + rx * k, cy + ry * k); p.setStrokeWidth(1.2f * U); p.setColor(Color.argb(150 - i * 20, 255, 90, 90)); c.drawOval(rf, p); }
        // قوس التقدّم + اللمعات الدوّارة
        rf.set(cx - rx, cy - ry, cx + rx, cy + ry); p.setStrokeWidth(3.4f * U); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.argb(240, 255, 235, 235)); c.drawArc(rf, 180, 360 * cl(frac), false, p);
        p.setStrokeWidth(2f * U); p.setColor(Color.argb(210, 255, 170, 170)); c.drawArc(rf, spin * 140, 55, false, p); c.drawArc(rf, spin * 140 + 180, 35, false, p); p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(7 * U); p.setColor(Color.rgb(255, 120, 110)); c.drawText(label, cx, cy + ry + 11 * U, p);
    }

    private void bigNumber(Canvas c, float cx, float y, String num, String unit) {
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)); p.setColor(Color.WHITE); p.setTextSize(31 * U); c.drawText(num, cx, y, p);
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(9 * U); c.drawText(unit, cx, y + 11 * U, p);
    }

    private void hexChip(Canvas c, float cx, float cy, String text, String key) {
        float w = 82 * U, hh = 26 * U, x = cx - w / 2, y = cy - hh / 2, e = 8 * U; path.reset();
        path.moveTo(x + e, y); path.lineTo(x + w - e, y); path.lineTo(x + w, cy); path.lineTo(x + w - e, y + hh); path.lineTo(x + e, y + hh); path.lineTo(x, cy); path.close();
        p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(0, y, 0, y + hh, Color.rgb(96, 16, 18), Color.rgb(36, 6, 8), Shader.TileMode.CLAMP)); c.drawPath(path, p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * U); p.setColor(Color.rgb(214, 48, 48)); c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(11.5f * U); c.drawText(text, cx, cy + 4 * U, p);
        Icons.draw(c, "chev", cx, cy + 20 * U, 11 * U, Color.WHITE, p);
        hits.add(new Object[]{new RectF(x, y, x + w, y + hh + 16 * U), key});
    }

    private void stripes(Canvas c, float x, float y0, float v, String icon, String key) {
        int n = 14; float sh = 6.5f * U, w = 55 * U; p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) { boolean fill = (1 - i / (float) n) <= v + .001f; float yy = y0 + i * sh; p.setColor(fill ? Color.argb(250, 245, 245, 245) : Color.argb(110, 150, 150, 150)); c.drawRect(x, yy, x + w, yy + sh - 1.8f * U, p); }
        Icons.draw(c, icon, x + 5 * U, y0 + n * sh + 14 * U, 13 * U, Color.WHITE, p);
        hits.add(new Object[]{new RectF(x - 6 * U, y0 - 6 * U, x + w + 6 * U, y0 + n * sh + 4 * U), key});
    }

    private void tile(Canvas c, String key, String label, float x, float y, float w, float hh, float iconS, float labelS, boolean kebab, long now, float delay) {
        float a = cl((openK(now) * 1f - delay) * 3f); if (a <= 0) return;
        boolean isOn = on(key); float tv = tw.containsKey(key) ? tw.get(key) : (isOn ? 1f : 0f); tv += ((isOn ? 1f : 0f) - tv) * .22f; tw.put(key, tv);
        float pk = 0; Long pa = pressAt.get(key); if (pa != null) { float k = (now - pa) / 420f; if (k < 1) pk = k; }
        float sc = pa != null && now - pa < 100 ? .92f : 1f;
        c.save(); c.translate(x + w / 2, y + hh / 2); c.scale(sc * (.7f + .3f * a), sc * (.7f + .3f * a)); c.translate(-w / 2, -hh / 2);
        int base = lerpC(Color.rgb(38, 38, 40), Color.rgb(156, 18, 16), tv);
        p.setStyle(Paint.Style.FILL); p.setAlpha(255); p.setColor(Color.argb((int) (255 * a), Color.red(base), Color.green(base), Color.blue(base))); rf.set(0, 0, w, hh); c.drawRoundRect(rf, 8 * U, 8 * U, p);
        if (pk > 0) { c.save(); path.reset(); path.addRoundRect(rf, 8 * U, 8 * U, Path.Direction.CW); c.clipPath(path); p.setColor(Color.argb((int) (90 * (1 - pk)), 255, 190, 170)); c.drawCircle(w / 2, hh / 2, pk * w, p); c.restore(); }
        int white = Color.argb((int) (255 * a), 255, 255, 255);
        if (key.equals("hz")) { p.setColor(white); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(iconS * .62f); c.drawText(Extras.hzNow(ctx) + "Hz", w / 2, hh / 2 + iconS * .22f, p); }
        else Icons.draw(c, key.equals("fix") ? "bolt" : key, w / 2, hh / 2, iconS, white, p);
        if (kebab && tv > .3f) Icons.draw(c, "kebab", w - 8 * U, 10 * U, 11 * U, white, p);
        c.restore();
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb((int) (255 * a), 255, 255, 255)); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(labelS);
        c.drawText(label, x + w / 2, y + hh + labelS * 1.25f, p);
        hits.add(new Object[]{new RectF(x, y, x + w, y + hh + labelS * 1.6f), key});
    }

    // ===== الرسم الرئيسي =====
    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis(); W = getWidth(); H = getHeight(); U = H / 365f; hits.clear();
        float k = openK(now), out = closeT > 0 ? cl((now - closeT) / 240f) : 0, slide = Math.max(0, k) * (1 - out), spin = (now - t0) / 1000f, shimmer = ((now - t0) / 1600f) % 1f;
        float scrim = Math.min(cl((now - t0) / 300f), 1 - out); c.drawColor(Color.argb((int) (120 * scrim), 0, 0, 0));
        dCpuNum += (cpuNum - dCpuNum) * .12f; dCpuFrac += (cpuFrac - dCpuFrac) * .12f; dRamFrac += (ramFrac - dRamFrac) * .12f;
        float panelW = 300 * U;
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0; float off = (left ? -1 : 1) * panelW * (1 - slide);
            c.save(); c.translate(off, 0);
            panelFill(c, left);
            float stag = 0;
            if (left) drawLeft(c, now, slide); else drawRight(c, now, slide);
            band(c, left, shimmer, slide);
            c.restore();
        }
        if (out >= 1f) { closer.close(); return; }
        postInvalidateOnAnimation();
    }

    private void drawLeft(Canvas c, long now, float slide) {
        // أزرار الأعلى
        String[] ic = {"home", "gear", "pad", "power"}; float[] bx = {35, 87, 139, 191};
        for (int i = 0; i < 4; i++) { float cx = bx[i] * U, cy = 22 * U; p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(30, 30, 32)); c.drawCircle(cx, cy, 19 * U, p); Icons.draw(c, ic[i], cx, cy, 20 * U, Color.WHITE, p); hits.add(new Object[]{new RectF(cx - 19 * U, cy - 19 * U, cx + 19 * U, cy + 19 * U), "top" + i}); }
        p.setStrokeWidth(1.2f * U); p.setColor(Color.argb(90, 255, 255, 255)); p.setStyle(Paint.Style.STROKE); c.drawLine(0, 48 * U, 238 * U, 48 * U, p);
        // مبدّل القائمة/الشبكة
        float sx = 18 * U, sy = 65 * U, sw = 95 * U, sh = 28 * U; float sk = cl((now - seg0) / 220f); float pos = listMode ? 0 : 1; if (seg0 > 0 && sk < 1) pos = listMode ? 1 - sk : sk;
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(34, 34, 36)); rf.set(sx, sy, sx + sw, sy + sh); c.drawRoundRect(rf, 6 * U, 6 * U, p);
        p.setColor(Color.rgb(150, 18, 16)); rf.set(sx + pos * sw / 2, sy, sx + pos * sw / 2 + sw / 2, sy + sh); c.drawRoundRect(rf, 6 * U, 6 * U, p);
        Icons.draw(c, "list", sx + sw * .25f, sy + sh / 2, 17 * U, Color.WHITE, p); Icons.draw(c, "grid", sx + sw * .75f, sy + sh / 2, 17 * U, Color.WHITE, p);
        hits.add(new Object[]{new RectF(sx, sy, sx + sw / 2, sy + sh), "segL"}); hits.add(new Object[]{new RectF(sx + sw / 2, sy, sx + sw, sy + sh), "segG"});
        // العدّاد
        float gcx = 182 * U; bigNumber(c, gcx, 82 * U, cpuIsGhz ? String.format(Locale.US, "%.2f", dCpuNum) : String.valueOf(Math.round(dCpuNum)), cpuIsGhz ? "GHz" : "%");
        disk(c, gcx, 118 * U, 62 * U, 24 * U, (now - t0) / 1000f, dCpuFrac, "CPU");
        hexChip(c, gcx, 170 * U, on("perf") ? "وضع التسريع" : "الوضع العادي", "chipPerf");
        stripes(c, 145 * U, 238 * U, briV, "sun", "barBri");
        // قائمة الأدوات (قابلة للتمرير)
        c.save(); c.clipRect(0, 98 * U, 130 * U, H);
        float y0 = 104 * U - scroll;
        for (int i = 0; i < LEFT.length; i++) {
            if (listMode) tile(c, LEFT[i][0], LEFT[i][1], 18 * U, y0 + i * 62 * U, 95 * U, 42 * U, 22 * U, 11 * U, true, now, i * .06f);
            else tile(c, LEFT[i][0], LSHORT[i], 18 * U + (i % 2) * 50 * U, y0 + (i / 2) * 62 * U, 45 * U, 42 * U, 20 * U, 8.5f * U, false, now, i * .06f);
        }
        c.restore();
    }

    private void drawRight(Canvas c, long now, float slide) {
        for (int i = 0; i < 4; i++) {
            float cx = W - new float[]{188, 134, 81, 29}[i] * U, cy = 22 * U;
            if (i < appIcons.size()) { Drawable d = appIcons.get(i); int r = (int) (19 * U); d.setBounds((int) cx - r, (int) cy - r, (int) cx + r, (int) cy + r); c.save(); path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW); c.clipPath(path); d.draw(c); c.restore(); hits.add(new Object[]{new RectF(cx - r, cy - r, cx + r, cy + r), "app" + i}); }
        }
        p.setStrokeWidth(1.2f * U); p.setColor(Color.argb(90, 255, 255, 255)); p.setStyle(Paint.Style.STROKE); c.drawLine(W - 238 * U, 48 * U, W, 48 * U, p);
        float gcx = W - 191 * U; bigNumber(c, gcx, 82 * U, String.valueOf(Math.round(dRamFrac * 100)) + "%", "");
        disk(c, gcx, 118 * U, 62 * U, 24 * U, (now - t0) / 1000f + .5f, dRamFrac, "RAM");
        hexChip(c, gcx, 170 * U, on("autoClean") ? "تنظيف تلقائي" : "الوضع العادي", "chipClean");
        stripes(c, W - 200 * U, 238 * U, volV, "spk", "barVol");
        for (int i = 0; i < RIGHT.length; i++) {
            float x = W - (i % 2 == 0 ? 104 : 47) * U, y = (49 + (i / 2) * 57) * U; if (i % 2 == 0) x = W - 104 * U; else x = W - 47 * U;
            tile(c, RIGHT[i][0], RIGHT[i][1], x, y, 38 * U, 38 * U, 21 * U, 9.5f * U, false, now, i * .05f);
        }
    }

    // ===== اللمس =====
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (closeT > 0 || openK(SystemClock.uptimeMillis()) < .85f) return true;
        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN: downKey = hit(x, y); downY = y; downScroll = scroll; dragged = false;
                if (downKey != null) { pressAt.put(downKey, SystemClock.uptimeMillis()); if (downKey.startsWith("bar")) bar(downKey, y); }
                else if (x > edgeX(y, true) && x < edgeX(y, false)) { downKey = "outside"; }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (downKey != null && downKey.startsWith("bar")) { bar(downKey, y); return true; }
                if (Math.abs(y - downY) > 14 * U / 2) dragged = true;
                if (dragged && x < 130 * U) { float max = Math.max(0, (104 * U + LEFT.length * 62 * U) - H + 20 * U); if (!listMode) max = Math.max(0, 104 * U + ((LEFT.length + 1) / 2) * 62 * U - H + 20 * U); scroll = Math.max(0, Math.min(max, downScroll + (downY - y))); }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragged && downKey != null) act(downKey);
                downKey = null; return true;
        }
        return true;
    }
    private String hit(float x, float y) { for (int i = hits.size() - 1; i >= 0; i--) { Object[] o = hits.get(i); if (((RectF) o[0]).contains(x, y)) return (String) o[1]; } return null; }
    private void bar(String k, float y) {
        float y0 = 238 * U, hgt = 14 * 6.5f * U, v = cl(1 - (y - y0) / hgt);
        if (k.equals("barBri")) { briV = v; final int val = Math.round(v * 255);
            if (Booster.hasWriteSettings(ctx)) { try { Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0); Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, val); } catch (Exception ignored) {} }
            else if (ShizukuHelper.granted()) ShizukuHelper.run(() -> { ShizukuHelper.settingsPut("system", "screen_brightness_mode", "0"); ShizukuHelper.settingsPut("system", "screen_brightness", String.valueOf(val)); }); }
        else { volV = v; Booster.setMediaVolumePct(ctx, Math.round(v * 100)); }
    }
    private void toast(String s) { Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show(); }
    private boolean needSz() { if (ShizukuHelper.granted()) return true; toast("يحتاج تفعيل Shizuku"); return false; }

    private void act(String k) {
        if (k.equals("outside")) { dismiss(); return; }
        if (k.startsWith("app")) { int i = Integer.parseInt(k.substring(3)); Intent li = ctx.getPackageManager().getLaunchIntentForPackage(appPkgs.get(i)); if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); dismiss(); ctx.startActivity(li); } return; }
        switch (k) {
            case "top0": go(LauncherActivity.class); break; case "top1": go(SettingsActivity.class); break; case "top2": go(HubActivity.class); break;
            case "top3": dismiss(); ctx.stopService(new Intent(ctx, OverlayService.class)); break;
            case "segL": if (!listMode) { listMode = true; seg0 = SystemClock.uptimeMillis(); scroll = 0; } break; case "segG": if (listMode) { listMode = false; seg0 = SystemClock.uptimeMillis(); scroll = 0; } break;
            case "data": Extras.toggleHud(ctx); break; case "cross": Extras.toggleCross(ctx); break; case "color": Extras.cycleTint(ctx); break;
            case "magn": if (needSz()) { final boolean v = !on("magn"); Booster.prefs(ctx).edit().putBoolean("magnLive", v).apply(); ShizukuHelper.run(() -> ShizukuHelper.settingsPut("secure", "accessibility_display_magnification_enabled", v ? "1" : "0")); toast(v ? "التكبير: اضغط ثلاثاً على الشاشة" : "التكبير متوقف"); } break;
            case "fix": if (needSz()) { final boolean v = !on("fix"); Booster.prefs(ctx).edit().putBoolean("fixedLive", v).apply(); ShizukuHelper.run(() -> { final String o = ShizukuHelper.fixedPerformance(v); h.post(() -> toast("أداء ثابت: " + o)); }); } break;
            case "touch": if (needSz()) { final boolean v = !on("touch"); Booster.prefs(ctx).edit().putBoolean("touchLive", v).apply(); ShizukuHelper.run(() -> ShizukuHelper.touchBoost(v)); toast(v ? "اللمس أسرع" : "اللمس عادي"); } break;
            case "shot": if (needSz()) { ShizukuHelper.run(() -> { final String f = ShizukuHelper.screenshot(); h.post(() -> toast(f != null ? "حُفظت اللقطة: " + f : "تعذّر أخذ اللقطة")); }); } break;
            case "rotate": { boolean v = !on("rotate"); if (Booster.setRotationLock(ctx, v)) Booster.prefs(ctx).edit().putBoolean("rotLockOn", v).apply(); else toast("امنح صلاحية تعديل الإعدادات"); break; }
            case "bellx": if (!Booster.setFilter(ctx, on("bellx") ? NotificationManager.INTERRUPTION_FILTER_ALL : NotificationManager.INTERRUPTION_FILTER_PRIORITY)) toast("امنح صلاحية عدم الإزعاج"); break;
            case "moon": if (!Booster.setFilter(ctx, on("moon") ? NotificationManager.INTERRUPTION_FILTER_ALL : NotificationManager.INTERRUPTION_FILTER_NONE)) toast("امنح صلاحية عدم الإزعاج"); break;
            case "auto": if (!Booster.setBright(ctx, !Booster.brightOn(ctx))) { if (needSz()) { final boolean v = !Booster.brightOn(ctx); Booster.prefs(ctx).edit().putBoolean("bright", v).apply(); ShizukuHelper.run(() -> { ShizukuHelper.settingsPut("system", "screen_brightness_mode", "0"); ShizukuHelper.settingsPut("system", "screen_brightness", v ? "255" : "128"); }); briV = v ? 1f : .5f; } } else briV = Booster.brightOn(ctx) ? 1f : .5f; break;
            case "hz": if (needSz()) ShizukuHelper.run(() -> { final int r = Extras.nextHz(ctx); h.post(() -> toast("معدّل التحديث: " + r + "Hz")); }); break;
            case "wifi": try { Intent i = new Intent(android.os.Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); } catch (Exception ignored) {} break;
            case "rocket": { final long f = Booster.killBackground(ctx, gamePkg); if (ShizukuHelper.granted()) ShizukuHelper.run(() -> { ShizukuHelper.forceStopOthers(ctx, gamePkg); ShizukuHelper.trimCaches(); }); toast("معزّز: تحرّر ~" + f + " MB" + (ShizukuHelper.granted() ? " + إغلاق قسري" : "")); break; }
            case "broom": if (needSz()) ShizukuHelper.run(() -> { final String o = ShizukuHelper.trimCaches(); h.post(() -> toast("تنظيف الكاش: " + o)); }); else toast("تنظيف عادي: ~" + Booster.killBackground(ctx, gamePkg) + " MB"); break;
            case "chipPerf": if (needSz() && gamePkg != null) { final boolean v = !on("perf"); Booster.setGameFlag(ctx, gamePkg, "perfLive", v); final String pk = gamePkg; ShizukuHelper.run(() -> { final String o = ShizukuHelper.gameMode(pk, v ? "performance" : "standard"); h.post(() -> toast(v ? "وضع التسريع: " + o : "الوضع العادي")); }); } else if (gamePkg == null && ShizukuHelper.granted()) toast("لم أتعرف على اللعبة المفتوحة"); break;
            case "chipClean": { boolean v = !on("autoClean"); Booster.prefs(ctx).edit().putBoolean("autoClean", v).apply(); toast(v ? "تنظيف تلقائي كل دقيقة ونصف" : "التنظيف التلقائي متوقف"); break; }
        }
        refreshStates();
    }
    private void go(Class<?> cls) { dismiss(); ctx.startActivity(new Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }

    void dismiss() { if (closeT < 0) { closeT = SystemClock.uptimeMillis(); running = false; h.removeCallbacksAndMessages(null); invalidate(); } }
}

package com.gamebooster.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;

import java.util.List;
import java.util.Locale;

/** اللوحة التي تظهر فوق اللعبة: عدّادات حقيقية متحركة + أدوات تعمل فعلاً. */
public class GamePanel extends FrameLayout {
    interface Closer { void close(); }
    private static final int ACC = Color.rgb(255, 165, 31);
    private final Context ctx; private final Closer closer;
    private final Handler h = new Handler(Looper.getMainLooper());
    private HoloGauge cpu, ram; private TextView cpuChip, ramChip;
    private Tile tDnd, tBright, tRot, tShot, tClean, tPerf, tFixed, tTouch;
    private String gamePkg; private boolean running;
    private final LinearLayout leftP, rightP; private final View scrim;
    private final float[] stagger = {0};

    // ===== عدّاد هولوغرافي بأرقام تتحرك =====
    static class HoloGauge extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final long t0 = SystemClock.uptimeMillis();
        float frac, dispFrac, num, dispNum; String unit = "", label = ""; int color = ACC; boolean decimals;
        HoloGauge(Context c, String label, int color) { super(c); this.label = label; this.color = color; }
        void set(float fraction, float number, String unit, boolean decimals) {
            this.unit = unit; this.decimals = decimals; final float f0 = dispFrac, n0 = dispNum;
            ValueAnimator a = ValueAnimator.ofFloat(0, 1); a.setDuration(600); a.setInterpolator(new DecelerateInterpolator());
            final float tf = Math.max(0, Math.min(1, fraction));
            a.addUpdateListener(an -> { float k = (float) an.getAnimatedValue(); dispFrac = f0 + (tf - f0) * k; dispNum = n0 + (number - n0) * k; invalidate(); }); a.start();
        }
        @Override protected void onDraw(Canvas c) {
            float t = (SystemClock.uptimeMillis() - t0) / 1000f, w = getWidth(), h = getHeight(), cx = w / 2, cy = h * .66f, r = Math.min(w * .42f, h * .55f);
            // رقم كبير
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)); p.setColor(Color.WHITE); p.setTextSize(h * .30f);
            String s = decimals ? String.format(Locale.US, "%.2f", dispNum) : String.valueOf(Math.round(dispNum));
            c.drawText(s + unit, cx, h * .36f, p);
            // حلقات هولوغرافية
            RectF rf = new RectF();
            for (int i = 0; i < 4; i++) {
                float rr = r * (1 - i * .09f), yy = cy + i * 3; rf.set(cx - rr, yy - rr * .30f, cx + rr, yy + rr * .30f);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(h * (.05f - i * .008f));
                p.setColor(Color.argb((int) (70 + 40 * i + 30 * Math.sin(t * 3 + i)), Color.red(color), Color.green(color), Color.blue(color))); c.drawOval(rf, p);
            }
            // قوس التقدّم (الاستهلاك) فوق الحلقة العليا
            rf.set(cx - r, cy - r * .30f, cx + r, cy + r * .30f);
            p.setStrokeWidth(h * .065f); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.WHITE); p.setAlpha(230);
            c.drawArc(rf, 180, 360 * dispFrac, false, p);
            p.setAlpha(255); p.setStrokeCap(Paint.Cap.BUTT);
            // لمعة دوّارة
            p.setStrokeWidth(h * .03f); p.setColor(Color.argb(200, 255, 255, 255)); c.drawArc(rf, t * 160, 40, false, p);
            // اسم العدّاد
            p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(h * .11f); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText(label, cx, h * .97f, p);
            postInvalidateOnAnimation();
        }
    }

    // ===== شريط جانبي مائل متحرك =====
    static class Chevron extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final long t0 = SystemClock.uptimeMillis(); final boolean leftSide;
        Chevron(Context c, boolean leftSide) { super(c); this.leftSide = leftSide; }
        @Override protected void onDraw(Canvas c) {
            float t = (SystemClock.uptimeMillis() - t0) / 1000f, w = getWidth(), h = getHeight(); int n = 14; float sh = h / n;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                float y = i * sh, k = ((i / (float) n) - t * .35f) % 1f; if (k < 0) k += 1f;
                int a = (int) (90 + 165 * (1 - k));
                p.setColor(Color.argb(a, 255, (int) (80 + 100 * (1 - k)), 50));
                Path pa = new Path(); float s = w * .55f * (leftSide ? 1 : -1), x0 = leftSide ? w * .1f : w * .9f, x1 = leftSide ? w * .9f : w * .1f;
                pa.moveTo(x0, y + sh * .12f); pa.lineTo(x1, y + sh * .12f + (leftSide ? sh * .2f : sh * .2f)); pa.lineTo(x1, y + sh * .85f); pa.lineTo(x0, y + sh * .65f); pa.close();
                c.drawPath(pa, p);
            }
            postInvalidateOnAnimation();
        }
    }

    // ===== زر أداة بحركة ضغط وتموّج =====
    static class Tile extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); String icon, label, state = ""; boolean on; float rx, ry, ripple = -1;
        Tile(Context c, String icon, String label) {
            super(c); this.icon = icon; this.label = label;
            setOnTouchListener((v, e) -> {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: rx = e.getX(); ry = e.getY(); ObjectAnimator.ofFloat(this, "scaleX", .9f).setDuration(90).start(); ObjectAnimator.ofFloat(this, "scaleY", .9f).setDuration(90).start();
                        ValueAnimator a = ValueAnimator.ofFloat(0, 1); a.setDuration(380); a.addUpdateListener(an -> { ripple = (float) an.getAnimatedValue(); invalidate(); }); a.start(); return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        ObjectAnimator.ofFloat(this, "scaleX", 1f).setDuration(160).start(); ObjectAnimator.ofFloat(this, "scaleY", 1f).setDuration(160).start();
                        if (e.getAction() == MotionEvent.ACTION_UP && e.getX() >= 0 && e.getX() <= getWidth() && e.getY() >= 0 && e.getY() <= getHeight()) performClick(); return true;
                }
                return true;
            });
        }
        void setOn(boolean on, String st) { this.on = on; this.state = st == null ? "" : st; invalidate(); }
        @Override public boolean performClick() { return super.performClick(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(); RectF r = new RectF(2, 2, w - 2, h - 2);
            p.setStyle(Paint.Style.FILL); p.setColor(on ? Color.argb(70, 255, 165, 31) : Color.argb(220, 28, 30, 46)); c.drawRoundRect(r, h * .2f, h * .2f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(on ? 3 : 1.5f); p.setColor(on ? ACC : Color.argb(90, 255, 255, 255)); c.drawRoundRect(r, h * .2f, h * .2f, p);
            if (ripple >= 0 && ripple < 1) { c.save(); Path cp = new Path(); cp.addRoundRect(r, h * .2f, h * .2f, Path.Direction.CW); c.clipPath(cp); p.setStyle(Paint.Style.FILL); p.setColor(Color.argb((int) (110 * (1 - ripple)), 255, 200, 120)); c.drawCircle(rx, ry, ripple * w, p); c.restore(); }
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTextSize(h * .34f); c.drawText(icon, w / 2, h * .46f, p);
            p.setTextSize(h * .16f); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText(label, w / 2, h * .70f, p);
            p.setTextSize(h * .13f); p.setTypeface(Typeface.DEFAULT); p.setColor(on ? ACC : Color.rgb(160, 168, 195)); c.drawText(state, w / 2, h * .88f, p);
        }
    }

    // ===== شريط عمودي قابل للسحب (سطوع / صوت) =====
    static class VBar extends View {
        interface Change { void on(float v); }
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float v; final String icon; final Change cb;
        VBar(Context c, String icon, float v, Change cb) { super(c); this.icon = icon; this.v = v; this.cb = cb; }
        @Override public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float top = getWidth() * .2f, bot = getHeight() - getWidth() * 1.1f; v = Math.max(0, Math.min(1, 1 - (e.getY() - top) / (bot - top))); invalidate();
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_MOVE) cb.on(v);
            return true;
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), top = w * .2f, bot = h - w * 1.1f; int n = 16; float sh = (bot - top) / n;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) { boolean on = (1 - i / (float) n) <= v + .001f; p.setColor(on ? Color.argb(240, 255, (int) (255 - i * 6), (int) (255 - i * 14)) : Color.argb(60, 255, 255, 255)); c.drawRoundRect(w * .15f, top + i * sh + 2, w * .85f, top + (i + 1) * sh - 2, 4, 4, p); }
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(w * .9f); p.setColor(Color.WHITE); c.drawText(icon, w / 2, h - w * .2f, p);
        }
    }

    // ===== بناء اللوحة =====
    public GamePanel(Context c, Closer closer) {
        super(c); this.ctx = c; this.closer = closer;
        scrim = new View(c); scrim.setBackgroundColor(Color.argb(130, 0, 0, 0)); scrim.setOnClickListener(v -> dismiss());
        addView(scrim, new LayoutParams(-1, -1));
        int pw = (int) Math.min(c.getResources().getDisplayMetrics().widthPixels * .32f, UiUtil.dp(this, 310));
        leftP = new LinearLayout(c); rightP = new LinearLayout(c);
        buildLeft(); buildRight();
        addView(leftP, new LayoutParams(pw, -1, Gravity.START)); addView(rightP, new LayoutParams(pw, -1, Gravity.END));
        setAlpha(0f);
        // معرفة اللعبة الحالية
        ShizukuHelper.run(() -> { final String p = ShizukuHelper.topPackage(); h.post(() -> { if (p != null && !p.equals(ctx.getPackageName())) gamePkg = p; refreshTiles(); }); });
        post(this::animateIn);
    }

    private LinearLayout column(boolean left) {
        LinearLayout row = left ? leftP : rightP; row.setOrientation(LinearLayout.HORIZONTAL); row.setBackground(UiUtil.round(Color.argb(235, 14, 16, 28), 0, 0, 0));
        LinearLayout col = new LinearLayout(ctx); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(UiUtil.dp(row, 8), UiUtil.dp(row, 8), UiUtil.dp(row, 8), UiUtil.dp(row, 8));
        return col;
    }
    private Tile tile(String ic, String lb) { return new Tile(ctx, ic, lb); }
    private GridLayout grid(Tile... ts) {
        GridLayout g = new GridLayout(ctx); g.setColumnCount(2);
        for (Tile t : ts) { GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)); lp.width = 0; lp.height = UiUtil.dp(g, 64); lp.setMargins(4, 4, 4, 4); g.addView(t, lp); }
        return g;
    }
    private TextView chip() { TextView t = UiUtil.text(ctx, "", 12, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setBackground(UiUtil.round(Color.argb(60, 255, 165, 31), UiUtil.dp(t, 8), ACC, 2)); t.setPadding(0, UiUtil.dp(t, 5), 0, UiUtil.dp(t, 5)); return t; }
    private View circle(String s, OnClickListener l) {
        TextView t = UiUtil.text(ctx, s, 16, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setBackground(UiUtil.round(Color.argb(200, 40, 42, 60), UiUtil.dp(t, 20), 0, 0)); t.setOnClickListener(l);
        t.setOnTouchListener((v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(.85f).scaleY(.85f).setDuration(80).start(); else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(140).start(); return false; });
        return t;
    }

    private void buildLeft() {
        LinearLayout col = column(true);
        LinearLayout head = new LinearLayout(ctx); head.setGravity(Gravity.CENTER_VERTICAL);
        int s = UiUtil.dp(col, 36);
        head.addView(circle("⌂", v -> { dismiss(); ctx.startActivity(new Intent(ctx, LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }), new LinearLayout.LayoutParams(s, s));
        head.addView(circle("⚙", v -> { dismiss(); ctx.startActivity(new Intent(ctx, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }), new LinearLayout.LayoutParams(s, s));
        head.addView(circle("⏻", v -> { dismiss(); ctx.stopService(new Intent(ctx, OverlayService.class)); }), new LinearLayout.LayoutParams(s, s));
        col.addView(head);
        cpu = new HoloGauge(ctx, "المعالج", Color.rgb(255, 90, 50)); col.addView(cpu, new LinearLayout.LayoutParams(-1, UiUtil.dp(col, 110)));
        cpuChip = chip(); col.addView(cpuChip);
        tShot = tile("📸", "لقطة شاشة"); tDnd = tile("🔕", "عدم الإزعاج"); tBright = tile("☀️", "سطوع أعلى"); tRot = tile("🔒", "قفل التدوير");
        col.addView(grid(tShot, tDnd, tBright, tRot));
        VBar br = new VBar(ctx, "☀", .7f, v -> { int val = Math.round(v * 255); if (Booster.hasWriteSettings(ctx)) { try { android.provider.Settings.System.putInt(ctx.getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 0); android.provider.Settings.System.putInt(ctx.getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, val); } catch (Exception ignored) {} } else ShizukuHelper.run(() -> { ShizukuHelper.settingsPut("system", "screen_brightness_mode", "0"); ShizukuHelper.settingsPut("system", "screen_brightness", String.valueOf(val)); }); });
        try { br.v = android.provider.Settings.System.getInt(ctx.getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f; } catch (Exception ignored) {}
        leftP.addView(br, new LinearLayout.LayoutParams(UiUtil.dp(leftP, 26), -1)); leftP.addView(col, new LinearLayout.LayoutParams(0, -1, 1));
        leftP.addView(new Chevron(ctx, true), new LinearLayout.LayoutParams(UiUtil.dp(leftP, 22), -1));
        tShot.setOnClickListener(v -> shot()); tDnd.setOnClickListener(v -> { boolean on = !Booster.dndOn(ctx); if (!Booster.setDnd(ctx, on)) toast("امنح صلاحية عدم الإزعاج"); refreshTiles(); });
        tBright.setOnClickListener(v -> { if (!Booster.setBright(ctx, !Booster.brightOn(ctx))) { if (ShizukuHelper.granted()) { final boolean on = !Booster.brightOn(ctx); Booster.prefs(ctx).edit().putBoolean("bright", on).apply(); ShizukuHelper.run(() -> { ShizukuHelper.settingsPut("system", "screen_brightness_mode", "0"); ShizukuHelper.settingsPut("system", "screen_brightness", on ? "255" : "128"); }); } else toast("امنح صلاحية تعديل الإعدادات"); } refreshTiles(); });
        tRot.setOnClickListener(v -> { boolean on = !Booster.prefs(ctx).getBoolean("rotLockOn", false); if (Booster.setRotationLock(ctx, on)) Booster.prefs(ctx).edit().putBoolean("rotLockOn", on).apply(); else toast("امنح صلاحية تعديل الإعدادات"); refreshTiles(); });
    }

    private void buildRight() {
        LinearLayout col = column(false);
        // أيقونات ألعاب سريعة (تشغيل حقيقي)
        LinearLayout apps = new LinearLayout(ctx); apps.setGravity(Gravity.CENTER_VERTICAL);
        PackageManager pm = ctx.getPackageManager(); List<ResolveInfo> games = Booster.games(ctx); int sz = UiUtil.dp(col, 36);
        for (int i = 0; i < Math.min(4, games.size()); i++) {
            final String pkg = games.get(i).activityInfo.packageName; ImageView iv = new ImageView(ctx); iv.setImageDrawable(games.get(i).loadIcon(pm));
            iv.setOnClickListener(v -> { Intent li = pm.getLaunchIntentForPackage(pkg); if (li != null) { dismiss(); ctx.startActivity(li); } });
            iv.setOnTouchListener((v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(.85f).scaleY(.85f).setDuration(80).start(); else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(140).start(); return false; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz); lp.setMargins(4, 0, 4, 0); apps.addView(iv, lp);
        }
        col.addView(apps);
        ram = new HoloGauge(ctx, "الذاكرة", Color.rgb(255, 205, 40)); col.addView(ram, new LinearLayout.LayoutParams(-1, UiUtil.dp(col, 110)));
        ramChip = chip(); col.addView(ramChip);
        tClean = tile("🧹", "تنظيف الذاكرة"); tPerf = tile("🚀", "وضع الأداء"); tFixed = tile("⚡", "أداء ثابت"); tTouch = tile("👆", "لمس أسرع");
        col.addView(grid(tClean, tPerf, tFixed, tTouch));
        VBar vol = new VBar(ctx, "🔊", Booster.mediaVolumePct(ctx) / 100f, v -> Booster.setMediaVolumePct(ctx, Math.round(v * 100)));
        rightP.addView(new Chevron(ctx, false), new LinearLayout.LayoutParams(UiUtil.dp(rightP, 22), -1)); rightP.addView(col, new LinearLayout.LayoutParams(0, -1, 1));
        rightP.addView(vol, new LinearLayout.LayoutParams(UiUtil.dp(rightP, 26), -1));
        tClean.setOnClickListener(v -> clean());
        tPerf.setOnClickListener(v -> { if (!needSz() || gamePkg == null) { if (gamePkg == null && ShizukuHelper.granted()) toast("لم أتعرف على اللعبة المفتوحة"); return; }
            final boolean on = !Booster.gameFlag(ctx, gamePkg, "perfLive", false); Booster.setGameFlag(ctx, gamePkg, "perfLive", on); final String pk = gamePkg;
            ShizukuHelper.run(() -> { String o = ShizukuHelper.gameMode(pk, on ? "performance" : "standard"); h.post(() -> toast(on ? "وضع الأداء: " + o : "رجع للوضع العادي")); }); refreshTiles(); });
        tFixed.setOnClickListener(v -> { if (!needSz()) return; final boolean on = !Booster.prefs(ctx).getBoolean("fixedLive", false); Booster.prefs(ctx).edit().putBoolean("fixedLive", on).apply();
            ShizukuHelper.run(() -> { String o = ShizukuHelper.fixedPerformance(on); h.post(() -> toast("أداء ثابت: " + o)); }); refreshTiles(); });
        tTouch.setOnClickListener(v -> { if (!needSz()) return; final boolean on = !Booster.prefs(ctx).getBoolean("touchLive", false); Booster.prefs(ctx).edit().putBoolean("touchLive", on).apply();
            ShizukuHelper.run(() -> ShizukuHelper.touchBoost(on)); refreshTiles(); toast(on ? "اللمس أسرع" : "اللمس عادي"); });
    }

    private boolean needSz() { if (ShizukuHelper.granted()) return true; toast("يحتاج تفعيل Shizuku"); return false; }
    private void toast(String s) { Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show(); }

    private void shot() {
        if (!needSz()) return;
        ShizukuHelper.run(() -> { final String f = ShizukuHelper.screenshot(); h.post(() -> toast(f != null ? "📸 حُفظت: " + f : "تعذّر أخذ اللقطة")); });
    }
    private void clean() {
        final long freed = Booster.killBackground(ctx, gamePkg);
        if (ShizukuHelper.granted()) ShizukuHelper.run(() -> { ShizukuHelper.forceStopOthers(ctx, gamePkg); ShizukuHelper.trimCaches(); });
        toast("🧹 تحرّر ~" + freed + " MB" + (ShizukuHelper.granted() ? " + الكاش" : "")); tick();
    }
    private void refreshTiles() {
        tDnd.setOn(Booster.dndOn(ctx), Booster.dndOn(ctx) ? "مفعّل" : "متوقف");
        tBright.setOn(Booster.brightOn(ctx), Booster.brightOn(ctx) ? "مفعّل" : "متوقف");
        boolean rot = Booster.prefs(ctx).getBoolean("rotLockOn", false); tRot.setOn(rot, rot ? "مقفول" : "حر");
        tShot.setOn(false, ShizukuHelper.granted() ? "جاهز" : "Shizuku");
        tClean.setOn(false, "اضغط");
        boolean perf = gamePkg != null && Booster.gameFlag(ctx, gamePkg, "perfLive", false); tPerf.setOn(perf, ShizukuHelper.granted() ? (perf ? "عالٍ" : "عادي") : "Shizuku");
        boolean fx = Booster.prefs(ctx).getBoolean("fixedLive", false); tFixed.setOn(fx, ShizukuHelper.granted() ? (fx ? "مفعّل" : "متوقف") : "Shizuku");
        boolean tc = Booster.prefs(ctx).getBoolean("touchLive", false); tTouch.setOn(tc, ShizukuHelper.granted() ? (tc ? "مفعّل" : "متوقف") : "Shizuku");
    }

    // ===== التحديث الحي للأرقام =====
    private void tick() {
        if (!running) return;
        long tot = Booster.totalMem(ctx), free = Booster.freeMem(ctx); final float used = 1f - (float) free / tot; final float temp = Booster.batteryTemp(ctx); final int bat = Booster.batteryPct(ctx);
        ram.set(used, used * 100, "%", false);
        ramChip.setText("🌡 " + Math.round(temp) + "°   🔋 " + bat + "%   متاح " + free / (1024 * 1024) + "MB");
        if (ShizukuHelper.granted()) ShizukuHelper.run(() -> {
            final float u = ShizukuHelper.cpuUsage(), g = ShizukuHelper.gpuBusy(), ghz = Booster.cpuGHz();
            h.post(() -> {
                if (u >= 0) cpu.set(u / 100f, u, "%", false);
                cpuChip.setText((ghz > 0 ? String.format(Locale.US, "%.2f GHz", ghz) : "") + (g >= 0 ? "   GPU " + Math.round(g) + "%" : ""));
            });
        }); else { float ghz = Booster.cpuGHz(); if (ghz > 0) cpu.set(ghz / 3.5f, ghz, " GHz", true); else cpu.set(temp / 50f, temp, "°", false); cpuChip.setText("فعّل Shizuku لقراءة الاستهلاك"); }
        h.postDelayed(this::tick, 1000);
    }

    // ===== الحركة =====
    private void animateIn() {
        running = true; refreshTiles(); tick();
        animate().alpha(1f).setDuration(220).start();
        leftP.setTranslationX(-leftP.getWidth()); rightP.setTranslationX(rightP.getWidth());
        leftP.animate().translationX(0).setDuration(420).setInterpolator(new OvershootInterpolator(.8f)).start();
        rightP.animate().translationX(0).setDuration(420).setInterpolator(new OvershootInterpolator(.8f)).start();
        Tile[] all = {tShot, tDnd, tBright, tRot, tClean, tPerf, tFixed, tTouch};
        for (int i = 0; i < all.length; i++) { Tile t = all[i]; t.setAlpha(0f); t.setScaleX(.6f); t.setScaleY(.6f); t.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(160 + i * 55L).setDuration(300).setInterpolator(new OvershootInterpolator(1.2f)).start(); }
    }
    void dismiss() {
        running = false; h.removeCallbacksAndMessages(null);
        scrim.animate().alpha(0f).setDuration(200).start();
        leftP.animate().translationX(-leftP.getWidth()).setDuration(240).start(); rightP.animate().translationX(rightP.getWidth()).setDuration(240).withEndAction(closer::close).start();
    }
}

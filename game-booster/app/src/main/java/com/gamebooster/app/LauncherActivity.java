package com.gamebooster.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;

/** القائمة الرئيسية: ألعابك + حالة الجهاز الحقيقية + خيارات كل لعبة + بدء اللعب بتسريع حقيقي. */
public class LauncherActivity extends Activity {
    private List<ResolveInfo> games = new ArrayList<>();
    private int sel = 0;
    private LinearLayout list, chips;
    private BgView bgView; private StageView stage; private String shownPkg;
    private TextView name, info, clock, cRam, cCpu, cTemp, cBat, cSz, empty, play;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean first = true;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            clock.setText(new SimpleDateFormat("hh:mm", Locale.getDefault()).format(new Date()));
            long tot = Booster.totalMem(LauncherActivity.this), free = Booster.freeMem(LauncherActivity.this); float used = 100f * (1 - (float) free / tot), temp = Booster.batteryTemp(LauncherActivity.this);
            setChip(cRam, Math.round(used) + "%", used > 85); setChip(cTemp, Math.round(temp) + "°", temp >= 42);
            setChip(cBat, Booster.batteryPct(LauncherActivity.this) + "%", false); chipIcon(cBat, Booster.isCharging(LauncherActivity.this) ? "bolt" : "batt");
            boolean sz = ShizukuHelper.granted(); setChip(cSz, "Shizuku", !sz); chipIcon(cSz, sz ? "check" : "x");
            if (sz) ShizukuHelper.run(() -> { final float u = ShizukuHelper.cpuUsage(); h.post(() -> setChip(cCpu, u >= 0 ? Math.round(u) + "%" : "--", u > 90)); });
            else { float g = Booster.cpuGHz(); setChip(cCpu, g > 0 ? String.format(Locale.US, "%.1fG", g) : "--", false); }
            h.postDelayed(this, 2000);
        }
    };

    private void setChip(TextView t, String s, boolean warn) {
        t.setText(s); t.setTextColor(warn ? Color.rgb(255, 110, 100) : Color.WHITE);
        t.setBackground(UiUtil.round(warn ? Color.argb(60, 255, 80, 60) : Color.argb(50, 255, 255, 255), UiUtil.dp(t, 14), warn ? Color.rgb(255, 90, 70) : 0, warn ? 2 : 0));
    }
    /** نص + أيقونة خطية على يساره (بدل الإيموجي). */
    private static android.graphics.drawable.Drawable iconDrawable(Context c, String key, int color) {
        final int sz = (int) UiUtil.px(c, 14); final Bitmap b = Bitmap.createBitmap(sz * 2, sz * 2, Bitmap.Config.ARGB_8888); Canvas cv = new Canvas(b); Icons.draw(cv, key, sz, sz, sz * 2f, color, new Paint(Paint.ANTI_ALIAS_FLAG));
        android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(c.getResources(), b); d.setBounds(0, 0, sz, sz); return d;
    }
    private void chipIcon(TextView t, String key) { t.setCompoundDrawables(iconDrawable(this, key, Color.WHITE), null, null, null); t.setCompoundDrawablePadding(UiUtil.dp(t, 5)); }

    // ---------- الخلفية ----------
    static final int[][] BG_COLS = {{255, 140, 20}, {0, 220, 220}, {255, 50, 30}, {60, 120, 255}};
    static void drawBgStyle(Canvas c, Paint p, float w, float hh, int style, float t) { drawBgStyle(c, p, w, hh, style, t, null); }
    static void drawBgStyle(Canvas c, Paint p, float w, float hh, int style, float t, int[] over) {
        int[] k = over != null ? over : BG_COLS[Math.max(0, Math.min(3, style))];
        p.setShader(new LinearGradient(0, 0, 0, hh, Color.rgb(4, 5, 12), Color.rgb(14, 16, 36), Shader.TileMode.CLAMP)); p.setStyle(Paint.Style.FILL); c.drawRect(0, 0, w, hh, p); p.setShader(null);
        float cx = w * .5f, hz = hh * .62f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, w / 900f));
        for (int i = -14; i <= 14; i++) { p.setColor(Color.argb(40, k[0], k[1], k[2])); c.drawLine(cx + i * w * .024f, hz, cx + i * w * .13f, hh, p); }
        for (int i = 0; i < 12; i++) { float z = ((i / 12f + t * .06f) % 1f), y = hz + z * z * (hh - hz); p.setColor(Color.argb((int) (20 + 60 * z), k[0], k[1], k[2])); c.drawLine(0, y, w, y, p); }
        float pulse = .85f + .15f * (float) Math.sin(t * 2);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, hh * .45f, hh * .75f * pulse, Color.argb(style == 2 ? 90 : 70, k[0], k[1], k[2]), 0x00000000, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, hh, p); p.setShader(null);
        if (style == 2) { p.setStrokeWidth(3); p.setStyle(Paint.Style.STROKE); for (int i = 0; i < 16; i++) { double a = Math.PI * 2 * i / 16 + t * .2; p.setColor(Color.argb(40, 255, 80, 40)); c.drawLine(cx, hh * .45f, (float) (cx + Math.cos(a) * w), (float) (hh * .45f + Math.sin(a) * w), p); } }
    }
    static class BgView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final long t0 = SystemClock.uptimeMillis();
        Bitmap bmp, blur; int style; boolean anim; int[] accent; long changeT;
        BgView(Context c) {
            super(c);
            style = Booster.num(c, "bg", 0); anim = Booster.flag(c, "bgAnim", true);
            String u = Booster.prefs(c).getString("bgUri", null);
            if (u != null) try {
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 2;
                java.io.InputStream in = c.getContentResolver().openInputStream(android.net.Uri.parse(u)); bmp = BitmapFactory.decodeStream(in, null, o); if (in != null) in.close();
            } catch (Exception e) { bmp = null; }
        }
        /** تلوين الخلفية بألوان أيقونة اللعبة المختارة + أيقونة مموّهة مظلّلة خلفها. */
        void setGame(Bitmap icon, int color) {
            accent = new int[]{Color.red(color), Color.green(color), Color.blue(color)}; changeT = SystemClock.uptimeMillis();
            blur = icon == null ? null : Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(icon, 10, 10, true), 40, 40, true); invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), t = anim ? (SystemClock.uptimeMillis() - t0) / 1000f : 0;
            if (bmp != null) {
                float s = Math.max(w / bmp.getWidth(), hh / bmp.getHeight()); c.save(); c.translate((w - bmp.getWidth() * s) / 2, (hh - bmp.getHeight() * s) / 2); c.scale(s, s); c.drawBitmap(bmp, 0, 0, p); c.restore();
                p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(110, 0, 0, 0)); c.drawRect(0, 0, w, hh, p);
            } else {
                drawBgStyle(c, p, w, hh, style, t, accent);
                if (blur != null) { p.setFilterBitmap(true); p.setAlpha((int) (70 * Math.min(1f, (SystemClock.uptimeMillis() - changeT) / 500f))); float s = Math.max(w / blur.getWidth(), hh / blur.getHeight()) * 1.3f; c.save(); c.translate(w / 2, hh * .45f); c.scale(s, s); c.drawBitmap(blur, -blur.getWidth() / 2f, -blur.getHeight() / 2f, p); c.restore(); p.setAlpha(255); }
            }
            // تظليل: أعلى وأسفل داكنان + هالة سوداء على الأطراف
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, 0, hh, new int[]{Color.argb(190, 0, 0, 0), 0, 0, Color.argb(215, 0, 0, 0)}, new float[]{0, .28f, .62f, 1}, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, hh, p); p.setShader(null);
            p.setShader(new RadialGradient(w / 2, hh * .48f, Math.max(w, hh) * .62f, 0x00000000, Color.argb(170, 0, 0, 0), Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, hh, p); p.setShader(null);
            if (anim) postInvalidateOnAnimation();
        }
    }
    /** المسرح: شعار اللعبة كبير بتوهج وأشعة وانعكاس على الأرض وجسيمات. */
    static class StageView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final long t0 = SystemClock.uptimeMillis();
        Bitmap bmp; int accent = Color.rgb(255, 165, 31); long changeT = -10000;
        StageView(Context c) { super(c); }
        void set(Bitmap b, int color) { bmp = b; accent = color; changeT = SystemClock.uptimeMillis(); invalidate(); }
        private static float eob(float x) { float c1 = 1.70158f, c3 = c1 + 1, k = x - 1; return 1 + c3 * k * k * k + c1 * k * k; }
        private int a(int alpha) { return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(accent), Color.green(accent), Color.blue(accent)); }
        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis(); float t = (now - t0) / 1000f, w = getWidth(), h = getHeight(), cx = w / 2, size = Math.min(w * .78f, h * .62f), cy = h * .44f;
            float pop = eob(Math.min(1f, (now - changeT) / 520f)), fade = Math.min(1f, (now - changeT) / 350f);
            // أشعة دوّارة
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 7; i++) {
                double ang = t * .18 + i * Math.PI * 2 / 7; float rx = (float) Math.cos(ang), ry = (float) Math.sin(ang), L = Math.max(w, h);
                p.setShader(new LinearGradient(cx, cy, cx + rx * L, cy + ry * L, a(46), a(0), Shader.TileMode.CLAMP));
                path.reset(); path.moveTo(cx, cy); path.lineTo(cx + (rx - ry * .09f) * L, cy + (ry + rx * .09f) * L); path.lineTo(cx + (rx + ry * .09f) * L, cy + (ry - rx * .09f) * L); path.close(); c.drawPath(path, p);
            }
            p.setShader(null);
            // توهج خلف الشعار + بقعة على الأرض
            p.setShader(new RadialGradient(cx, cy, size * .95f, a((int) (125 * fade)), a(0), Shader.TileMode.CLAMP)); c.drawCircle(cx, cy, size * .95f, p); p.setShader(null);
            float fy = cy + size * .58f; rf.set(cx - size * .75f, fy - size * .09f, cx + size * .75f, fy + size * .09f);
            p.setShader(new RadialGradient(cx, fy, size * .75f, a(150), a(0), Shader.TileMode.CLAMP)); c.save(); c.scale(1f, .24f, cx, fy); c.drawCircle(cx, fy, size * .75f, p); c.restore(); p.setShader(null);
            // حلقات
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 3; i++) { float k = (t * .45f + i / 3f) % 1f; p.setStrokeWidth(3); p.setColor(a((int) (170 * (1 - k)))); c.drawCircle(cx, cy, size * (.56f + k * .55f), p); }
            p.setStrokeWidth(4.5f); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(a(230)); rf.set(cx - size * .6f, cy - size * .6f, cx + size * .6f, cy + size * .6f);
            c.drawArc(rf, t * 80, 64, false, p); c.drawArc(rf, t * 80 + 180, 64, false, p); p.setStrokeWidth(2); p.setColor(a(120)); c.drawArc(rf, -t * 55 + 60, 100, false, p); p.setStrokeCap(Paint.Cap.BUTT);
            if (bmp != null) {
                float bob = (float) Math.sin(t * 2.2) * size * .025f, s = size * pop, half = s / 2;
                // الانعكاس على الأرض
                int lay = c.saveLayer(cx - half, cy + half + bob + 6, cx + half, cy + half + bob + 6 + half * .9f, null);
                c.save(); c.translate(cx, cy + half + bob + 6); c.scale(1, -1); rf.set(-half, 0, half, s); p.setStyle(Paint.Style.FILL); p.setAlpha(90); c.drawBitmap(bmp, null, rf, p); p.setAlpha(255); c.restore();
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN)); p.setShader(new LinearGradient(0, cy + half + bob + 6, 0, cy + half + bob + 6 + half * .9f, Color.argb(160, 0, 0, 0), 0x00000000, Shader.TileMode.CLAMP));
                c.drawRect(cx - half, cy + half + bob + 6, cx + half, cy + half + bob + 6 + half * .9f, p); p.setShader(null); p.setXfermode(null); c.restoreToCount(lay);
                // الشعار نفسه مع إطار ولمعة تعبر
                c.save(); c.translate(0, bob); rf.set(cx - half, cy - half, cx + half, cy + half); path.reset(); path.addRoundRect(rf, s * .22f, s * .22f, Path.Direction.CW);
                p.setStyle(Paint.Style.FILL); p.setColor(a(90)); rf.inset(-s * .04f, -s * .04f); c.drawRoundRect(rf, s * .26f, s * .26f, p); rf.set(cx - half, cy - half, cx + half, cy + half);
                c.save(); c.clipPath(path); p.setAlpha((int) (255 * fade)); c.drawBitmap(bmp, null, rf, p); p.setAlpha(255);
                float sx = cx - half + ((t * .5f) % 2f - .5f) * s * 1.6f; p.setShader(new LinearGradient(sx - s * .12f, 0, sx + s * .12f, 0, new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                c.save(); c.skew(-.3f, 0); c.drawRect(sx - s * .12f, cy - half, sx + s * .12f, cy + half, p); c.restore(); p.setShader(null); c.restore();
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.argb(210, 255, 255, 255)); c.drawPath(path, p); c.restore();
            }
            // جسيمات تصعد
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 26; i++) {
                float k = (t * (.12f + (i % 5) * .03f) + i * .137f) % 1f, x = cx + (float) Math.sin(i * 12.9898) * size * .75f + (float) Math.sin(t + i) * 8, y = cy + size * .62f - k * size * 1.5f;
                p.setColor(a((int) (200 * (1 - k)))); c.drawCircle(x, y, 1.5f + (i % 3), p);
            }
            postInvalidateOnAnimation();
        }
        private final Path path = new Path(); private final RectF rf = new RectF();
    }

    /** يحوّل أيقونة التطبيق إلى صورة، ويستخرج لوناً مميزاً منها. */
    static Bitmap toBitmap(android.graphics.drawable.Drawable d) {
        Bitmap b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888); Canvas cv = new Canvas(b); d.setBounds(0, 0, 192, 192); d.draw(cv); return b;
    }
    static int accentOf(Bitmap b) {
        Bitmap s = Bitmap.createScaledBitmap(b, 16, 16, true); float[] hsv = new float[3]; double r = 0, g = 0, bl = 0, ws = 0;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) { int px = s.getPixel(x, y); Color.colorToHSV(px, hsv); double wgt = hsv[1] * hsv[2] * (Color.alpha(px) / 255.0); r += Color.red(px) * wgt; g += Color.green(px) * wgt; bl += Color.blue(px) * wgt; ws += wgt; }
        if (ws < .5) return Color.rgb(255, 165, 31);
        Color.colorToHSV(Color.rgb((int) (r / ws), (int) (g / ws), (int) (bl / ws)), hsv); hsv[1] = Math.max(.6f, hsv[1]); hsv[2] = Math.max(.9f, hsv[2]); return Color.HSVToColor(hsv);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.immersive(this);
        FrameLayout root = new FrameLayout(this);
        bgView = new BgView(this); root.addView(bgView, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtil.dp(root, 20); col.setPadding(pad, UiUtil.dp(root, 10), pad, UiUtil.dp(root, 8));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // ===== الشريط العلوي: عنوان + ساعة + شرائح حالة حيّة =====
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        SpannableString title = new SpannableString("مسرّع الألعاب"); title.setSpan(new ForegroundColorSpan(UiUtil.ACC), 5, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView tt = UiUtil.text(this, "", 22, Color.WHITE, true); tt.setText(title); top.addView(tt);
        clock = UiUtil.text(this, "", 16, Color.WHITE, true); clock.setPadding(UiUtil.dp(root, 22), 0, UiUtil.dp(root, 14), 0); top.addView(clock);
        cRam = chip(); cCpu = chip(); cTemp = chip(); cBat = chip(); cSz = chip(); chipIcon(cRam, "ram"); chipIcon(cCpu, "cpu"); chipIcon(cTemp, "therm");
        LinearLayout chipRow = new LinearLayout(this); chipRow.setGravity(Gravity.CENTER_VERTICAL);
        for (TextView c : new TextView[]{cRam, cCpu, cTemp, cBat, cSz}) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, UiUtil.dp(c, 30)); lp.setMargins(UiUtil.dp(c, 4), 0, UiUtil.dp(c, 4), 0); chipRow.addView(c, lp); }
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); hs.setOverScrollMode(View.OVER_SCROLL_NEVER); hs.addView(chipRow);
        top.addView(hs, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(circle("x", v -> finishAffinity())); top.addView(circle("wrench", v -> startActivity(new Intent(this, HubActivity.class)))); top.addView(circle("gear", v -> startActivity(new Intent(this, SettingsActivity.class))));
        col.addView(top, new LinearLayout.LayoutParams(-1, UiUtil.dp(root, 50)));

        // ===== الوسط =====
        LinearLayout mid = new LinearLayout(this); mid.setGravity(Gravity.CENTER_VERTICAL);
        ScrollView sv = new ScrollView(this); sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); sv.addView(list);
        mid.addView(sv, new LinearLayout.LayoutParams(0, -1, 3f));
        FrameLayout center = new FrameLayout(this);
        stage = new StageView(this); center.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        empty = UiUtil.text(this, "لا توجد ألعاب بعد\nاضغط + لإضافة لعبة", 16, Color.rgb(190, 200, 230), false); empty.setGravity(Gravity.CENTER); center.addView(empty, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        mid.addView(center, new LinearLayout.LayoutParams(0, -1, 3.4f));
        LinearLayout right = new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL); right.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        name = UiUtil.text(this, "", 26, Color.WHITE, true); name.setGravity(Gravity.END);
        info = UiUtil.text(this, "", 12, Color.rgb(170, 178, 205), false); info.setGravity(Gravity.END); info.setPadding(0, 4, 0, 10);
        right.addView(name); right.addView(info);
        chips = new LinearLayout(this); chips.setGravity(Gravity.END); right.addView(chips);
        LinearLayout btns = new LinearLayout(this); btns.setGravity(Gravity.CENTER_VERTICAL); btns.setPadding(0, UiUtil.dp(root, 12), 0, 0);
        TextView arrow = UiUtil.text(this, "›", 30, Color.WHITE, true); arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(UiUtil.round(Color.argb(60, 255, 165, 31), UiUtil.dp(root, 26), UiUtil.ACC, 3)); arrow.setOnClickListener(v -> startActivity(new Intent(this, HubActivity.class))); UiUtil.pressable(arrow);
        play = UiUtil.text(this, "بدء اللعب  ▶", 20, Color.rgb(42, 18, 0), true); play.setGravity(Gravity.CENTER);
        play.setBackground(UiUtil.round(UiUtil.ACC, UiUtil.dp(root, 14), Color.WHITE, 2)); play.setOnClickListener(v -> startGame()); UiUtil.pressable(play);
        ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f); pulse.setDuration(1100); pulse.setRepeatMode(ValueAnimator.REVERSE); pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a -> { float k = (float) a.getAnimatedValue(); play.setElevation(UiUtil.dp(play, 2) + k * UiUtil.dp(play, 10)); }); pulse.start();
        btns.addView(arrow, new LinearLayout.LayoutParams(UiUtil.dp(root, 52), UiUtil.dp(root, 52)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(UiUtil.dp(root, 190), UiUtil.dp(root, 52)); plp.setMargins(UiUtil.dp(root, 10), 0, 0, 0); btns.addView(play, plp);
        right.addView(btns);
        mid.addView(right, new LinearLayout.LayoutParams(0, -1, 3.6f));
        col.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        // ===== الشريط السفلي =====
        FrameLayout bottom = new FrameLayout(this);
        View plus = UiUtil.icon(this, "plus", Color.WHITE); plus.setPadding(UiUtil.dp(plus, 12), UiUtil.dp(plus, 8), UiUtil.dp(plus, 12), UiUtil.dp(plus, 8)); plus.setBackground(UiUtil.round(UiUtil.CARD, UiUtil.dp(root, 10), 0, 0));
        plus.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class))); UiUtil.pressable(plus);
        bottom.addView(plus, new FrameLayout.LayoutParams(UiUtil.dp(root, 54), UiUtil.dp(root, 44), Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout tabs = new LinearLayout(this);
        TextView home = tab("الرئيسية (اللوبي)", true), tools = tab("الأدوات والتسريع", false);
        tools.setOnClickListener(v -> startActivity(new Intent(this, HubActivity.class))); UiUtil.pressable(tools);
        tabs.addView(home, new LinearLayout.LayoutParams(UiUtil.dp(root, 200), UiUtil.dp(root, 44))); tabs.addView(tools, new LinearLayout.LayoutParams(UiUtil.dp(root, 200), UiUtil.dp(root, 44)));
        bottom.addView(tabs, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        col.addView(bottom, new LinearLayout.LayoutParams(-1, UiUtil.dp(root, 48)));
        setContentView(root);
    }

    private TextView chip() { TextView t = UiUtil.text(this, "", 12, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setPadding(UiUtil.dp(t, 10), 0, UiUtil.dp(t, 10), 0); return t; }
    private View circle(String key, View.OnClickListener l) {
        View t = UiUtil.icon(this, key, Color.WHITE); t.setPadding(UiUtil.dp(t, 10), UiUtil.dp(t, 10), UiUtil.dp(t, 10), UiUtil.dp(t, 10)); t.setBackground(UiUtil.round(Color.argb(160, 30, 32, 48), UiUtil.dp(t, 22), 0, 0)); t.setOnClickListener(l); UiUtil.pressable(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(t, 44), UiUtil.dp(t, 44)); lp.setMargins(UiUtil.dp(t, 8), 0, 0, 0); t.setLayoutParams(lp); return t;
    }
    private TextView tab(String s, boolean active) {
        TextView t = UiUtil.text(this, s, 15, Color.WHITE, true); t.setGravity(Gravity.CENTER);
        t.setBackground(UiUtil.round(active ? Color.rgb(150, 40, 20) : Color.argb(120, 40, 44, 70), UiUtil.dp(t, 8), active ? UiUtil.ACC : 0, active ? 2 : 0)); return t;
    }

    @Override protected void onResume() { super.onResume(); UiUtil.immersive(this); Booster.endSessionIfActive(this); reload(); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }

    private void reload() {
        games = Booster.games(this);
        if (sel >= games.size()) sel = 0;
        list.removeAllViews();
        PackageManager pm = getPackageManager();
        for (int i = 0; i < games.size(); i++) {
            final int idx = i; final ResolveInfo r = games.get(i);
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(UiUtil.dp(row, 10), UiUtil.dp(row, 8), UiUtil.dp(row, 10), UiUtil.dp(row, 8));
            ImageView ic = new ImageView(this); ic.setImageDrawable(r.loadIcon(pm)); ic.setOutlineProvider(ViewOutlineProvider.BACKGROUND); ic.setClipToOutline(true);
            ic.setBackground(UiUtil.round(Color.TRANSPARENT, UiUtil.dp(row, 30), 0, 0));
            row.addView(ic, new LinearLayout.LayoutParams(UiUtil.dp(row, 54), UiUtil.dp(row, 54)));
            LinearLayout tx = new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL); tx.setPadding(UiUtil.dp(row, 12), 0, 0, 0);
            TextView n = UiUtil.text(this, r.loadLabel(pm).toString(), 16, Color.WHITE, true); n.setSingleLine(true); n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tx.addView(n); TextView ver = UiUtil.text(this, "الإصدار " + versionOf(r.activityInfo.packageName), 11, Color.rgb(170, 178, 205), false); ver.setSingleLine(true); ver.setEllipsize(android.text.TextUtils.TruncateAt.END); tx.addView(ver);
            row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(v -> { if (sel != idx) { sel = idx; reload(); pop(); } });
            row.setOnLongClickListener(v -> { Booster.setGame(this, r.activityInfo.packageName, false); toast("أُزيلت من الألعاب"); reload(); return true; });
            UiUtil.pressable(row);
            if (i == sel) row.setBackground(UiUtil.round(Color.argb(70, 255, 165, 31), UiUtil.dp(row, 12), UiUtil.ACC, 2));
            list.addView(row);
            View line = new View(this); line.setBackgroundColor(Color.argb(90, 255, 90, 60)); list.addView(line, new LinearLayout.LayoutParams(-1, 2));
            if (first) { row.setAlpha(0f); row.setTranslationX(-UiUtil.dp(row, 40)); row.animate().alpha(1f).translationX(0).setStartDelay(80L * i).setDuration(380).setInterpolator(new DecelerateInterpolator()).start(); }
        }
        first = false;
        boolean has = !games.isEmpty();
        empty.setVisibility(has ? View.GONE : View.VISIBLE); stage.setVisibility(has ? View.VISIBLE : View.GONE); play.setAlpha(has ? 1f : .6f);
        chips.removeAllViews();
        if (has) {
            ResolveInfo r = games.get(sel); String pkg = r.activityInfo.packageName;
            name.setText(r.loadLabel(pm)); if (!pkg.equals(shownPkg)) { shownPkg = pkg; Bitmap ib = toBitmap(r.loadIcon(pm)); int ac = accentOf(ib); stage.set(ib, ac); bgView.setGame(ib, ac); }
            boolean sz = ShizukuHelper.granted();
            info.setText(sz ? "Shizuku فعّال — التسريع الكامل متاح" : "Shizuku غير مفعّل — تسريع محدود");
            chips.addView(optChip("أداء", "rocket", pkg, "perf", Booster.flag(this, "gameModePerf", true), sz));
            chips.addView(optChip("كتم", "moon", pkg, "dnd", Booster.flag(this, "dndOnStart", true), true));
            chips.addView(optChip("تنظيف", "broom", pkg, "clean", Booster.flag(this, "cleanBefore", true), true));
        } else { name.setText("مسرّع الألعاب"); info.setText("أضف لعبة من زر +"); }
    }

    /** شريحة خيار لكل لعبة: تبديل بحركة ولون */
    private TextView optChip(String label, final String iconKey, final String pkg, final String key, boolean def, final boolean enabled) {
        final TextView t = UiUtil.text(this, "", 13, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setPadding(UiUtil.dp(t, 12), 0, UiUtil.dp(t, 12), 0);
        final boolean[] on = {Booster.gameFlag(this, pkg, key, def) && enabled};
        final Runnable paint = () -> { t.setText(label); t.setCompoundDrawables(iconDrawable(this, on[0] ? "check" : iconKey, Color.WHITE), null, null, null); t.setCompoundDrawablePadding(UiUtil.dp(t, 5)); t.setTextColor(enabled ? Color.WHITE : Color.rgb(120, 126, 150)); t.setBackground(UiUtil.round(on[0] ? Color.argb(80, 255, 165, 31) : Color.argb(40, 255, 255, 255), UiUtil.dp(t, 14), on[0] ? UiUtil.ACC : 0, on[0] ? 2 : 0)); };
        paint.run();
        t.setOnClickListener(v -> {
            if (!enabled) { toast("يحتاج تفعيل Shizuku"); return; }
            on[0] = !on[0]; Booster.setGameFlag(this, pkg, key, on[0]); paint.run(); t.setScaleX(.8f); t.setScaleY(.8f); t.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
        });
        UiUtil.pressable(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, UiUtil.dp(t, 32)); lp.setMargins(UiUtil.dp(t, 6), 0, 0, 0); t.setLayoutParams(lp);
        return t;
    }

    private void pop() { /* الحركة تتم داخل StageView عند تغيّر اللعبة */ }
    private String versionOf(String pkg) { try { String v = getPackageManager().getPackageInfo(pkg, 0).versionName; return v == null ? "-" : v; } catch (Exception e) { return "-"; } }

    private void startGame() {
        if (games.isEmpty()) { startActivity(new Intent(this, AppsActivity.class)); return; }
        final String pkg = games.get(sel).activityInfo.packageName;
        final String res = Booster.startSession(this, pkg);
        final Runnable go = () -> {
            Toast.makeText(this, res, Toast.LENGTH_LONG).show();
            if (Booster.flag(this, "autoPanel", true) && Booster.hasOverlay(this) && !Booster.flag(this, "disableBubble", false) && !OverlayService.running) {
                Intent si = new Intent(this, OverlayService.class); if (Build.VERSION.SDK_INT >= 26) startForegroundService(si); else startService(si);
            }
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) startActivity(i);
        };
        if (!Booster.flag(this, "launchAnim", true)) { go.run(); return; }
        final View fx = new View(this) {
            final Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG); final long t0 = SystemClock.uptimeMillis();
            @Override protected void onDraw(Canvas c) {
                float k = Math.min(1f, (SystemClock.uptimeMillis() - t0) / 650f), w = getWidth(), hh = getHeight(), cx = w / 2, cy = hh / 2;
                pp.setStyle(Paint.Style.FILL); pp.setColor(Color.argb((int) (230 * k), 0, 0, 0)); c.drawRect(0, 0, w, hh, pp);
                pp.setStyle(Paint.Style.STROKE); pp.setStrokeWidth(8);
                for (int i = 0; i < 3; i++) { float r = (k * 1.2f - i * .15f); if (r <= 0) continue; pp.setColor(Color.argb((int) (255 * (1 - Math.min(1, r))), 255, 165, 31)); c.drawCircle(cx, cy, r * Math.max(w, hh) * .6f, pp); }
                Path b = new Path(); float s = hh * .0045f * (.5f + k); b.moveTo(cx + 14 * s, cy - 96 * s); b.lineTo(cx - 52 * s, cy + 10 * s); b.lineTo(cx - 6 * s, cy + 10 * s); b.lineTo(cx - 24 * s, cy + 96 * s); b.lineTo(cx + 52 * s, cy - 18 * s); b.lineTo(cx + 6 * s, cy - 18 * s); b.close();
                pp.setStyle(Paint.Style.FILL); pp.setColor(Color.argb((int) (255 * k), 255, 177, 0)); c.drawPath(b, pp);
                if (k < 1f) postInvalidateOnAnimation();
            }
        };
        ((ViewGroup) getWindow().getDecorView()).addView(fx, new ViewGroup.LayoutParams(-1, -1));
        fx.postDelayed(() -> { go.run(); fx.postDelayed(() -> ((ViewGroup) getWindow().getDecorView()).removeView(fx), 600); }, 700);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}

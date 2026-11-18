package com.gamebooster.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
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
    private ImageView big;
    private TextView name, info, clock, cRam, cCpu, cTemp, cBat, cSz, empty, play;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean first = true;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            clock.setText(new SimpleDateFormat("hh:mm", Locale.getDefault()).format(new Date()));
            long tot = Booster.totalMem(LauncherActivity.this), free = Booster.freeMem(LauncherActivity.this); float used = 100f * (1 - (float) free / tot), temp = Booster.batteryTemp(LauncherActivity.this);
            setChip(cRam, "🧠 " + Math.round(used) + "%", used > 85); setChip(cTemp, "🌡 " + Math.round(temp) + "°", temp >= 42);
            setChip(cBat, (Booster.isCharging(LauncherActivity.this) ? "⚡ " : "🔋 ") + Booster.batteryPct(LauncherActivity.this) + "%", false);
            boolean sz = ShizukuHelper.granted(); setChip(cSz, sz ? "● Shizuku" : "○ Shizuku", !sz);
            if (sz) ShizukuHelper.run(() -> { final float u = ShizukuHelper.cpuUsage(); h.post(() -> setChip(cCpu, u >= 0 ? "⚙ " + Math.round(u) + "%" : "⚙ —", u > 90)); });
            else { float g = Booster.cpuGHz(); setChip(cCpu, g > 0 ? "⚙ " + String.format(Locale.US, "%.1fG", g) : "⚙ —", false); }
            h.postDelayed(this, 2000);
        }
    };

    private void setChip(TextView t, String s, boolean warn) {
        t.setText(s); t.setTextColor(warn ? Color.rgb(255, 110, 100) : Color.WHITE);
        t.setBackground(UiUtil.round(warn ? Color.argb(60, 255, 80, 60) : Color.argb(50, 255, 255, 255), UiUtil.dp(t, 14), warn ? Color.rgb(255, 90, 70) : 0, warn ? 2 : 0));
    }

    // ---------- الخلفية ----------
    static final int[][] BG_COLS = {{255, 140, 20}, {0, 220, 220}, {255, 50, 30}, {60, 120, 255}};
    static void drawBgStyle(Canvas c, Paint p, float w, float hh, int style, float t) {
        int[] k = BG_COLS[Math.max(0, Math.min(3, style))];
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
        Bitmap bmp; int style; boolean anim;
        BgView(Context c) {
            super(c);
            style = Booster.num(c, "bg", 0); anim = Booster.flag(c, "bgAnim", true);
            String u = Booster.prefs(c).getString("bgUri", null);
            if (u != null) try {
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 2;
                java.io.InputStream in = c.getContentResolver().openInputStream(android.net.Uri.parse(u)); bmp = BitmapFactory.decodeStream(in, null, o); if (in != null) in.close();
            } catch (Exception e) { bmp = null; }
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), t = anim ? (SystemClock.uptimeMillis() - t0) / 1000f : 0;
            if (bmp != null) {
                float s = Math.max(w / bmp.getWidth(), hh / bmp.getHeight()); c.save(); c.translate((w - bmp.getWidth() * s) / 2, (hh - bmp.getHeight() * s) / 2); c.scale(s, s); c.drawBitmap(bmp, 0, 0, p); c.restore();
                p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(110, 0, 0, 0)); c.drawRect(0, 0, w, hh, p);
            } else drawBgStyle(c, p, w, hh, style, t);
            if (anim) postInvalidateOnAnimation();
        }
    }
    static class RingView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final long t0 = SystemClock.uptimeMillis();
        RingView(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float t = (SystemClock.uptimeMillis() - t0) / 1000f, cx = getWidth() / 2f, cy = getHeight() / 2f, r0 = Math.min(cx, cy);
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 3; i++) { float k = (t * .5f + i / 3f) % 1f; p.setStrokeWidth(3); p.setColor(Color.argb((int) (150 * (1 - k)), 255, 165, 31)); c.drawCircle(cx, cy, r0 * (.55f + k * .45f), p); }
            p.setStrokeWidth(4); p.setColor(Color.argb(200, 255, 165, 31)); RectF rf = new RectF(cx - r0 * .55f, cy - r0 * .55f, cx + r0 * .55f, cy + r0 * .55f);
            c.drawArc(rf, t * 90, 70, false, p); c.drawArc(rf, t * 90 + 180, 70, false, p);
            postInvalidateOnAnimation();
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.immersive(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(new BgView(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtil.dp(root, 20); col.setPadding(pad, UiUtil.dp(root, 10), pad, UiUtil.dp(root, 8));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // ===== الشريط العلوي: عنوان + ساعة + شرائح حالة حيّة =====
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        SpannableString title = new SpannableString("مسرّع الألعاب"); title.setSpan(new ForegroundColorSpan(UiUtil.ACC), 5, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView tt = UiUtil.text(this, "", 22, Color.WHITE, true); tt.setText(title); top.addView(tt);
        clock = UiUtil.text(this, "", 16, Color.WHITE, true); clock.setPadding(UiUtil.dp(root, 22), 0, UiUtil.dp(root, 14), 0); top.addView(clock);
        cRam = chip(); cCpu = chip(); cTemp = chip(); cBat = chip(); cSz = chip();
        for (TextView c : new TextView[]{cRam, cCpu, cTemp, cBat, cSz}) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, UiUtil.dp(c, 30)); lp.setMargins(UiUtil.dp(c, 4), 0, UiUtil.dp(c, 4), 0); top.addView(c, lp); }
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        top.addView(circle("✖", v -> finishAffinity())); top.addView(circle("🛠", v -> startActivity(new Intent(this, HubActivity.class)))); top.addView(circle("⚙", v -> startActivity(new Intent(this, SettingsActivity.class))));
        col.addView(top, new LinearLayout.LayoutParams(-1, UiUtil.dp(root, 50)));

        // ===== الوسط =====
        LinearLayout mid = new LinearLayout(this); mid.setGravity(Gravity.CENTER_VERTICAL);
        ScrollView sv = new ScrollView(this); sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); sv.addView(list);
        mid.addView(sv, new LinearLayout.LayoutParams(0, -1, 3f));
        FrameLayout center = new FrameLayout(this);
        center.addView(new RingView(this), new FrameLayout.LayoutParams(-1, -1));
        big = new ImageView(this); big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int bs = UiUtil.dp(root, 130); center.addView(big, new FrameLayout.LayoutParams(bs, bs, Gravity.CENTER));
        ObjectAnimator bob = ObjectAnimator.ofFloat(big, "translationY", -UiUtil.dp(root, 6), UiUtil.dp(root, 6)); bob.setDuration(1800); bob.setRepeatMode(ValueAnimator.REVERSE); bob.setRepeatCount(ValueAnimator.INFINITE); bob.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()); bob.start();
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
        TextView plus = UiUtil.text(this, "+", 28, Color.WHITE, true); plus.setGravity(Gravity.CENTER); plus.setBackground(UiUtil.round(UiUtil.CARD, UiUtil.dp(root, 10), 0, 0));
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
    private TextView circle(String s, View.OnClickListener l) {
        TextView t = UiUtil.text(this, s, 18, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setBackground(UiUtil.round(Color.argb(160, 30, 32, 48), UiUtil.dp(t, 22), 0, 0)); t.setOnClickListener(l); UiUtil.pressable(t);
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
            tx.addView(n); tx.addView(UiUtil.text(this, "الإصدار " + versionOf(r.activityInfo.packageName), 11, Color.rgb(170, 178, 205), false));
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
        empty.setVisibility(has ? View.GONE : View.VISIBLE); big.setVisibility(has ? View.VISIBLE : View.GONE); play.setAlpha(has ? 1f : .6f);
        chips.removeAllViews();
        if (has) {
            ResolveInfo r = games.get(sel); String pkg = r.activityInfo.packageName;
            big.setImageDrawable(r.loadIcon(pm)); name.setText(r.loadLabel(pm));
            boolean sz = ShizukuHelper.granted();
            info.setText(sz ? "Shizuku فعّال — التسريع الكامل متاح" : "Shizuku غير مفعّل — تسريع محدود");
            chips.addView(optChip("🚀 أداء", pkg, "perf", Booster.flag(this, "gameModePerf", true), sz));
            chips.addView(optChip("🔕 كتم", pkg, "dnd", Booster.flag(this, "dndOnStart", true), true));
            chips.addView(optChip("🧹 تنظيف", pkg, "clean", Booster.flag(this, "cleanBefore", true), true));
        } else { name.setText("مسرّع الألعاب"); info.setText("أضف لعبة من زر +"); }
    }

    /** شريحة خيار لكل لعبة: تبديل بحركة ولون */
    private TextView optChip(String label, final String pkg, final String key, boolean def, final boolean enabled) {
        final TextView t = UiUtil.text(this, "", 13, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setPadding(UiUtil.dp(t, 12), 0, UiUtil.dp(t, 12), 0);
        final boolean[] on = {Booster.gameFlag(this, pkg, key, def) && enabled};
        final Runnable paint = () -> { t.setText(label + (on[0] ? "  ✔" : "")); t.setTextColor(enabled ? Color.WHITE : Color.rgb(120, 126, 150)); t.setBackground(UiUtil.round(on[0] ? Color.argb(80, 255, 165, 31) : Color.argb(40, 255, 255, 255), UiUtil.dp(t, 14), on[0] ? UiUtil.ACC : 0, on[0] ? 2 : 0)); };
        paint.run();
        t.setOnClickListener(v -> {
            if (!enabled) { toast("يحتاج تفعيل Shizuku"); return; }
            on[0] = !on[0]; Booster.setGameFlag(this, pkg, key, on[0]); paint.run(); t.setScaleX(.8f); t.setScaleY(.8f); t.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
        });
        UiUtil.pressable(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, UiUtil.dp(t, 32)); lp.setMargins(UiUtil.dp(t, 6), 0, 0, 0); t.setLayoutParams(lp);
        return t;
    }

    private void pop() { ObjectAnimator a = ObjectAnimator.ofFloat(big, "scaleX", .75f, 1f); a.setDuration(300).setInterpolator(new OvershootInterpolator(2f)); a.start(); ObjectAnimator b = ObjectAnimator.ofFloat(big, "scaleY", .75f, 1f); b.setDuration(300).setInterpolator(new OvershootInterpolator(2f)); b.start(); }
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

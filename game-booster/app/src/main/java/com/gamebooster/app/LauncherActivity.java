package com.gamebooster.app;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
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
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;

/** القائمة الرئيسية (مشغّل الألعاب) — تظهر بعد منح الصلاحيات. */
public class LauncherActivity extends Activity {
    private List<ResolveInfo> games = new ArrayList<>();
    private int sel = 0;
    private LinearLayout list;
    private ImageView big;
    private TextView name, info, clock, battery, empty;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            clock.setText(new SimpleDateFormat("hh:mm", Locale.getDefault()).format(new Date()));
            String chg = Booster.isCharging(LauncherActivity.this) ? "⚡ " : "";
            battery.setText(chg + Booster.batteryPct(LauncherActivity.this) + "%   🌡 " + Math.round(Booster.batteryTemp(LauncherActivity.this)) + "°");
            h.postDelayed(this, 20000);
        }
    };

    // ---------- الخلفية ----------
    static final int[][] BG_COLS = {{255, 140, 20}, {0, 220, 220}, {255, 50, 30}, {60, 120, 255}};
    /** يرسم أحد أنماط الخلفية الأربعة (t = الزمن للتحريك). */
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

    // ---------- حلقات حول صورة اللعبة ----------
    static class RingView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final long t0 = SystemClock.uptimeMillis();
        RingView(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float t = (SystemClock.uptimeMillis() - t0) / 1000f, cx = getWidth() / 2f, cy = getHeight() / 2f, r0 = Math.min(cx, cy);
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 3; i++) { float k = (t * .5f + i / 3f) % 1f; p.setStrokeWidth(3); p.setColor(Color.argb((int) (150 * (1 - k)), 255, 165, 31)); c.drawCircle(cx, cy, r0 * (.55f + k * .45f), p); }
            p.setStrokeWidth(4); p.setColor(Color.argb(200, 255, 165, 31)); c.drawArc(cx - r0 * .55f, cy - r0 * .55f, cx + r0 * .55f, cy + r0 * .55f, t * 90, 70, false, p);
            c.drawArc(cx - r0 * .55f, cy - r0 * .55f, cx + r0 * .55f, cy + r0 * .55f, t * 90 + 180, 70, false, p);
            postInvalidateOnAnimation();
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.immersive(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(new BgView(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtil.dp(root, 20);
        col.setPadding(pad, UiUtil.dp(root, 10), pad, UiUtil.dp(root, 8));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // ===== الشريط العلوي =====
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        SpannableString title = new SpannableString("مسرّع الألعاب");
        title.setSpan(new ForegroundColorSpan(UiUtil.ACC), 5, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView tt = UiUtil.text(this, "", 22, Color.WHITE, true); tt.setText(title);
        top.addView(tt);
        clock = UiUtil.text(this, "", 16, Color.WHITE, true); clock.setPadding(UiUtil.dp(root, 28), 0, UiUtil.dp(root, 20), 0);
        battery = UiUtil.text(this, "", 16, Color.WHITE, true);
        top.addView(clock); top.addView(battery);
        View sp = new View(this); top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
        top.addView(circle("✖", v -> finishAffinity()));
        top.addView(circle("🛠", v -> startActivity(new Intent(this, HubActivity.class))));
        top.addView(circle("⚙", v -> startActivity(new Intent(this, SettingsActivity.class))));
        col.addView(top, new LinearLayout.LayoutParams(-1, UiUtil.dp(root, 50)));

        // ===== الوسط =====
        LinearLayout mid = new LinearLayout(this);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        // يسار: قائمة الألعاب
        ScrollView sv = new ScrollView(this); sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        mid.addView(sv, new LinearLayout.LayoutParams(0, -1, 3f));
        // وسط: صورة اللعبة
        FrameLayout center = new FrameLayout(this);
        center.addView(new RingView(this), new FrameLayout.LayoutParams(-1, -1));
        big = new ImageView(this); big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int bs = UiUtil.dp(root, 130);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(bs, bs, Gravity.CENTER);
        center.addView(big, blp);
        empty = UiUtil.text(this, "لا توجد ألعاب بعد\nاضغط + لإضافة لعبة", 16, Color.rgb(190, 200, 230), false); empty.setGravity(Gravity.CENTER);
        center.addView(empty, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        mid.addView(center, new LinearLayout.LayoutParams(0, -1, 3.6f));
        // يمين: الاسم + بدء اللعب
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL); right.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        name = UiUtil.text(this, "", 26, Color.WHITE, true); name.setGravity(Gravity.END);
        info = UiUtil.text(this, "", 12, Color.rgb(170, 178, 205), false); info.setGravity(Gravity.END); info.setPadding(0, 6, 0, 14);
        right.addView(name); right.addView(info);
        LinearLayout btns = new LinearLayout(this); btns.setGravity(Gravity.CENTER_VERTICAL);
        TextView arrow = UiUtil.text(this, "›", 30, Color.WHITE, true); arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(UiUtil.round(Color.argb(60, 255, 165, 31), UiUtil.dp(root, 26), UiUtil.ACC, 3));
        arrow.setOnClickListener(v -> startActivity(new Intent(this, HubActivity.class)));
        TextView play = UiUtil.text(this, "بدء اللعب  ▶", 20, Color.rgb(42, 18, 0), true); play.setGravity(Gravity.CENTER);
        play.setBackground(UiUtil.round(UiUtil.ACC, UiUtil.dp(root, 14), Color.WHITE, 2));
        play.setOnClickListener(v -> startGame());
        btns.addView(arrow, new LinearLayout.LayoutParams(UiUtil.dp(root, 52), UiUtil.dp(root, 52)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(UiUtil.dp(root, 190), UiUtil.dp(root, 52)); plp.setMargins(UiUtil.dp(root, 10), 0, 0, 0);
        btns.addView(play, plp);
        right.addView(btns);
        mid.addView(right, new LinearLayout.LayoutParams(0, -1, 3.4f));
        col.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        // ===== الشريط السفلي =====
        FrameLayout bottom = new FrameLayout(this);
        TextView plus = UiUtil.text(this, "+", 28, Color.WHITE, true); plus.setGravity(Gravity.CENTER);
        plus.setBackground(UiUtil.round(UiUtil.CARD, UiUtil.dp(root, 10), 0, 0));
        plus.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        bottom.addView(plus, new FrameLayout.LayoutParams(UiUtil.dp(root, 54), UiUtil.dp(root, 44), Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout tabs = new LinearLayout(this);
        TextView home = tab("الرئيسية (اللوبي)", true);
        TextView tools = tab("الأدوات والتسريع", false);
        tools.setOnClickListener(v -> startActivity(new Intent(this, HubActivity.class)));
        tabs.addView(home, new LinearLayout.LayoutParams(UiUtil.dp(root, 200), UiUtil.dp(root, 44)));
        tabs.addView(tools, new LinearLayout.LayoutParams(UiUtil.dp(root, 200), UiUtil.dp(root, 44)));
        bottom.addView(tabs, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        col.addView(bottom, new LinearLayout.LayoutParams(-1, UiUtil.dp(root, 48)));
        setContentView(root);
    }

    private TextView circle(String s, View.OnClickListener l) {
        TextView t = UiUtil.text(this, s, 18, Color.WHITE, true); t.setGravity(Gravity.CENTER);
        t.setBackground(UiUtil.round(Color.argb(160, 30, 32, 48), UiUtil.dp(t, 22), 0, 0));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(t, 44), UiUtil.dp(t, 44)); lp.setMargins(UiUtil.dp(t, 8), 0, 0, 0); t.setLayoutParams(lp);
        return t;
    }
    private TextView tab(String s, boolean active) {
        TextView t = UiUtil.text(this, s, 15, Color.WHITE, true); t.setGravity(Gravity.CENTER);
        t.setBackground(UiUtil.round(active ? Color.rgb(150, 40, 20) : Color.argb(120, 40, 44, 70), UiUtil.dp(t, 8), active ? UiUtil.ACC : 0, active ? 2 : 0));
        return t;
    }

    @Override protected void onResume() { super.onResume(); UiUtil.immersive(this); Booster.endSessionIfActive(this); reload(); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }

    private void reload() {
        games = Booster.games(this);
        if (sel >= games.size()) sel = 0;
        list.removeAllViews();
        PackageManager pm = getPackageManager();
        for (int i = 0; i < games.size(); i++) {
            final int idx = i; ResolveInfo r = games.get(i);
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(UiUtil.dp(row, 10), UiUtil.dp(row, 8), UiUtil.dp(row, 10), UiUtil.dp(row, 8));
            ImageView ic = new ImageView(this); ic.setImageDrawable(r.loadIcon(pm));
            ic.setOutlineProvider(ViewOutlineProvider.BACKGROUND); ic.setClipToOutline(true);
            ic.setBackground(UiUtil.round(Color.TRANSPARENT, UiUtil.dp(row, 30), 0, 0));
            row.addView(ic, new LinearLayout.LayoutParams(UiUtil.dp(row, 54), UiUtil.dp(row, 54)));
            LinearLayout tx = new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL); tx.setPadding(UiUtil.dp(row, 12), 0, 0, 0);
            TextView n = UiUtil.text(this, r.loadLabel(pm).toString(), 16, Color.WHITE, true); n.setSingleLine(true); n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tx.addView(n); tx.addView(UiUtil.text(this, "الإصدار " + versionOf(r.activityInfo.packageName), 11, Color.rgb(170, 178, 205), false));
            row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(v -> { sel = idx; reload(); pop(); });
            row.setOnLongClickListener(v -> { Booster.setGame(this, r.activityInfo.packageName, false); toast("أُزيلت من الألعاب"); reload(); return true; });
            if (i == sel) row.setBackground(UiUtil.round(Color.argb(70, 255, 165, 31), UiUtil.dp(row, 12), UiUtil.ACC, 2));
            list.addView(row);
            View line = new View(this); line.setBackgroundColor(Color.argb(90, 255, 90, 60)); list.addView(line, new LinearLayout.LayoutParams(-1, 2));
        }
        boolean has = !games.isEmpty();
        empty.setVisibility(has ? View.GONE : View.VISIBLE); big.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            ResolveInfo r = games.get(sel);
            big.setImageDrawable(r.loadIcon(pm)); name.setText(r.loadLabel(pm));
            info.setText("قبل التشغيل: تنظيف الخلفية" + (Booster.hasDnd(this) ? " • كتم الإشعارات" : "") + (Booster.hasWriteSettings(this) ? " • سطوع عالٍ" : ""));
        } else { name.setText("مسرّع الألعاب"); info.setText("أضف لعبة من زر +"); }
    }

    private void pop() { ObjectAnimator a = ObjectAnimator.ofFloat(big, "scaleX", .8f, 1f); a.setDuration(250).start(); ObjectAnimator b = ObjectAnimator.ofFloat(big, "scaleY", .8f, 1f); b.setDuration(250).start(); }

    private String versionOf(String pkg) {
        try { String v = getPackageManager().getPackageInfo(pkg, 0).versionName; return v == null ? "-" : v; } catch (Exception e) { return "-"; }
    }

    private void startGame() {
        if (games.isEmpty()) { startActivity(new Intent(this, AppsActivity.class)); return; }
        final String pkg = games.get(sel).activityInfo.packageName;
        final String res = Booster.startSession(this, pkg);
        final Runnable go = () -> {
            Toast.makeText(this, res, Toast.LENGTH_LONG).show();
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) startActivity(i);
        };
        if (!Booster.flag(this, "launchAnim", true)) { go.run(); return; }
        // حركة الانطلاق: حلقات + صاعقة تكبر ثم تشغيل اللعبة
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

    private void pickGameToAdd() {
        final List<ResolveInfo> all = new ArrayList<>();
        for (ResolveInfo r : Booster.launchable(this)) if (!Booster.isGame(this, r)) all.add(r);
        if (all.isEmpty()) { toast("كل تطبيقاتك مضافة"); return; }
        PackageManager pm = getPackageManager();
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) names[i] = all.get(i).loadLabel(pm).toString();
        new AlertDialog.Builder(this).setTitle("إضافة لعبة").setItems(names, (d, w) -> {
            Booster.setGame(this, all.get(w).activityInfo.packageName, true); sel = 0; reload(); toast("أُضيفت: " + names[w]);
        }).setNegativeButton("إلغاء", null).show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}

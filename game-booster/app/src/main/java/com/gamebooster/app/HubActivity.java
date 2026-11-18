package com.gamebooster.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** لوحة الخيارات بعد المقدمة: عدّادات + أدوات التسريع. */
public class HubActivity extends Activity {
    private Tile tDnd, tSound, tBright, tBubble;
    private Gauge gRam, gTemp;
    private TextView status, batt;
    private SeekBar vol;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { @Override public void run() { updateGauges(); h.postDelayed(this, 1500); } };

    // ---------- عنصر الأداة ----------
    static class Tile extends LinearLayout {
        final TextView icon, label, state;
        Tile(Context c, String ic, String lb) {
            super(c);
            setOrientation(VERTICAL); setGravity(Gravity.CENTER);
            icon = UiUtil.text(c, ic, 26, Color.WHITE, false); icon.setGravity(Gravity.CENTER);
            label = UiUtil.text(c, lb, 12, Color.WHITE, true); label.setGravity(Gravity.CENTER);
            state = UiUtil.text(c, "", 10, Color.rgb(160, 168, 195), false); state.setGravity(Gravity.CENTER);
            addView(icon); addView(label); addView(state);
            setPadding(8, 10, 8, 10);
            set(false, null);
        }
        void set(boolean on, String st) {
            setBackground(UiUtil.round(on ? Color.argb(60, 255, 165, 31) : UiUtil.CARD, UiUtil.dp(this, 14), on ? UiUtil.ACC : Color.TRANSPARENT, on ? 3 : 0));
            state.setText(st == null ? "" : st); state.setTextColor(on ? UiUtil.ACC : Color.rgb(160, 168, 195));
        }
    }

    // ---------- العدّاد ----------
    static class Gauge extends View {
        float v; String label = "", text = ""; int color = UiUtil.ACC;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final RectF r = new RectF();
        Gauge(Context c) { super(c); }
        void set(float v, String text, String label, int color) { this.v = v; this.text = text; this.label = label; this.color = color; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), d = Math.min(w, hh) - 24, cx = w / 2, cy = hh / 2;
            r.set(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2);
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(d * .09f);
            p.setColor(Color.argb(40, 255, 255, 255)); c.drawArc(r, 135, 270, false, p);
            p.setColor(color); c.drawArc(r, 135, 270 * Math.max(0.02f, Math.min(1, v)), false, p);
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(d * .22f); c.drawText(text, cx, cy + d * .04f, p);
            p.setTextSize(d * .11f); p.setColor(color); c.drawText(label, cx, cy + d * .2f, p);
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.immersive(this);
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(UiUtil.BG);
        root.setPadding(UiUtil.dp(root, 28), UiUtil.dp(root, 14), UiUtil.dp(root, 28), UiUtil.dp(root, 14));

        // يسار: العدّادات
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL); left.setGravity(Gravity.CENTER);
        left.addView(UiUtil.text(this, "⚡ مسرّع الألعاب", 18, Color.WHITE, true));
        LinearLayout gs = new LinearLayout(this);
        gRam = new Gauge(this); gTemp = new Gauge(this);
        gs.addView(gRam, new LinearLayout.LayoutParams(0, -1, 1)); gs.addView(gTemp, new LinearLayout.LayoutParams(0, -1, 1));
        left.addView(gs, new LinearLayout.LayoutParams(-1, 0, 1));
        batt = UiUtil.text(this, "", 13, Color.rgb(190, 200, 230), false); batt.setGravity(Gravity.CENTER);
        left.addView(batt);
        root.addView(left, new LinearLayout.LayoutParams(0, -1, 2.2f));

        // وسط: زر التسريع + الصوت
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL); mid.setGravity(Gravity.CENTER); mid.setPadding(16, 0, 16, 0);
        Button boost = new Button(this);
        boost.setText("⚡ تسريع الآن"); boost.setTextSize(20); boost.setTypeface(Typeface.DEFAULT_BOLD); boost.setTextColor(Color.rgb(42, 18, 0));
        boost.setBackground(UiUtil.round(UiUtil.ACC, UiUtil.dp(mid, 36), Color.WHITE, 3));
        boost.setOnClickListener(v -> { status.setText(Booster.boostAll(this, null)); refreshTiles(); updateGauges(); });
        mid.addView(boost, new LinearLayout.LayoutParams(-1, UiUtil.dp(mid, 70)));
        status = UiUtil.text(this, "اضغط للتسريع الكامل", 12, Color.rgb(190, 200, 230), false); status.setGravity(Gravity.CENTER); status.setPadding(0, 12, 0, 12);
        mid.addView(status);
        mid.addView(UiUtil.text(this, "🔊 مستوى الصوت", 13, Color.WHITE, true));
        vol = new SeekBar(this); vol.setMax(100);
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) { if (user) Booster.setMediaVolumePct(HubActivity.this, v); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        mid.addView(vol);
        root.addView(mid, new LinearLayout.LayoutParams(0, -1, 3));

        // يمين: الأدوات
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        Tile tBoost = new Tile(this, "🧹", "تنظيف الخلفية");
        tDnd = new Tile(this, "🔕", "كتم الإشعارات والمكالمات");
        tSound = new Tile(this, "🔊", "وضع صوت الألعاب");
        tBright = new Tile(this, "☀️", "سطوع أعلى");
        tBubble = new Tile(this, "🫧", "الفقاعة العائمة");
        Tile tGames = new Tile(this, "🎮", "مكتبة الألعاب");
        Tile tNet = new Tile(this, "📶", "الإنترنت");
        Tile tPerm = new Tile(this, "🔐", "الصلاحيات");
        Tile tExit = new Tile(this, "⏻", "خروج");
        Tile[] all = {tGames, tBoost, tDnd, tSound, tBright, tBubble, tNet, tPerm, tExit};
        for (Tile t : all) {
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0; lp.height = UiUtil.dp(grid, 84); lp.setMargins(6, 6, 6, 6);
            grid.addView(t, lp);
        }
        ScrollView sv = new ScrollView(this); sv.addView(grid);
        root.addView(sv, new LinearLayout.LayoutParams(0, -1, 4.6f));
        setContentView(root);

        tBoost.setOnClickListener(v -> { status.setText("✔ تحرّر ~" + Booster.killBackground(this, null) + " MB"); updateGauges(); });
        tDnd.setOnClickListener(v -> {
            boolean on = !Booster.dndOn(this);
            if (!Booster.setDnd(this, on)) { toast("امنح صلاحية «عدم الإزعاج» أولاً"); startActivity(Booster.dndSettings()); }
            refreshTiles();
        });
        tSound.setOnClickListener(v -> { Booster.setGameSound(this, !Booster.gameSoundOn(this)); refreshTiles(); refreshVol(); });
        tBright.setOnClickListener(v -> {
            if (!Booster.setBright(this, !Booster.brightOn(this))) { toast("امنح صلاحية تعديل إعدادات النظام أولاً"); startActivity(Booster.writeSettings(this)); }
            refreshTiles();
        });
        tBubble.setOnClickListener(v -> {
            if (!Booster.hasOverlay(this)) { toast("امنح صلاحية الظهور فوق التطبيقات أولاً"); startActivity(Booster.overlaySettings(this)); return; }
            if (OverlayService.running) stopService(new Intent(this, OverlayService.class));
            else {
                Intent i = new Intent(this, OverlayService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                toast("الفقاعة ظهرت. اسحبها أو اضغطها.");
            }
            h.postDelayed(this::refreshTiles, 400);
        });
        tGames.setOnClickListener(v -> { startActivity(new Intent(this, LauncherActivity.class)); finish(); });
        tNet.setOnClickListener(v -> {
            try { startActivity(new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : Settings.ACTION_WIRELESS_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
        });
        tPerm.setOnClickListener(v -> startActivity(new Intent(this, PermissionsActivity.class)));
        tExit.setOnClickListener(v -> finishAffinity());
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void refreshTiles() {
        tDnd.set(Booster.dndOn(this), Booster.hasDnd(this) ? (Booster.dndOn(this) ? "مفعّل" : "متوقف") : "يحتاج صلاحية");
        tSound.set(Booster.gameSoundOn(this), Booster.gameSoundOn(this) ? "مفعّل" : "متوقف");
        tBright.set(Booster.brightOn(this), Booster.hasWriteSettings(this) ? (Booster.brightOn(this) ? "مفعّل" : "متوقف") : "يحتاج صلاحية");
        tBubble.set(OverlayService.running, Booster.hasOverlay(this) ? (OverlayService.running ? "ظاهرة" : "مخفية") : "يحتاج صلاحية");
    }
    private void refreshVol() { vol.setProgress(Booster.mediaVolumePct(this)); }

    private void updateGauges() {
        long tot = Booster.totalMem(this), free = Booster.freeMem(this);
        float used = 1f - (float) free / tot, temp = Booster.batteryTemp(this);
        gRam.set(used, Math.round(used * 100) + "%", "الذاكرة", used > .85f ? UiUtil.WARN : UiUtil.ACC);
        gTemp.set(Math.min(1f, temp / 50f), Math.round(temp) + "°", "الحرارة", temp >= 42 ? UiUtil.WARN : UiUtil.OK);
        batt.setText("🔋 " + Booster.batteryPct(this) + "%   ·   متاح " + free / (1024 * 1024) + " MB" + (temp >= 42 ? "\n⚠️ الجهاز ساخن" : ""));
    }

    @Override protected void onResume() { super.onResume(); UiUtil.immersive(this); refreshTiles(); refreshVol(); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }
}

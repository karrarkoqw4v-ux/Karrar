package com.gamebooster.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import rikka.shizuku.Shizuku;

/** بوابة الدخول: لا يدخل التطبيق قبل تفعيل Shizuku ومنحه الإذن. */
public class GateActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout steps;
    private boolean moved; private static boolean skipped;
    private final Shizuku.OnRequestPermissionResultListener permL = (code, res) -> refresh();
    private final Shizuku.OnBinderReceivedListener recvL = this::refresh;
    private final Shizuku.OnBinderDeadListener deadL = this::refresh;
    private final Runnable poll = new Runnable() { @Override public void run() { refresh(); h.postDelayed(this, 1200); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.immersive(this);
        if (skipped) { next(); return; }
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(UiUtil.BG); root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(UiUtil.dp(root, 40), UiUtil.dp(root, 16), UiUtil.dp(root, 40), UiUtil.dp(root, 16));
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); left.setGravity(Gravity.CENTER);
        View ic = UiUtil.icon(this, "shield", UiUtil.OK); left.addView(ic, new LinearLayout.LayoutParams(UiUtil.dp(left, 84), UiUtil.dp(left, 84)));
        TextView t = UiUtil.text(this, "تفعيل Shizuku", 26, Color.WHITE, true); t.setGravity(Gravity.CENTER); left.addView(t);
        TextView d = UiUtil.text(this, "يحتاجه المسرّع لتنفيذ التسريع الحقيقي: وضع الأداء للعبة، تنظيف الكاش، الإغلاق القسري، لقطة الشاشة وقراءة استهلاك المعالج.", 13, Color.rgb(170, 178, 205), false);
        d.setGravity(Gravity.CENTER); d.setPadding(0, UiUtil.dp(d, 10), 0, 0); left.addView(d);
        root.addView(left, new LinearLayout.LayoutParams(0, -2, 4));
        LinearLayout right = new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL); right.setPadding(UiUtil.dp(right, 24), 0, 0, 0);
        steps = new LinearLayout(this); steps.setOrientation(LinearLayout.VERTICAL); right.addView(steps);
        TextView skip = UiUtil.text(this, "متابعة بدون Shizuku (ميزات أقل)", 12, Color.rgb(140, 148, 175), false); skip.setGravity(Gravity.CENTER); skip.setPadding(0, UiUtil.dp(skip, 14), 0, 0);
        skip.setOnClickListener(v -> { skipped = true; next(); });
        right.addView(skip);
        root.addView(right, new LinearLayout.LayoutParams(0, -2, 6));
        UiUtil.applySafeInsets(root);
        setContentView(root);
        try { Shizuku.addBinderReceivedListenerSticky(recvL); Shizuku.addBinderDeadListener(deadL); Shizuku.addRequestPermissionResultListener(permL); } catch (Throwable ignored) {}
    }

    @Override protected void onResume() { super.onResume(); UiUtil.immersive(this); h.post(poll); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(poll); }
    @Override protected void onDestroy() {
        super.onDestroy();
        try { Shizuku.removeBinderReceivedListener(recvL); Shizuku.removeBinderDeadListener(deadL); Shizuku.removeRequestPermissionResultListener(permL); } catch (Throwable ignored) {}
    }

    private void next() {
        if (moved) return; moved = true;
        startActivity(new Intent(this, IntroActivity.class)); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); finish();
    }

    private void refresh() {
        final boolean inst = ShizukuHelper.installed(this), run = ShizukuHelper.running(), ok = ShizukuHelper.granted();
        steps.removeAllViews();
        steps.addView(step("١", "تثبيت تطبيق Shizuku", inst || run, "تثبيت", v -> startActivity(ShizukuHelper.installIntent())));
        steps.addView(step("٢", "تشغيل خدمة Shizuku (افتحه واختر «التشغيل عبر التصحيح اللاسلكي» أو Root)", run, "فتح Shizuku", v -> {
            Intent i = ShizukuHelper.managerIntent(this); if (i != null) startActivity(i); else Toast.makeText(this, "Shizuku غير مثبّت", Toast.LENGTH_SHORT).show(); }));
        steps.addView(step("٣", "السماح للمسرّع باستخدام Shizuku", ok, "السماح", v -> {
            if (!run) Toast.makeText(this, "شغّل خدمة Shizuku أولاً", Toast.LENGTH_SHORT).show(); else ShizukuHelper.request(1); }));
        if (ok) h.postDelayed(this::next, 500);
    }

    private LinearLayout step(String n, String title, boolean done, String btn, android.view.View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UiUtil.round(UiUtil.CARD, UiUtil.dp(row, 14), done ? UiUtil.OK : Color.TRANSPARENT, done ? 2 : 0));
        row.setPadding(UiUtil.dp(row, 14), UiUtil.dp(row, 10), UiUtil.dp(row, 14), UiUtil.dp(row, 10));
        row.addView(UiUtil.text(this, done ? "✔" : n, 22, done ? UiUtil.OK : UiUtil.ACC, true));
        TextView t = UiUtil.text(this, title, 14, Color.WHITE, true); t.setPadding(UiUtil.dp(row, 12), 0, UiUtil.dp(row, 12), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (!done) {
            Button b = new Button(this); b.setText(btn); b.setTextColor(Color.rgb(42, 18, 0)); b.setBackground(UiUtil.round(UiUtil.ACC, UiUtil.dp(row, 18), 0, 0)); b.setOnClickListener(l);
            row.addView(b, new LinearLayout.LayoutParams(UiUtil.dp(row, 120), UiUtil.dp(row, 38)));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, UiUtil.dp(row, 6), 0, 0); row.setLayoutParams(lp);
        return row;
    }
}

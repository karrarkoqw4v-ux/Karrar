package com.gamebooster.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

/** كل الصلاحيات التي يستطيع التطبيق طلبها، مع زر «منح الكل» عبر Shizuku. */
public class PermissionsActivity extends Activity {
    private interface Check { boolean ok(); }
    private interface Grant { void run(); }
    private static class Item {
        String icon, title, desc; Check check; Grant grant; boolean auto;
        Item(String i, String t, String d, Check c, Grant g, boolean auto) { icon = i; title = t; desc = d; check = c; grant = g; this.auto = auto; }
    }
    private final List<Item> items = new ArrayList<>();
    private LinearLayout list; private TextView allBtn;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.immersive(this);
        items.add(new Item("moon", "كتم الإشعارات والمكالمات", "للتحكم في «عدم الإزعاج» أثناء اللعب حتى لا تقاطعك مكالمة أو إشعار.", () -> Booster.hasDnd(this), () -> startActivity(Booster.dndSettings()), false));
        items.add(new Item("layers", "الظهور فوق التطبيقات", "لعرض لوحة اللعبة وشريط المعلومات فوق اللعبة بدون الخروج منها.", () -> Booster.hasOverlay(this), () -> startActivity(Booster.overlaySettings(this)), false));
        items.add(new Item("wrench", "تعديل إعدادات النظام", "لرفع السطوع وقفل التدوير أثناء اللعب وإرجاعهما بعدها.", () -> Booster.hasWriteSettings(this), () -> startActivity(Booster.writeSettings(this)), false));
        items.add(new Item("bell", "إشعار الخدمة", "يلزم لإبقاء اللوحة تعمل في الخلفية (أندرويد 13 فما فوق).", () -> Booster.hasPostNotifications(this), () -> { if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7); }, false));
        items.add(new Item("batt", "الاستثناء من توفير البطارية", "حتى لا يوقف النظام اللوحة وأنت داخل اللعبة.", () -> Booster.hasBatteryExempt(this), () -> startActivity(Booster.batteryIntent(this)), false));
        items.add(new Item("clock", "الوصول لبيانات الاستخدام", "لمعرفة اللعبة المفتوحة الآن عندما لا يتوفر Shizuku.", () -> Booster.hasUsage(this), () -> startActivity(Booster.usageIntent()), false));
        items.add(new Item("shield", "تعديل الإعدادات الآمنة", "للتحكم في معدّل التحديث والتكبير مباشرة. تُمنح عبر Shizuku فقط (زر «منح الكل»).", () -> Booster.hasSecure(this), () -> toast("استخدم زر «منح الكل عبر Shizuku»"), false));
        items.add(new Item("pad", "معرفة التطبيقات والألعاب المثبّتة", "لعرض ألعابك وتشغيلها. تُمنح تلقائياً عند التثبيت.", () -> true, null, true));
        items.add(new Item("broom", "إغلاق تطبيقات الخلفية", "لتحرير الذاكرة قبل اللعب. تُمنح تلقائياً ولا تغلق التطبيقات النظامية.", () -> true, null, true));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(UiUtil.BG);
        root.setPadding(UiUtil.dp(root, 36), UiUtil.dp(root, 12), UiUtil.dp(root, 36), UiUtil.dp(root, 10));
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiUtil.text(this, "الصلاحيات المطلوبة", 22, Color.WHITE, true));
        titles.addView(UiUtil.text(this, "كل صلاحية تُستخدم فعلاً داخل التطبيق. لا يُغيَّر شيء على جهازك بدون موافقتك.", 12, Color.rgb(160, 168, 195), false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        allBtn = UiUtil.text(this, "منح الكل عبر Shizuku", 14, Color.rgb(42, 18, 0), true); allBtn.setGravity(Gravity.CENTER);
        allBtn.setPadding(UiUtil.dp(allBtn, 14), 0, UiUtil.dp(allBtn, 14), 0); UiUtil.pressable(allBtn);
        allBtn.setOnClickListener(v -> grantAll());
        head.addView(allBtn, new LinearLayout.LayoutParams(-2, UiUtil.dp(head, 42)));
        root.addView(head);
        ScrollView sv = new ScrollView(this); sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView go = UiUtil.text(this, "متابعة", 17, Color.rgb(42, 18, 0), true); go.setGravity(Gravity.CENTER);
        go.setBackground(UiUtil.round(UiUtil.ACC, UiUtil.dp(root, 22), 0, 0)); UiUtil.pressable(go);
        go.setOnClickListener(v -> { startActivity(new Intent(this, LauncherActivity.class)); finish(); });
        root.addView(go, new LinearLayout.LayoutParams(UiUtil.dp(root, 220), UiUtil.dp(root, 46)));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); UiUtil.immersive(this); render(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void grantAll() {
        if (!ShizukuHelper.granted()) { toast("فعّل Shizuku ومنحه الإذن أولاً"); return; }
        allBtn.setText("جارٍ المنح...");
        ShizukuHelper.run(() -> { Booster.grantAllViaShizuku(this); h.post(() -> { toast("تمت محاولة منح كل الصلاحيات"); render(); }); });
    }

    private void render() {
        boolean sz = ShizukuHelper.granted();
        allBtn.setText("منح الكل عبر Shizuku"); allBtn.setAlpha(sz ? 1f : .5f);
        allBtn.setBackground(UiUtil.round(UiUtil.ACC, UiUtil.dp(allBtn, 21), 0, 0));
        list.removeAllViews();
        for (Item it : items) {
            boolean ok = it.check.ok();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UiUtil.round(UiUtil.CARD, UiUtil.dp(row, 14), ok ? UiUtil.OK : Color.TRANSPARENT, ok ? 2 : 0));
            row.setPadding(UiUtil.dp(row, 14), UiUtil.dp(row, 9), UiUtil.dp(row, 14), UiUtil.dp(row, 9));
            row.addView(UiUtil.icon(this, it.icon, ok ? UiUtil.OK : Color.WHITE), new LinearLayout.LayoutParams(UiUtil.dp(row, 28), UiUtil.dp(row, 28)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL); col.setPadding(UiUtil.dp(row, 12), 0, UiUtil.dp(row, 12), 0);
            col.addView(UiUtil.text(this, it.title, 15, Color.WHITE, true));
            col.addView(UiUtil.text(this, it.desc, 11, Color.rgb(160, 168, 195), false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            if (ok) row.addView(UiUtil.text(this, it.auto ? "✔ تلقائي" : "✔ ممنوحة", 13, UiUtil.OK, true));
            else {
                TextView b = UiUtil.text(this, "منح", 14, Color.rgb(42, 18, 0), true); b.setGravity(Gravity.CENTER);
                b.setBackground(UiUtil.round(UiUtil.ACC, UiUtil.dp(row, 17), 0, 0)); UiUtil.pressable(b); b.setOnClickListener(v -> it.grant.run());
                row.addView(b, new LinearLayout.LayoutParams(UiUtil.dp(row, 84), UiUtil.dp(row, 34)));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, UiUtil.dp(row, 6), 0, 0);
            list.addView(row, lp);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) { render(); }
}

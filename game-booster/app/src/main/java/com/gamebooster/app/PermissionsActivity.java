package com.gamebooster.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

/** يطلب الصلاحيات من الجهاز بعد الضغط على «تسريع الآن». */
public class PermissionsActivity extends Activity {
    private interface Check { boolean ok(); }
    private interface Grant { void run(); }
    private static class Item {
        String icon, title, desc; Check check; Grant grant; boolean auto;
        Item(String i, String t, String d, Check c, Grant g, boolean auto) { icon = i; title = t; desc = d; check = c; grant = g; this.auto = auto; }
    }
    private final List<Item> items = new ArrayList<>();
    private LinearLayout list;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.immersive(this);
        items.add(new Item("🔕", "كتم الإشعارات والمكالمات", "يسمح للمسرّع بتفعيل وضع «عدم الإزعاج» الكامل أثناء اللعب حتى لا تقاطعك مكالمة أو إشعار.",
                () -> Booster.hasDnd(this), () -> startActivity(Booster.dndSettings()), false));
        items.add(new Item("🫧", "الظهور فوق التطبيقات", "لعرض الفقاعة العائمة وأدوات التسريع فوق اللعبة بدون الخروج منها.",
                () -> Booster.hasOverlay(this), () -> startActivity(Booster.overlaySettings(this)), false));
        items.add(new Item("🎮", "معرفة التطبيقات والألعاب المثبّتة", "لعرض ألعابك وتشغيلها. هذه الصلاحية تُمنح تلقائياً عند التثبيت.",
                () -> true, null, true));
        items.add(new Item("🧹", "إغلاق تطبيقات الخلفية", "لتحرير الذاكرة قبل اللعب. تُمنح تلقائياً عند التثبيت ولا تغلق التطبيقات النظامية.",
                () -> true, null, true));
        items.add(new Item("🔔", "إشعار الخدمة", "يلزم لتشغيل الفقاعة العائمة في الخلفية (أندرويد 13 فما فوق).",
                () -> Booster.hasPostNotifications(this), () -> { if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7); }, false));
        items.add(new Item("☀️", "تعديل إعدادات النظام (اختياري)", "لرفع سطوع الشاشة أثناء اللعب وإرجاعه بعدها.",
                () -> Booster.hasWriteSettings(this), () -> startActivity(Booster.writeSettings(this)), false));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiUtil.BG);
        root.setPadding(UiUtil.dp(root, 40), UiUtil.dp(root, 16), UiUtil.dp(root, 40), UiUtil.dp(root, 12));
        root.addView(UiUtil.text(this, "الصلاحيات المطلوبة", 22, android.graphics.Color.WHITE, true));
        root.addView(UiUtil.text(this, "امنح ما تريد، ويمكنك المتابعة حتى لو تركت بعضها. لا يُغيَّر شيء على جهازك بدون موافقتك.", 12, android.graphics.Color.rgb(160, 168, 195), false));
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        Button go = new Button(this);
        go.setText("متابعة ◄");
        go.setTextColor(android.graphics.Color.rgb(42, 18, 0));
        go.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        go.setBackground(UiUtil.round(UiUtil.ACC, UiUtil.dp(root, 22), 0, 0));
        go.setOnClickListener(v -> { startActivity(new Intent(this, HubActivity.class)); finish(); });
        root.addView(go, new LinearLayout.LayoutParams(UiUtil.dp(root, 220), UiUtil.dp(root, 46)));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); UiUtil.immersive(this); render(); }

    private void render() {
        list.removeAllViews();
        for (Item it : items) {
            boolean ok = it.check.ok();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UiUtil.round(UiUtil.CARD, UiUtil.dp(row, 14), ok ? UiUtil.OK : android.graphics.Color.TRANSPARENT, ok ? 2 : 0));
            row.setPadding(UiUtil.dp(row, 14), UiUtil.dp(row, 10), UiUtil.dp(row, 14), UiUtil.dp(row, 10));
            row.addView(UiUtil.text(this, it.icon, 26, android.graphics.Color.WHITE, false));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(UiUtil.dp(row, 12), 0, UiUtil.dp(row, 12), 0);
            col.addView(UiUtil.text(this, it.title, 16, android.graphics.Color.WHITE, true));
            col.addView(UiUtil.text(this, it.desc, 12, android.graphics.Color.rgb(160, 168, 195), false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            if (ok) row.addView(UiUtil.text(this, it.auto ? "✔ تلقائي" : "✔ ممنوحة", 14, UiUtil.OK, true));
            else {
                Button b = new Button(this);
                b.setText("منح");
                b.setTextColor(android.graphics.Color.rgb(42, 18, 0));
                b.setBackground(UiUtil.round(UiUtil.ACC, UiUtil.dp(row, 18), 0, 0));
                b.setOnClickListener(v -> it.grant.run());
                row.addView(b, new LinearLayout.LayoutParams(UiUtil.dp(row, 90), UiUtil.dp(row, 38)));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, UiUtil.dp(row, 8), 0, 0);
            list.addView(row, lp);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) { render(); }
}

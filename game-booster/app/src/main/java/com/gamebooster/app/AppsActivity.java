package com.gamebooster.app;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** «إضافة التطبيقات»: مفتاح لكل تطبيق لتحديده كلعبة (أو استثنائه من التنظيف). */
public class AppsActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.immersive(this);
        final boolean protect = "protect".equals(getIntent().getStringExtra("mode"));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(SettingsUi.CONTENT);
        root.addView(SettingsUi.topBar(this, protect ? "التطبيقات المستثناة من التنظيف" : "إضافة التطبيقات", this::finish), new LinearLayout.LayoutParams(-1, UiUtil.dp(root, 46)));
        ScrollView sv = new ScrollView(this); sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        PackageManager pm = getPackageManager();
        for (ResolveInfo r : Booster.launchable(this)) {
            final String pkg = r.activityInfo.packageName;
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(UiUtil.dp(row, 22), UiUtil.dp(row, 8), UiUtil.dp(row, 30), UiUtil.dp(row, 8));
            ImageView ic = new ImageView(this); ic.setImageDrawable(r.loadIcon(pm));
            row.addView(ic, new LinearLayout.LayoutParams(UiUtil.dp(row, 50), UiUtil.dp(row, 50)));
            LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(UiUtil.dp(row, 14), 0, 0, 0);
            String ver = "-"; try { String v = pm.getPackageInfo(pkg, 0).versionName; if (v != null) ver = v; } catch (Exception ignored) {}
            col.addView(UiUtil.text(this, "الإصدار " + ver, 11, SettingsUi.MUTED, false));
            col.addView(UiUtil.text(this, r.loadLabel(pm).toString(), 18, Color.WHITE, true));
            col.addView(UiUtil.text(this, pkg, 11, SettingsUi.MUTED, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            boolean on = protect ? Booster.isProtected(this, pkg) : Booster.isGame(this, r);
            row.addView(new SettingsUi.Toggle(this, on, v -> { if (protect) Booster.setProtected(this, pkg, v); else Booster.setGame(this, pkg, v); }), new LinearLayout.LayoutParams(UiUtil.dp(row, 54), UiUtil.dp(row, 30)));
            list.addView(row);
            View l = new View(this); l.setBackgroundColor(SettingsUi.LINE); list.addView(l, new LinearLayout.LayoutParams(-1, 2));
        }
        UiUtil.applySafeInsets(root);
        setContentView(root);
    }
    @Override protected void onResume() { super.onResume(); UiUtil.immersive(this); }
}

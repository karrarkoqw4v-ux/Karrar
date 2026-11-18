package com.gamebooster.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.*;

/** مسرّع الألعاب: يحرّر الذاكرة من تطبيقات الخلفية ويفعّل عدم الإزعاج ثم يشغّل اللعبة. */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(15, 18, 32), CARD = Color.rgb(28, 33, 56), ACC = Color.rgb(0, 200, 150);
    private SharedPreferences prefs;
    private TextView stats;
    private LinearLayout list;
    private boolean dnd;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("games", MODE_PRIVATE);
        dnd = prefs.getBoolean("__dnd", true);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(36), dp(16), dp(24));
        sv.addView(root);
        setContentView(sv);

        root.addView(text("⚡ مسرّع الألعاب", 26, Color.WHITE, true));
        stats = text("", 15, Color.rgb(190, 200, 230), false);
        stats.setBackground(round(CARD));
        stats.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, dp(12));
        root.addView(stats, lp);

        CheckBox cb = new CheckBox(this);
        cb.setText("تفعيل «عدم الإزعاج» أثناء اللعب");
        cb.setTextColor(Color.WHITE);
        cb.setChecked(dnd);
        cb.setOnCheckedChangeListener((v, c) -> { dnd = c; prefs.edit().putBoolean("__dnd", c).apply(); });
        root.addView(cb);

        root.addView(text("اضغط على اللعبة لتسريع الجهاز وتشغيلها. الضغط المطوّل يثبّتها في الأعلى ⭐", 13, Color.rgb(150, 160, 190), false));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        stats.setText(statsText());
        list.removeAllViews();
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(q, 0);
        List<ResolveInfo> games = new ArrayList<>();
        for (ResolveInfo r : apps) {
            ApplicationInfo ai = r.activityInfo.applicationInfo;
            if (ai.packageName.equals(getPackageName())) continue;
            boolean isGame = prefs.getBoolean(ai.packageName, android.os.Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME)
                    || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
            if (isGame) games.add(r);
        }
        sort(games, pm);
        if (games.isEmpty()) list.addView(text("لم يتم العثور على ألعاب. أضف لعبة من القائمة أدناه.", 14, Color.LTGRAY, false));
        for (ResolveInfo r : games) list.addView(row(r, pm, true));

        list.addView(text("كل التطبيقات (اضغط مطوّلاً لإضافتها كلعبة)", 14, ACC, true));
        List<ResolveInfo> rest = new ArrayList<>(apps);
        rest.removeAll(games);
        sort(rest, pm);
        for (ResolveInfo r : rest) if (!r.activityInfo.packageName.equals(getPackageName())) list.addView(row(r, pm, false));
    }

    private void sort(List<ResolveInfo> l, PackageManager pm) {
        Collections.sort(l, (a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
    }

    private View row(ResolveInfo r, PackageManager pm, boolean game) {
        final String pkg = r.activityInfo.packageName;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(CARD));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(r.loadIcon(pm));
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = text(r.loadLabel(pm).toString(), 16, Color.WHITE, false);
        t.setPadding(dp(12), 0, dp(12), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (game) row.addView(text("🚀", 22, ACC, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, 0);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> boostAndLaunch(pkg));
        row.setOnLongClickListener(v -> {
            prefs.edit().putBoolean(pkg, !game).apply();
            Toast.makeText(this, game ? "أُزيلت من الألعاب" : "أُضيفت إلى الألعاب", Toast.LENGTH_SHORT).show();
            refresh();
            return true;
        });
        return row;
    }

    private void boostAndLaunch(String pkg) {
        long before = freeMem();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        PackageManager pm = getPackageManager();
        int n = 0;
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (system || ai.packageName.equals(pkg) || ai.packageName.equals(getPackageName())) continue;
            am.killBackgroundProcesses(ai.packageName);
            n++;
        }
        if (dnd) setDnd();
        long freed = Math.max(0, freeMem() - before) / (1024 * 1024);
        Toast.makeText(this, "تم تسريع الجهاز ✔ (فُحص " + n + " تطبيق، تحرّر ~" + freed + " MB)", Toast.LENGTH_LONG).show();
        Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch != null) startActivity(launch);
    }

    private void setDnd() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm.isNotificationPolicyAccessGranted()) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        } else {
            Toast.makeText(this, "امنح المسرّع صلاحية «عدم الإزعاج» ثم أعد المحاولة", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        }
    }

    private long freeMem() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return mi.availMem;
    }

    private String statsText() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int lvl = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        float temp = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        String warn = temp >= 42 ? "\n⚠️ الجهاز ساخن، أرح اللعب قليلاً" : "";
        return "🧠 الذاكرة الحرّة: " + mi.availMem / (1024 * 1024) + " / " + mi.totalMem / (1024 * 1024) + " MB"
                + "\n🔋 البطارية: " + (lvl * 100 / scale) + "%   🌡 " + temp + "°C" + warn;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }
    private GradientDrawable round(int c) { GradientDrawable g = new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp(14)); return g; }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

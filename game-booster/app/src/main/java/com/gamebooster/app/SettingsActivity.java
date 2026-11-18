package com.gamebooster.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** صفحة الإعدادات: قائمة جانبية + محتوى. كل خيار هنا يغيّر سلوكاً حقيقياً في التطبيق. */
public class SettingsActivity extends Activity {
    private static final int REQ_IMG = 11;
    private static final String[] NAMES = {"عدم الإزعاج", "الحماية", "المعالج", "#أداء", "الذاكرة", "الخلفية", "زر اللوحة", "لوحة الأدوات", "خيارات أخرى", "الأداء المتقدم", "عن التطبيق"};
    private FrameLayout content;
    private LinearLayout side;
    private int page = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.immersive(this);
        page = getIntent().getIntExtra("page", 0);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(SettingsUi.CONTENT);
        root.addView(SettingsUi.topBar(this, "الإعدادات", this::finish), new LinearLayout.LayoutParams(-1, UiUtil.dp(root, 46)));
        LinearLayout body = new LinearLayout(this);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(SettingsUi.SIDE); sv.setVerticalScrollBarEnabled(false);
        side = new LinearLayout(this); side.setOrientation(LinearLayout.VERTICAL); sv.addView(side);
        body.addView(sv, new LinearLayout.LayoutParams(0, -1, 2.2f));
        content = new FrameLayout(this); body.addView(content, new LinearLayout.LayoutParams(0, -1, 7.8f));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        show(page);
    }

    @Override protected void onResume() { super.onResume(); UiUtil.immersive(this); show(page); }

    private void show(int p) {
        page = p;
        side.removeAllViews();
        for (int i = 0; i < NAMES.length; i++) {
            final int idx = i; String n = NAMES[i];
            if (n.startsWith("#")) { TextView h = UiUtil.text(this, n.substring(1), 15, Color.WHITE, true); h.setPadding(UiUtil.dp(h, 10), UiUtil.dp(h, 14), 0, UiUtil.dp(h, 6)); side.addView(h); continue; }
            TextView t = UiUtil.text(this, n, 16, Color.WHITE, true); t.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            t.setPadding(UiUtil.dp(t, 8), 0, UiUtil.dp(t, 14), 0);
            if (i == p) {
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.rgb(70, 76, 120), Color.rgb(125, 56, 90)});
                t.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{g, new android.graphics.drawable.ColorDrawable(0) {
                    @Override public void draw(Canvas c) { Paint pp = new Paint(); pp.setColor(Color.rgb(224, 69, 58)); Rect r = getBounds(); c.drawRect(r.right - 5, r.top, r.right, r.bottom, pp); } }}));
            }
            t.setOnClickListener(v -> show(idx));
            side.addView(t, new LinearLayout.LayoutParams(-1, UiUtil.dp(side, 46)));
        }
        content.removeAllViews();
        content.addView(buildPage(p), new FrameLayout.LayoutParams(-1, -1));
    }

    private View buildPage(int p) {
        ScrollView[] o = new ScrollView[1]; LinearLayout col = SettingsUi.verticalScroll(this, o);
        switch (p) {
            case 0: pageDnd(col); break;
            case 1: pageProtect(col); break;
            case 2: pageCpu(col); break;
            case 4: pageMem(col); break;
            case 5: pageBackground(col); break;
            case 6: pageBubble(col); break;
            case 7: pageHub(col); break;
            case 8: pageOther(col); break;
            case 9: pageShizuku(col); break;
            case 10: pageAbout(col); break;
        }
        return o[0];
    }

    private void flagRow(LinearLayout col, String title, String sub, String key, boolean def) {
        col.addView(SettingsUi.toggleRow(this, title, sub, Booster.flag(this, key, def), v -> Booster.setFlag(this, key, v)));
    }
    private void needPerm(LinearLayout col, String text, Intent i) {
        TextView t = UiUtil.text(this, text, 14, Color.rgb(255, 110, 100), true); t.setPadding(UiUtil.dp(t, 18), UiUtil.dp(t, 10), UiUtil.dp(t, 18), UiUtil.dp(t, 4));
        col.addView(t); TextView b = SettingsUi.choice(this, "منح الصلاحية", true); b.setOnClickListener(v -> startActivity(i));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(b, 160), UiUtil.dp(b, 40)); lp.setMargins(UiUtil.dp(b, 18), 0, 0, UiUtil.dp(b, 8)); col.addView(b, lp);
    }

    // ===== 0: عدم الإزعاج =====
    private void pageDnd(LinearLayout c) {
        if (!Booster.hasDnd(this)) needPerm(c, "يلزم السماح للتطبيق بالتحكم في «عدم الإزعاج»", Booster.dndSettings());
        flagRow(c, "تفعيل عدم الإزعاج عند بدء اللعبة", "يُفعَّل تلقائياً عند الضغط على «بدء اللعب»، ويُلغى عند رجوعك إلى المسرّع", "dndOnStart", true);
        flagRow(c, "كتم المكالمات أيضاً", "عند الإيقاف تبقى المكالمات من جهات الاتصال المفضّلة تصل إليك", "blockCalls", true);
    }
    // ===== 1: الحماية =====
    private void pageProtect(LinearLayout c) {
        flagRow(c, "تنظيف الذاكرة قبل التشغيل", "يغلق تطبيقات الخلفية غير النظامية قبل فتح اللعبة", "cleanBefore", true);
        TextView b = SettingsUi.choice(this, "التطبيقات المستثناة من التنظيف", false);
        b.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class).putExtra("mode", "protect")));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(b, 320), UiUtil.dp(b, 46)); lp.setMargins(UiUtil.dp(b, 18), UiUtil.dp(b, 16), 0, 0); c.addView(b, lp);
        TextView n = UiUtil.text(this, "التطبيقات المستثناة لا تُغلق أبداً (مثل الموسيقى أو الملاحة).", 12, SettingsUi.MUTED, false); n.setPadding(UiUtil.dp(n, 18), UiUtil.dp(n, 8), 0, 0); c.addView(n);
    }
    // ===== 2/4: العدّادات =====
    private void styleCards(LinearLayout c, String title, String key, String top, String label, int color) {
        TextView t = UiUtil.text(this, title, 18, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setPadding(0, UiUtil.dp(t, 12), 0, UiUtil.dp(t, 10)); c.addView(t);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER);
        int sel = Booster.num(this, key, 0);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            FrameLayout f = SettingsUi.card(this, new SettingsUi.GaugePreview(this, i, color, top, label), sel == i);
            f.setOnClickListener(v -> { Booster.setNum(this, key, idx); show(page); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(f, 150), UiUtil.dp(f, 118)); lp.setMargins(UiUtil.dp(f, 8), 0, UiUtil.dp(f, 8), 0); row.addView(f, lp);
        }
        c.addView(row);
    }
    private void pageCpu(LinearLayout c) {
        styleCards(c, "اختر شكل عدّاد المعالج", "cpuStyle", "GHz", "CPU", Color.rgb(255, 60, 40));
        float g = Booster.cpuGHz();
        TextView t = UiUtil.text(this, g > 0 ? "تردد المعالج الآن: " + String.format(java.util.Locale.US, "%.2f", g) + " GHz" : "هذا الجهاز لا يسمح للتطبيقات بقراءة تردد المعالج، فسيُعرض بدلاً منه حرارة البطارية.", 14, SettingsUi.MUTED, false);
        t.setGravity(Gravity.CENTER); t.setPadding(UiUtil.dp(t, 18), UiUtil.dp(t, 14), UiUtil.dp(t, 18), 0); c.addView(t);
    }
    private void pageMem(LinearLayout c) {
        styleCards(c, "اختر شكل عدّاد الذاكرة", "memStyle", "50%", "RAM", Color.rgb(255, 205, 40));
        TextView res = UiUtil.text(this, "الذاكرة المتاحة الآن: " + Booster.freeMem(this) / (1024 * 1024) + " MB", 14, SettingsUi.MUTED, false);
        res.setGravity(Gravity.CENTER); res.setPadding(0, UiUtil.dp(res, 12), 0, UiUtil.dp(res, 6)); c.addView(res);
        TextView b = SettingsUi.choice(this, "تنظيف الذاكرة الآن", true);
        b.setOnClickListener(v -> { long f = Booster.killBackground(this, null); res.setText("✔ تحرّر ~" + f + " MB — المتاحة الآن: " + Booster.freeMem(this) / (1024 * 1024) + " MB"); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(b, 260), UiUtil.dp(b, 46)); lp.gravity = Gravity.CENTER_HORIZONTAL; c.addView(b, lp);
    }
    // ===== 5: الخلفية =====
    private void pageBackground(LinearLayout c) {
        int sel = Booster.num(this, "bg", 0); String uri = Booster.prefs(this).getString("bgUri", null);
        LinearLayout tabs = new LinearLayout(this); tabs.setGravity(Gravity.CENTER); tabs.setPadding(0, UiUtil.dp(tabs, 10), 0, UiUtil.dp(tabs, 8));
        TextView t1 = SettingsUi.choice(this, "خلفيات التطبيق", uri == null); TextView t2 = SettingsUi.choice(this, "صورة من المعرض", uri != null);
        t2.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"); startActivityForResult(i, REQ_IMG); });
        t1.setOnClickListener(v -> { Booster.prefs(this).edit().remove("bgUri").apply(); show(page); });
        tabs.addView(t1, new LinearLayout.LayoutParams(UiUtil.dp(t1, 170), UiUtil.dp(t1, 40))); tabs.addView(t2, new LinearLayout.LayoutParams(UiUtil.dp(t2, 170), UiUtil.dp(t2, 40))); c.addView(tabs);
        int[][] cols = {{255, 140, 20}, {0, 220, 220}, {255, 50, 30}, {60, 120, 255}};
        LinearLayout grid1 = new LinearLayout(this), grid2 = new LinearLayout(this); grid1.setGravity(Gravity.CENTER); grid2.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            View pv = new View(this) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas cv) { LauncherActivity.drawBgStyle(cv, p, getWidth(), getHeight(), idx, 0); }
            };
            FrameLayout f = SettingsUi.card(this, pv, uri == null && sel == i);
            f.setOnClickListener(v -> { Booster.setNum(this, "bg", idx); Booster.prefs(this).edit().remove("bgUri").apply(); show(page); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(f, 230), UiUtil.dp(f, 100)); lp.setMargins(UiUtil.dp(f, 8), UiUtil.dp(f, 6), UiUtil.dp(f, 8), UiUtil.dp(f, 6));
            (i < 2 ? grid1 : grid2).addView(f, lp);
        }
        c.addView(grid1); c.addView(grid2);
    }
    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_IMG && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            Booster.prefs(this).edit().putString("bgUri", u.toString()).apply(); show(page);
        }
    }
    // ===== 6: زر اللوحة =====
    private void pageBubble(LinearLayout c) {
        TextView t = UiUtil.text(this, "شكل زر فتح اللوحة (يظهر وسط الشاشة، اسحبه لأي مكان)", 18, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setPadding(0, UiUtil.dp(t, 12), 0, UiUtil.dp(t, 12)); c.addView(t);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER); int sel = Booster.num(this, "bubbleStyle", 0);
        String[] labels = {"دائرة", "مربع مستدير"};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            View pv = new View(this) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas cv) {
                    float w = getWidth(), h = getHeight(), r = h * .22f, cx = w / 2, cy = (h - 30) / 2; p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(24, 27, 40)); cv.drawRoundRect(10, 10, w - 10, h - 40, 18, 18, p);
                    p.setShader(new LinearGradient(0, cy - r, 0, cy + r, Color.rgb(255, 200, 80), Color.rgb(230, 110, 10), Shader.TileMode.CLAMP));
                    if (idx == 0) cv.drawCircle(cx, cy, r, p); else cv.drawRoundRect(cx - r, cy - r, cx + r, cy + r, r * .38f, r * .38f, p); p.setShader(null);
                    Icons.draw(cv, "bolt", cx, cy, r * 1.1f, Color.rgb(60, 25, 0), p);
                    p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h * .09f); cv.drawText(labels[idx], w / 2, h - 12, p);
                }
            };
            FrameLayout f = SettingsUi.card(this, pv, sel == i); f.setOnClickListener(v -> { Booster.setNum(this, "bubbleStyle", idx); show(page); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(f, 200), UiUtil.dp(f, 130)); lp.setMargins(UiUtil.dp(f, 10), 0, UiUtil.dp(f, 10), 0); row.addView(f, lp);
        }
        c.addView(row);
        TextView n = UiUtil.text(this, "يتغيّر الشكل عند إعادة تشغيل الزر (ابدأ اللعب من جديد). اضغطه لفتح اللوحة.", 12, SettingsUi.MUTED, false); n.setGravity(Gravity.CENTER); n.setPadding(0, UiUtil.dp(n, 12), 0, 0); c.addView(n);
    }
    // ===== 7: لوحة الأدوات =====
    private void pageHub(LinearLayout c) {
        TextView t = UiUtil.text(this, "عدد أعمدة أزرار لوحة الأدوات", 18, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setPadding(0, UiUtil.dp(t, 14), 0, UiUtil.dp(t, 12)); c.addView(t);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER); int sel = Booster.num(this, "hubCols", 3);
        for (int cols = 3; cols <= 4; cols++) {
            final int cc = cols; TextView b = SettingsUi.choice(this, cols == 3 ? "3 أعمدة (كبيرة)" : "4 أعمدة (مضغوطة)", sel == cols);
            b.setOnClickListener(v -> { Booster.setNum(this, "hubCols", cc); show(page); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(b, 200), UiUtil.dp(b, 46)); lp.setMargins(UiUtil.dp(b, 8), 0, UiUtil.dp(b, 8), 0); row.addView(b, lp);
        }
        c.addView(row);
    }
    // ===== 8: خيارات أخرى =====
    private void pageOther(LinearLayout c) {
        if (!Booster.hasWriteSettings(this)) needPerm(c, "قفل السطوع والتدوير يحتاجان صلاحية «تعديل إعدادات النظام»", Booster.writeSettings(this));
        flagRow(c, "قفل السطوع", "يرفع السطوع إلى الأعلى أثناء اللعب", "lockBright", false);
        flagRow(c, "قفل التدوير", "يثبّت الشاشة بالوضع الأفقي أثناء اللعب", "lockRotation", false);
        flagRow(c, "إرجاع الإعدادات عند الرجوع", "يُلغى الكتم ويرجع السطوع والتدوير إلى ما كانا عليه عند عودتك للمسرّع", "restoreOnReturn", true);
        flagRow(c, "حركة الانطلاق", "تظهر حركة قصيرة عند الضغط على «بدء اللعب»", "launchAnim", true);
        flagRow(c, "خلفية متحركة", "تحريك الشبكة والتوهج في الصفحة الرئيسية", "bgAnim", true);
        flagRow(c, "تعطيل الفقاعة العائمة", "لا يمكن تشغيل الفقاعة طالما هذا الخيار مفعّل", "disableBubble", false);
    }
    // ===== 9: الأداء المتقدم (Shizuku) =====
    private void pageShizuku(final LinearLayout c) {
        boolean ok = ShizukuHelper.granted();
        c.addView(info("حالة Shizuku", ok ? "يعمل ومسموح ✔" : (ShizukuHelper.running() ? "يعمل — لم تُمنح الصلاحية" : "غير مفعّل")));
        final TextView who = UiUtil.text(this, ok ? "جارٍ الفحص..." : "الأوامر لن تعمل قبل تفعيل Shizuku ومنحه الإذن.", 14, ok ? SettingsUi.MUTED : Color.rgb(255, 110, 100), false);
        who.setPadding(UiUtil.dp(who, 18), UiUtil.dp(who, 8), UiUtil.dp(who, 18), UiUtil.dp(who, 4)); c.addView(who);
        if (ok) ShizukuHelper.async("id", o -> who.setText("هوية التنفيذ: " + (o.isEmpty() ? "—" : o)));
        else { TextView b = SettingsUi.choice(this, "تفعيل الآن", true); b.setOnClickListener(v -> { if (ShizukuHelper.running()) ShizukuHelper.request(2); else { android.content.Intent i = ShizukuHelper.managerIntent(this); startActivity(i != null ? i : ShizukuHelper.installIntent()); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(b, 180), UiUtil.dp(b, 42)); lp.setMargins(UiUtil.dp(b, 18), 0, 0, UiUtil.dp(b, 8)); c.addView(b, lp); }
        flagRow(c, "وضع الأداء للعبة (Game Mode)", "يطبّق وضع الأداء من النظام على اللعبة عند بدئها ويرجّعه عند عودتك", "gameModePerf", true);
        flagRow(c, "إغلاق قسري لتطبيقات الخلفية", "أقوى من التنظيف العادي ويستثني التطبيقات المحمية", "forceStop", true);
        flagRow(c, "تنظيف الكاش عند التشغيل", "يمسح الذاكرة المؤقتة لكل التطبيقات قبل اللعب", "trimCaches", true);
        
        flagRow(c, "تفعيل «أداء ثابت» تلقائياً عند بدء اللعبة", "وضع أداء ثابت + أعلى معدّل تحديث + تنظيف + ضبط الشبكة، ويُرجع كل شيء عند خروجك من اللعبة", "fixedPerf", false);
        flagRow(c, "لمس أسرع", "يقلّل مهلة الضغط المطوّل واللمس المتعدد أثناء اللعب ويرجعها بعد الخروج", "touchBoost", false);
        flagRow(c, "إظهار اللوحة تلقائياً عند بدء اللعبة", "يفتح لسان اللوحة على حافة الشاشة فوق اللعبة", "autoPanel", true);
    }

    // ===== 10: عن التطبيق =====
    private void pageAbout(LinearLayout c) {
        String ver = "1.0.0"; try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        c.addView(info("الإصدار", ver));
        c.addView(info("عدم الإزعاج", Booster.hasDnd(this) ? "ممنوحة ✔" : "غير ممنوحة"));
        c.addView(info("الظهور فوق التطبيقات", Booster.hasOverlay(this) ? "ممنوحة ✔" : "غير ممنوحة"));
        c.addView(info("تعديل إعدادات النظام", Booster.hasWriteSettings(this) ? "ممنوحة ✔" : "غير ممنوحة"));
        c.addView(info("الإشعارات", Booster.hasPostNotifications(this) ? "ممنوحة ✔" : "غير ممنوحة"));
        TextView b = SettingsUi.choice(this, "إدارة الصلاحيات", true); b.setOnClickListener(v -> startActivity(new Intent(this, PermissionsActivity.class)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtil.dp(b, 220), UiUtil.dp(b, 46)); lp.setMargins(UiUtil.dp(b, 18), UiUtil.dp(b, 16), 0, 0); c.addView(b, lp);
    }
    private View info(String k, String v) {
        LinearLayout w = new LinearLayout(this); w.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r = new LinearLayout(this); r.setPadding(UiUtil.dp(r, 18), UiUtil.dp(r, 12), UiUtil.dp(r, 24), UiUtil.dp(r, 12));
        r.addView(UiUtil.text(this, k, 16, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1)); r.addView(UiUtil.text(this, v, 15, SettingsUi.MUTED, false));
        w.addView(r); View l = new View(this); l.setBackgroundColor(SettingsUi.LINE); w.addView(l, new LinearLayout.LayoutParams(-1, 2)); return w;
    }
}

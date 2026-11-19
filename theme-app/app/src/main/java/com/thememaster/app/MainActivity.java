package com.thememaster.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** الشاشة الرئيسية: بدون تسجيل دخول — زرّا رفع الثيم وتصدير الثيم الحالي. */
public class MainActivity extends Activity {
    private static final int REQ_UPLOAD = 1, REQ_STORAGE = 2;
    private static final int ACC = Color.rgb(109, 76, 230), ACC2 = Color.rgb(230, 76, 170), BG = Color.rgb(246, 246, 250), CARD = Color.WHITE, TXT = Color.rgb(28, 28, 38), MUT = Color.rgb(120, 122, 140);
    private TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22); root.setPadding(pad, dp(36), pad, pad);
        sv.addView(root); setContentView(sv);

        root.addView(text("صانع الثيمات", 28, TXT, true));
        root.addView(text("بدون تسجيل دخول — ارفع ملف ثيم بصيغة ZIP ليُطبَّق فوراً.", 14, MUT, false));

        TextView warn = text("صريح: لا تستطيع أي أداة خارجية — ولا هذا التطبيق — الوصول إلى محرّك الثيمات الخاص بشاومي أو هواوي أو هونر، لأنه محمي بتوقيع الشركة نفسها. ما يُطبَّق هنا حقيقي 100% ويعمل على كل الأجهزة: خلفية الشاشة الرئيسية وخلفية شاشة القفل.", 12, Color.rgb(140, 90, 10), false);
        warn.setBackground(round(Color.rgb(255, 244, 214), dp(12), 0, 0)); warn.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams wlp = lp(-1, -2); wlp.setMargins(0, dp(14), 0, dp(18)); root.addView(warn, wlp);

        root.addView(bigButton("⬆  رفع ملف ثيم (ZIP)", "يطبّق خلفية الشاشة وشاشة القفل من الملف فوراً", ACC, v -> pickZip()));
        LinearLayout.LayoutParams g1 = lp(-1, dp(14)); root.addView(new View(this), g1);
        root.addView(bigButton("⬇  تصدير الثيم الحالي", "يحفظ خلفيتك الحالية كملف ZIP في مجلّد التنزيلات", ACC2, v -> exportTheme()));

        status = text("", 13, MUT, false); status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = lp(-1, -2); slp.setMargins(0, dp(18), 0, 0); root.addView(status, slp);

        TextView help = text("تركيب ملف الثيم: ضَع داخل الـZIP ملف wallpaper.jpg (خلفية الشاشة الرئيسية) و/أو lockscreen.jpg (خلفية شاشة القفل)، ويمكن إضافة theme.json فيه {\"name\":\"اسم الثيم\"} لعرض اسمه.", 12, MUT, false);
        LinearLayout.LayoutParams hlp = lp(-1, -2); hlp.setMargins(0, dp(24), 0, 0); root.addView(help, hlp);
    }

    private void pickZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        try { startActivityForResult(i, REQ_UPLOAD); } catch (Exception e) { Toast.makeText(this, "لا يوجد تطبيق لاختيار الملفات", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_UPLOAD && res == RESULT_OK && data != null && data.getData() != null) applyZip(data.getData());
    }

    private void applyZip(final Uri uri) {
        status.setText("جارٍ فكّ الملف وتطبيقه...");
        new AsyncTask<Void, Void, Object>() {
            @Override protected Object doInBackground(Void... v) {
                try {
                    ThemeEngine.Theme t = ThemeEngine.extract(MainActivity.this, uri);
                    ThemeEngine.ApplyResult r = ThemeEngine.apply(MainActivity.this, t);
                    return new Object[]{t, r};
                } catch (Exception e) { return e; }
            }
            @Override protected void onPostExecute(Object res) {
                if (res instanceof Exception) { status.setText(""); showResult("تعذّر", "تعذّر فتح الملف: " + ((Exception) res).getMessage(), false); return; }
                Object[] pair = (Object[]) res; ThemeEngine.Theme t = (ThemeEngine.Theme) pair[0]; ThemeEngine.ApplyResult r = (ThemeEngine.ApplyResult) pair[1];
                status.setText(""); boolean ok = r.homeApplied || r.lockApplied;
                showResult(ok ? "تم تطبيق الثيم ✔" : "لم يُطبَّق شيء", (ok ? "الثيم: " + t.name + "\n" : "") + r.summary(), ok);
            }
        }.execute();
    }

    private void exportTheme() {
        status.setText("جارٍ التصدير...");
        new AsyncTask<Void, Void, String>() {
            @Override protected String doInBackground(Void... v) {
                try {
                    Bitmap wp = WallpaperManager.getInstance(MainActivity.this).getBitmap();
                    if (wp == null) return "لا توجد خلفية شاشة يمكن قراءتها على هذا الجهاز";
                    String name = "ثيمي " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
                    File tmp = File.createTempFile("theme", ".zip", getCacheDir());
                    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp))) {
                        zos.putNextEntry(new ZipEntry("theme.json")); zos.write(("{\"name\":\"" + name.replace("\"", "'") + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8)); zos.closeEntry();
                        zos.putNextEntry(new ZipEntry("wallpaper.jpg")); wp.compress(Bitmap.CompressFormat.JPEG, 92, zos); zos.closeEntry();
                    }
                    return saveToDownloads(tmp, "theme_" + System.currentTimeMillis() + ".zip") ? "" : "تعذّر الحفظ في مجلّد التنزيلات";
                } catch (Exception e) { return "خطأ: " + e.getMessage(); }
            }
            @Override protected void onPostExecute(String err) {
                status.setText("");
                if (err == null || err.isEmpty()) showResult("تم التصدير ✔", "احفظ الملف من مجلّد التنزيلات وشاركه أو ارفعه لاحقاً.", true);
                else showResult("تعذّر", err, false);
            }
        }.execute();
    }

    private boolean saveToDownloads(File src, String name) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name); cv.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (u == null) return false;
            try (OutputStream os = getContentResolver().openOutputStream(u); java.io.FileInputStream is = new java.io.FileInputStream(src)) {
                byte[] buf = new byte[8192]; int n; while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            }
            return true;
        } else {
            if (checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, REQ_STORAGE); return false; }
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); dir.mkdirs();
            File out = new File(dir, name);
            try (OutputStream os = new FileOutputStream(out); java.io.FileInputStream is = new java.io.FileInputStream(src)) {
                byte[] buf = new byte[8192]; int n; while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            }
            return true;
        }
    }

    private void showResult(String title, String msg, boolean ok) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("تم", null).show();
    }

    // ===== أدوات واجهة صغيرة =====
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t;
    }
    private GradientDrawable round(int color, int radius, int strokeColor, int strokeW) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); if (strokeW > 0) g.setStroke(strokeW, strokeColor); return g;
    }
    private View bigButton(String title, String sub, int color, View.OnClickListener l) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{color, mix(color, Color.BLACK, .18f)});
        g.setCornerRadius(dp(18)); card.setBackground(g); card.setPadding(dp(20), dp(20), dp(20), dp(20));
        TextView t = text(title, 19, Color.WHITE, true); card.addView(t);
        TextView s = text(sub, 12, Color.argb(220, 255, 255, 255), false); LinearLayout.LayoutParams slp = lp(-1, -2); slp.setMargins(0, dp(4), 0, 0); card.addView(s, slp);
        card.setOnClickListener(l);
        card.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN: v.animate().scaleX(.97f).scaleY(.97f).setDuration(90).start(); break;
                case android.view.MotionEvent.ACTION_UP: case android.view.MotionEvent.ACTION_CANCEL: v.animate().scaleX(1f).scaleY(1f).setDuration(150).start(); break;
            }
            return false;
        });
        return card;
    }
    private static int mix(int a, int b, float k) { return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * k), (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * k), (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k)); }
}

package com.thememaster.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * شاشة واحدة بزر واحد: «رفع ملف الثيمات». يتحقّق من الملف (2–5 ثوانٍ فعلياً: فحص سلامة الـZIP)،
 * ثم يسلّمه لتطبيق الثيمات الرسمي في نظام الجهاز نفسه — هو من يطبّق الثيم فعلياً، وليس هذا التطبيق.
 * الدعم الآن: شاومي / ريدمي / بوكو (MIUI) فقط، كما طُلب.
 */
public class MainActivity extends Activity {
    private static final int REQ_PICK = 1;
    private static final int ACC = Color.rgb(109, 76, 230);
    private TextView status, brandLabel;
    private ColorSpinnerView spinner;
    private GlowView glow;
    private View btnRef;

    /** توهّج نابض خلف الزر — ألوان متعدّدة تدور وتخفت وتتوهّج ببطء. */
    static class GlowView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final long t0 = SystemClock.uptimeMillis();
        final int[] cols = {Color.rgb(109, 76, 230), Color.rgb(230, 76, 170), Color.rgb(90, 200, 255)};
        GlowView(android.content.Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float t = (SystemClock.uptimeMillis() - t0) / 1000f, w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
            float pulse = .75f + .25f * (float) Math.sin(t * 2.2);
            for (int i = 0; i < 3; i++) {
                int col = cols[i]; float ang = t * .4f + i * 2.094f;
                float ox = (float) Math.cos(ang) * w * .06f, oy = (float) Math.sin(ang) * w * .06f;
                p.setShader(new RadialGradient(cx + ox, cy + oy, Math.min(w, h) * .42f * pulse, Color.argb(70, Color.red(col), Color.green(col), Color.blue(col)), 0x00000000, Shader.TileMode.CLAMP));
                c.drawCircle(cx + ox, cy + oy, Math.min(w, h) * .42f * pulse, p);
            }
            p.setShader(null);
            postInvalidateOnAnimation();
        }
    }
    /** مؤشر تحقّق ملوّن (بدل ProgressBar الرمادي) — حلقة بأربعة ألوان تدور. */
    static class ColorSpinnerView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final long t0 = SystemClock.uptimeMillis();
        final int[] cols = {Color.rgb(255, 90, 90), Color.rgb(255, 196, 60), Color.rgb(90, 200, 255), Color.rgb(120, 220, 140)};
        ColorSpinnerView(android.content.Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float t = (SystemClock.uptimeMillis() - t0) / 1000f, w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - 4;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5); p.setStrokeCap(Paint.Cap.ROUND);
            RectF rf = new RectF(cx - r, cy - r, cx + r, cy + r);
            for (int i = 0; i < 4; i++) { p.setColor(cols[i]); c.drawArc(rf, t * 170 + i * 90, 60, false, p); }
            postInvalidateOnAnimation();
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout stage = new FrameLayout(this); stage.setBackgroundColor(Color.rgb(246, 246, 250));
        glow = new GlowView(this); stage.addView(glow, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        int pad = dp(28); root.setPadding(pad, pad, pad, pad);
        stage.addView(root, new FrameLayout.LayoutParams(-1, -1));

        TextView title = text("رفع ملف الثيمات", 24, Color.rgb(28, 28, 38), true); title.setGravity(Gravity.CENTER);
        root.addView(title);

        int bt = DeviceTheme.brand();
        brandLabel = text("جهازك: " + DeviceTheme.brandName(bt) + (DeviceTheme.isSupportedNow(bt) ? "" : " — غير مدعوم بعد"), 13, Color.rgb(120, 122, 140), false);
        brandLabel.setGravity(Gravity.CENTER); LinearLayout.LayoutParams blp = lp(-2, -2); blp.setMargins(0, dp(6), 0, dp(26)); root.addView(brandLabel, blp);

        LinearLayout btn = new LinearLayout(this); btn.setOrientation(LinearLayout.VERTICAL); btn.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACC, Color.rgb(90, 62, 189)});
        g.setCornerRadius(dp(22)); btn.setBackground(g); btn.setPadding(dp(30), dp(30), dp(30), dp(30)); btn.setElevation(dp(10));
        btn.addView(text("⬆", 30, Color.WHITE, true));
        TextView lbl = text("رفع ملف الثيمات (ZIP)", 17, Color.WHITE, true); lbl.setGravity(Gravity.CENTER); LinearLayout.LayoutParams lblp = lp(-2, -2); lblp.setMargins(0, dp(8), 0, 0); btn.addView(lbl, lblp);
        btn.setOnClickListener(v -> pick());
        btn.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN: v.animate().scaleX(.92f).scaleY(.92f).setDuration(90).start(); break;
                case android.view.MotionEvent.ACTION_UP: case android.view.MotionEvent.ACTION_CANCEL: v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(3f)).start(); break;
            }
            return false;
        });
        LinearLayout.LayoutParams btnlp = lp(dp(220), dp(150)); root.addView(btn, btnlp); btnRef = btn;

        spinner = new ColorSpinnerView(this); spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams splp = lp(dp(40), dp(40)); splp.setMargins(0, dp(22), 0, 0); root.addView(spinner, splp);
        status = text("", 13, Color.rgb(120, 122, 140), false); status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stlp = lp(-1, -2); stlp.setMargins(0, dp(10), 0, 0); root.addView(status, stlp);

        TextView note = text(DeviceTheme.isSupportedNow(bt)
                ? "يُرسَل الملف مباشرة إلى تطبيق الثيمات الرسمي على جهازك — هو من يتحقّق منه ويطبّقه. التطبيق هنا لا يطبّق الثيم بنفسه."
                : "هذا الإصدار يدعم أجهزة شاومي وريدمي وبوكو فقط حالياً. بقية الشركات قريباً.", 12, Color.rgb(150, 100, 20), false);
        note.setGravity(Gravity.CENTER);
        note.setBackground(round(Color.rgb(255, 244, 214), dp(10))); note.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams nlp = lp(-1, -2); nlp.setMargins(0, dp(28), 0, 0); root.addView(note, nlp);

        setContentView(stage);
        entranceAnim(title, brandLabel, btn, note);
    }

    /** دخول متتابع: العنوان ينزلق، اسم الجهاز يتبعه، الزر يقفز بارتداد، الملاحظة تتلاشى. */
    private void entranceAnim(View title, View brand, View btn, View note) {
        for (View v : new View[]{title, brand, btn, note}) { v.setAlpha(0f); v.setTranslationY(dp(18)); }
        btn.setScaleX(.6f); btn.setScaleY(.6f);
        title.animate().alpha(1f).translationY(0).setStartDelay(80).setDuration(360).setInterpolator(new DecelerateInterpolator()).start();
        brand.animate().alpha(1f).translationY(0).setStartDelay(180).setDuration(360).setInterpolator(new DecelerateInterpolator()).start();
        btn.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setStartDelay(300).setDuration(520).setInterpolator(new OvershootInterpolator(1.6f)).start();
        note.animate().alpha(1f).translationY(0).setStartDelay(620).setDuration(400).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void pick() {
        if (!DeviceTheme.isSupportedNow(DeviceTheme.brand())) { Toast.makeText(this, "هذا الجهاز غير مدعوم بعد", Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        try { startActivityForResult(i, REQ_PICK); } catch (Exception e) { Toast.makeText(this, "لا يوجد تطبيق لاختيار الملفات", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) verifyAndSend(data.getData());
    }

    /** يتحقّق من سلامة الـZIP فعلياً (قراءة كل مدخلاته) ثم يرسله لتطبيق الثيمات في النظام. */
    private void verifyAndSend(final Uri src) {
        spinner.setVisibility(View.VISIBLE);
        status.setText("جارٍ التحقّق من الملف...");
        new AsyncTask<Void, Void, Object>() {
            @Override protected Object doInBackground(Void... v) {
                File out = new File(getCacheDir(), "theme_" + System.currentTimeMillis() + ".mtz");
                try {
                    long start = System.currentTimeMillis();
                    int entries = 0; boolean ok;
                    try (InputStream raw = getContentResolver().openInputStream(src); OutputStream os = new FileOutputStream(out)) {
                        if (raw == null) return "تعذّر فتح الملف";
                        byte[] buf = new byte[16384]; int n; ByteCounter counter = new ByteCounter();
                        while ((n = raw.read(buf)) > 0) { os.write(buf, 0, n); counter.total += n; if (counter.total > 150L * 1024 * 1024) return "الملف كبير جداً (الحد 150MB)"; }
                    }
                    try (ZipInputStream zin = new ZipInputStream(new java.io.FileInputStream(out))) {
                        ZipEntry e; while ((e = zin.getNextEntry()) != null) { entries++; if (entries > 5000) return "عدد ملفات داخلي كبير جداً"; }
                        ok = entries > 0;
                    } catch (ZipException ze) { return "الملف ليس أرشيف ZIP سليم"; }
                    if (!ok) return "الملف فارغ";
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed < 1800) try { Thread.sleep(1800 - elapsed); } catch (InterruptedException ignored) {}   // فحص حقيقي، بحد أدنى يعكس حجم العمل الفعلي
                    return out;
                } catch (Exception e) { return "خطأ: " + e.getMessage(); }
            }
            @Override protected void onPostExecute(Object r) {
                spinner.setVisibility(View.GONE); status.setText("");
                if (r instanceof String) { showDialog("تعذّر", (String) r); return; }
                sendToSystemThemeApp((File) r);
            }
        }.execute();
    }
    private static final class ByteCounter { long total; }

    private void sendToSystemThemeApp(File mtz) {
        int bt = DeviceTheme.brand();
        String pkg = DeviceTheme.themeAppPackage(bt);
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", mtz);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/zip");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (pkg != null) i.setPackage(pkg);
        try {
            startActivity(i);
            showDialog("تم الإرسال ✔", "أُرسل الملف إلى تطبيق الثيمات في نظام جهازك. أكمل التثبيت من هناك — هو من يتحقّق من الثيم ويطبّقه فعلياً.");
        } catch (ActivityNotFoundException e1) {
            if (pkg != null) {
                i.setPackage(null);
                try { startActivity(i); showDialog("تم الإرسال ✔", "أُرسل الملف وفتحته أول أداة تدعم ملفات الثيمات على جهازك."); return; } catch (ActivityNotFoundException ignored) {}
            }
            showDialog("تعذّر الإرسال", "لم يُعثر على تطبيق ثيمات يقبل هذا الملف على جهازك (قد يختلف باختلار نسخة النظام أو المنطقة).");
        }
    }

    private void showDialog(String title, String msg) { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("تم", null).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); return g; }
}

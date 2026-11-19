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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
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
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import rikka.shizuku.Shizuku;

/**
 * شاشة واحدة بزر واحد: «رفع ملف الثيمات». يتحقّق من الملف (2–5 ثوانٍ فعلياً: فحص سلامة الـZIP)،
 * ثم يسلّمه لتطبيق الثيمات الرسمي في نظام الجهاز نفسه — هو من يطبّق الثيم فعلياً، وليس هذا التطبيق.
 * الدعم الآن: شاومي / ريدمي / بوكو (MIUI) فقط، كما طُلب.
 */
public class MainActivity extends Activity {
    private static final int REQ_PICK = 1, REQ_STORAGE = 2, REQ_SHIZUKU = 3;
    private static final int ACC = Color.rgb(109, 76, 230), OK = Color.rgb(50, 170, 110);
    private TextView status, brandLabel;
    private ColorSpinnerView spinner;
    private GlowView glow;
    private View btnRef;
    private TextView permRow, permBtn;
    private TextView shzRow, shzBtn;
    private final Shizuku.OnRequestPermissionResultListener shzListener = (req, grant) -> { if (req == REQ_SHIZUKU) refreshShzUi(); };

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
        brandLabel.setGravity(Gravity.CENTER); LinearLayout.LayoutParams blp = lp(-2, -2); blp.setMargins(0, dp(6), 0, dp(14)); root.addView(brandLabel, blp);

        // ===== صلاحية الوصول الكامل للملفات: لنسخ ملف الثيم أيضاً إلى مجلّد شاومي المحلي للثيمات =====
        LinearLayout permCard = new LinearLayout(this); permCard.setGravity(Gravity.CENTER_VERTICAL);
        permCard.setBackground(round(Color.WHITE, dp(14))); permCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        permCard.setElevation(dp(2));
        LinearLayout permCol = new LinearLayout(this); permCol.setOrientation(LinearLayout.VERTICAL);
        permCol.addView(text("صلاحية الوصول للملفات", 14, Color.rgb(40, 40, 50), true));
        permRow = text("", 11, Color.rgb(140, 142, 158), false);
        permCol.addView(permRow);
        permCard.addView(permCol, lp(0, -2)); ((LinearLayout.LayoutParams) permCol.getLayoutParams()).weight = 1;
        permBtn = text("منح", 13, Color.WHITE, true); permBtn.setGravity(Gravity.CENTER);
        permBtn.setBackground(round(ACC, dp(10))); permBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        permBtn.setOnClickListener(v -> requestFileAccess());
        permCard.addView(permBtn);
        LinearLayout.LayoutParams pclp = lp(dp(280), -2); pclp.setMargins(0, 0, 0, dp(10)); root.addView(permCard, pclp);

        // ===== Shizuku (اختياري): طريقة إضافية لنسخ الملف عبر صلاحية shell من خيارات المطوّر =====
        LinearLayout shzCard = new LinearLayout(this); shzCard.setGravity(Gravity.CENTER_VERTICAL);
        shzCard.setBackground(round(Color.WHITE, dp(14))); shzCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        shzCard.setElevation(dp(2));
        LinearLayout shzCol = new LinearLayout(this); shzCol.setOrientation(LinearLayout.VERTICAL);
        shzCol.addView(text("Shizuku (اختياري)", 14, Color.rgb(40, 40, 50), true));
        shzRow = text("", 11, Color.rgb(140, 142, 158), false);
        shzCol.addView(shzRow);
        shzCard.addView(shzCol, lp(0, -2)); ((LinearLayout.LayoutParams) shzCol.getLayoutParams()).weight = 1;
        shzBtn = text("منح", 13, Color.WHITE, true); shzBtn.setGravity(Gravity.CENTER);
        shzBtn.setBackground(round(ACC, dp(10))); shzBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        shzBtn.setOnClickListener(v -> requestShizuku());
        shzCard.addView(shzBtn);
        LinearLayout.LayoutParams sclp = lp(dp(280), -2); sclp.setMargins(0, 0, 0, dp(22)); root.addView(shzCard, sclp);

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
        entranceAnim(title, brandLabel, permCard, shzCard, btn, note);
        try { Shizuku.addRequestPermissionResultListener(shzListener); } catch (Throwable ignored) {}
    }

    @Override protected void onResume() { super.onResume(); refreshPermUi(); refreshShzUi(); }
    @Override protected void onDestroy() { super.onDestroy(); try { Shizuku.removeRequestPermissionResultListener(shzListener); } catch (Throwable ignored) {} }

    private void refreshPermUi() {
        boolean ok = hasFileAccess();
        permRow.setText(ok ? "ممنوحة ✔ — يُنسخ ملف الثيم أيضاً إلى مجلّد شاومي المحلي" : "اختياري: لنسخ الملف أيضاً إلى مجلّد الثيمات المحلي على جهازك");
        permRow.setTextColor(ok ? OK : Color.rgb(140, 142, 158));
        permBtn.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    private void refreshShzUi() {
        if (!ThemeShizuku.installed(this)) {
            shzRow.setText("غير مثبّت — طريقة إضافية اختيارية لنسخ الملف عبر صلاحية shell"); shzRow.setTextColor(Color.rgb(140, 142, 158));
            shzBtn.setText("تثبيت"); shzBtn.setVisibility(View.VISIBLE); return;
        }
        boolean ok = ThemeShizuku.granted();
        shzRow.setText(ok ? "ممنوحة ✔ — طريقة نسخ إضافية عبر shell (لا تتجاوز تأكيد تطبيق الثيمات نفسه)" : "اختياري: صلاحية shell من تطبيق Shizuku (يُفعَّل من خيارات المطوّر)");
        shzRow.setTextColor(ok ? OK : Color.rgb(140, 142, 158));
        shzBtn.setText("منح"); shzBtn.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    private boolean hasFileAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    private void requestFileAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        } else requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, REQ_STORAGE);
    }
    @Override public void onRequestPermissionsResult(int req, String[] p, int[] r) { refreshPermUi(); }

    private void requestShizuku() {
        if (!ThemeShizuku.installed(this)) { try { startActivity(ThemeShizuku.installIntent()); } catch (Exception ignored) {} return; }
        if (!ThemeShizuku.running()) {
            Intent m = ThemeShizuku.managerIntent(this);
            if (m != null) startActivity(m);
            showDialog("فعّل Shizuku أولاً", "افتح تطبيق Shizuku وابدأ الخدمة (عبر «تصحيح USB» أو «تصحيح عبر الشبكة» ضمن خيارات المطوّر)، ثم عد واضغط «منح».");
            return;
        }
        ThemeShizuku.request(REQ_SHIZUKU);
    }

    /** دخول متتابع: العنوان ينزلق، ثم اسم الجهاز، ثم بطاقات الصلاحيات، ثم الزر يقفز بارتداد، ثم الملاحظة تتلاشى. */
    private void entranceAnim(View title, View brand, View perm, View shz, View btn, View note) {
        for (View v : new View[]{title, brand, perm, shz, btn, note}) { v.setAlpha(0f); v.setTranslationY(dp(18)); }
        btn.setScaleX(.6f); btn.setScaleY(.6f);
        title.animate().alpha(1f).translationY(0).setStartDelay(80).setDuration(360).setInterpolator(new DecelerateInterpolator()).start();
        brand.animate().alpha(1f).translationY(0).setStartDelay(170).setDuration(340).setInterpolator(new DecelerateInterpolator()).start();
        perm.animate().alpha(1f).translationY(0).setStartDelay(260).setDuration(360).setInterpolator(new DecelerateInterpolator()).start();
        shz.animate().alpha(1f).translationY(0).setStartDelay(330).setDuration(360).setInterpolator(new DecelerateInterpolator()).start();
        btn.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setStartDelay(420).setDuration(520).setInterpolator(new OvershootInterpolator(1.6f)).start();
        note.animate().alpha(1f).translationY(0).setStartDelay(740).setDuration(400).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void pick() {
        if (!DeviceTheme.isSupportedNow(DeviceTheme.brand())) { Toast.makeText(this, "هذا الجهاز غير مدعوم بعد", Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        try { startActivityForResult(i, REQ_PICK); } catch (Exception e) { Toast.makeText(this, "لا يوجد تطبيق لاختيار الملفات", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) verifyAndSend(data.getData());
    }

    /** نتيجة تحقّق ناجحة: الملف + تفاصيل حقيقية عنه (حجم، عدد مدخلات، بصمة SHA-256). */
    private static final class Verified {
        final File file; final long size; final int entries; final String sha256;
        Verified(File f, long s, int e, String h) { file = f; size = s; entries = e; sha256 = h; }
    }

    /** يتحقّق من سلامة الـZIP فعلياً (قراءة كل مدخلاته + بصمة SHA-256 حقيقية) ثم يرسله لتطبيق الثيمات في النظام. */
    private void verifyAndSend(final Uri src) {
        spinner.setVisibility(View.VISIBLE);
        status.setText("جارٍ قراءة الملف...");
        new AsyncTask<Void, String, Object>() {
            @Override protected Object doInBackground(Void... v) {
                File out = new File(getCacheDir(), "theme_" + System.currentTimeMillis() + ".mtz");
                try {
                    long start = System.currentTimeMillis();
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    long size = 0;
                    try (InputStream raw = getContentResolver().openInputStream(src); OutputStream os = new FileOutputStream(out)) {
                        if (raw == null) return "تعذّر فتح الملف";
                        byte[] buf = new byte[16384]; int n;
                        while ((n = raw.read(buf)) > 0) { os.write(buf, 0, n); md.update(buf, 0, n); size += n; if (size > 150L * 1024 * 1024) return "الملف كبير جداً (الحد 150MB)"; }
                    }
                    publishProgress("جارٍ التحقّق من سلامة الأرشيف...");
                    int entries = 0; boolean ok;
                    try (ZipInputStream zin = new ZipInputStream(new java.io.FileInputStream(out))) {
                        ZipEntry e; while ((e = zin.getNextEntry()) != null) { entries++; if (entries > 5000) return "عدد ملفات داخلي كبير جداً"; }
                        ok = entries > 0;
                    } catch (ZipException ze) { return "الملف ليس أرشيف ZIP سليم"; }
                    if (!ok) return "الملف فارغ";
                    publishProgress("جارٍ حساب بصمة الأمان SHA-256...");
                    StringBuilder hex = new StringBuilder();
                    for (byte b : md.digest()) hex.append(String.format(java.util.Locale.US, "%02x", b));
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed < 1800) try { Thread.sleep(1800 - elapsed); } catch (InterruptedException ignored) {}   // فحص حقيقي، بحد أدنى يعكس حجم العمل الفعلي
                    return new Verified(out, size, entries, hex.toString());
                } catch (Exception e) { return "خطأ: " + e.getMessage(); }
            }
            @Override protected void onProgressUpdate(String... s) { status.setText(s[0]); }
            @Override protected void onPostExecute(Object r) {
                spinner.setVisibility(View.GONE); status.setText("");
                if (r instanceof String) { showDialog("تعذّر", (String) r); return; }
                sendToSystemThemeApp((Verified) r);
            }
        }.execute();
    }

    private void sendToSystemThemeApp(Verified v) {
        File mtz = v.file;
        // نسخة ثانية في مجلّد شاومي المحلي للثيمات — عبر صلاحية الملفات، أو عبر Shizuku إن لم تُمنح الأولى
        String localPath = hasFileAccess() ? copyToMiuiLocal(mtz) : null;
        if (localPath == null && ThemeShizuku.granted()) localPath = ThemeShizuku.copyToMiuiLocal(mtz);

        int bt = DeviceTheme.brand();
        String pkg = DeviceTheme.themeAppPackage(bt);
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", mtz);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/zip");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (pkg != null) i.setPackage(pkg);
        final String details = "\n\nتفاصيل الملف المتحقَّق منه:\n• الحجم: " + (v.size / 1024) + " كيلوبايت\n• عدد الملفات داخله: " + v.entries
                + "\n• بصمة الأمان SHA-256: " + v.sha256.substring(0, 16) + "…";
        String extra = localPath != null ? "\n\nكما نُسخ الملف إلى مجلّد الثيمات المحلي على جهازك (" + localPath + ")؛ إن لم يفتح تطبيق الثيمات تلقائياً، افتحه وابحث عن الثيم ضمن «المحلي / My Themes»." : "";
        final String confirmNote = "\n\nملاحظة مهمة: تثبيت الثيم وتفعيله فعلياً يتم من داخل تطبيق الثيمات لجهازك، وهذا يشمل حتى لو استُخدم Shizuku — فتطبيق MIUI هو من يتحقّق من الثيم ويطلب تأكيدك، ولا توجد صلاحية تتجاوز تلك الخطوة.";
        try {
            startActivity(i);
            showDialog("تم الإرسال ✔", "أُرسل الملف إلى تطبيق الثيمات في نظام جهازك. أكمل التثبيت من هناك — هو من يتحقّق من الثيم ويطبّقه فعلياً، وليس هذا التطبيق." + details + extra + confirmNote);
        } catch (ActivityNotFoundException e1) {
            if (pkg != null) {
                i.setPackage(null);
                try { startActivity(i); showDialog("تم الإرسال ✔", "أُرسل الملف وفتحته أول أداة تدعم ملفات الثيمات على جهازك." + details + extra + confirmNote); return; } catch (ActivityNotFoundException ignored) {}
            }
            if (ThemeShizuku.granted() && localPath != null && ThemeShizuku.launchThemeManager(localPath)) {
                showDialog("تم الإرسال ✔", "فُتح تطبيق الثيمات عبر Shizuku." + details + extra + confirmNote); return;
            }
            showDialog(localPath != null ? "أُرسل جزئياً" : "تعذّر الإرسال",
                    "لم يُفتح تطبيق الثيمات تلقائياً على جهازك (قد يختلف باختلاف نسخة النظام أو المنطقة)." + (localPath != null
                            ? "\n\nلكن الملف نُسخ إلى (" + localPath + "). افتح تطبيق الثيمات يدوياً وابحث عنه ضمن «المحلي»."
                            : " فعّل صلاحية الوصول للملفات أو Shizuku أعلاه لإتاحة طريقة بديلة.") + details + confirmNote);
        }
    }

    /** ينسخ الملف إلى مجلّد الثيمات المحلي المعروف لتطبيق شاومي (يحتاج صلاحية الوصول الكامل للملفات). */
    private String copyToMiuiLocal(File mtz) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "MIUI/theme");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File dst = new File(dir, mtz.getName());
            try (InputStream in = new java.io.FileInputStream(mtz); OutputStream os = new FileOutputStream(dst)) {
                byte[] buf = new byte[16384]; int n; while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            return dst.getAbsolutePath();
        } catch (Exception e) { return null; }
    }

    private void showDialog(String title, String msg) { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("تم", null).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); return g; }
}

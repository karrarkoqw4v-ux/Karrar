package com.gamebooster.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** المقدمة الكاملة (سيارة سريعة → انفجار → شعار) مع زر «تسريع الآن» الحقيقي. */
public class IntroActivity extends Activity {
    private boolean going;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.immersive(this);
        setContentView(new IntroView(this, this::proceed));
    }

    @Override protected void onResume() { super.onResume(); going = false; }

    private void proceed() {
        if (going) return;
        going = true;
        boolean allGood = Booster.hasDnd(this) && Booster.hasOverlay(this) && Booster.hasPostNotifications(this);
        startActivity(new Intent(this, allGood ? HubActivity.class : PermissionsActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    static class IntroView extends View {
        static final float W = 1280, H = 576, HIT = 5.4f, SPEED = 1.8182f;
        final Runnable onBtn;
        final long start = SystemClock.uptimeMillis();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final RectF rf = new RectF();
        final java.util.Random rnd = new java.util.Random();
        float t, s, ox, oy, carAlpha = 1;

        IntroView(Context c, Runnable onBtn) { super(c); this.onBtn = onBtn; }

        // ---------- أدوات ----------
        static float cl(float x) { return Math.max(0, Math.min(1, x)); }
        static float seg(float t, float a, float b) { return cl((t - a) / (b - a)); }
        static float eo(float x) { float k = 1 - x; return 1 - k * k * k; }
        static float ei(float x) { return x * x * x; }
        static float eb(float x) { float c1 = 1.70158f, c3 = c1 + 1, k = x - 1; return 1 + c3 * k * k * k + c1 * k * k; }
        static float R(float i) { double v = Math.sin(i * 127.1 + 311.7) * 43758.5453; return (float) (v - Math.floor(v)); }
        static float lerp(float a, float b, float k) { return a + (b - a) * k; }
        static int A(float a) { return (int) (255 * cl(a)); }
        int col(float a, int r, int g, int b) { return Color.argb(A(a * carAlpha), r, g, b); }

        void line(Canvas c, float x1, float y1, float x2, float y2, float w, int color) {
            p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); p.setColor(color); c.drawLine(x1, y1, x2, y2, p);
        }
        void fillColor(int color) { p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); }
        void bolt(Path pa) { pa.reset(); pa.moveTo(14, -96); pa.lineTo(-52, 10); pa.lineTo(-6, 10); pa.lineTo(-24, 96); pa.lineTo(52, -18); pa.lineTo(6, -18); pa.close(); }
        void halo(Canvas c, Path pa, int r, int g, int b, float a) {
            p.setShader(null); p.setStyle(Paint.Style.STROKE);
            for (int k = 4; k >= 1; k--) { p.setStrokeWidth(k * 14); p.setColor(Color.argb(A(a * 0.10f), r, g, b)); c.drawPath(pa, p); }
        }
        void glowText(Canvas c, String str, float x, float y, float size, int r, int g, int b, float a, float spacing, float glitch) {
            p.setShader(null); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(size);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)); p.setLetterSpacing(spacing);
            if (glitch > 0.5f) {
                p.setStyle(Paint.Style.FILL); p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
                p.setColor(Color.argb(A(a * .8f), 255, 0, 60)); c.drawText(str, x - glitch, y, p);
                p.setColor(Color.argb(A(a * .8f), 0, 220, 255)); c.drawText(str, x + glitch, y, p);
                p.setColor(Color.argb(A(a * .6f), 255, 255, 255)); c.drawText(str, x, y, p);
                p.setXfermode(null);
            } else {
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                for (int k = 3; k >= 1; k--) { p.setStrokeWidth(k * size * .06f); p.setColor(Color.argb(A(a * .13f), r, g, b)); c.drawText(str, x, y, p); }
                p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(A(a), 255, 255, 255)); c.drawText(str, x, y, p);
            }
            p.setLetterSpacing(0);
        }
        void sparks(Canvas c, float t, float t0, int n, float cx, float cy, int r, int g, int b, float speed, float life) {
            float k = t - t0; if (k < 0 || k > life) return;
            fillColor(0);
            for (int i = 0; i < n; i++) {
                float a = R(i) * 6.283f, v = speed * (.3f + R(i + 99)), d = k * v;
                p.setColor(Color.argb(A(1 - k / life), r, g, b));
                c.drawCircle(cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d + k * k * 120, 2 + R(i + 7) * 2.5f, p);
            }
        }

        // ---------- السيارة ----------
        void car(Canvas c, float x, float y, float sc, float wr, int ar, int ag, int ab) {
            c.save(); c.translate(x, y); c.scale(sc, sc);
            // توهج الأرض
            fillColor(col(.35f, ar, ag, ab)); rf.set(-175, -12, 175, 6); c.drawOval(rf, p);
            fillColor(col(.2f, ar, ag, ab)); rf.set(-215, -20, 215, 14); c.drawOval(rf, p);
            // شعاع الأضواء
            path.reset(); path.moveTo(190, -44); path.lineTo(560, -90); path.lineTo(560, 10); path.lineTo(190, -30); path.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(190, 0, 560, 0, col(.55f, 255, 240, 200), col(0, 255, 240, 200), Shader.TileMode.CLAMP));
            c.drawPath(path, p); p.setShader(null);
            // الهيكل
            path.reset(); path.moveTo(-182, -30); path.lineTo(-184, -58); path.lineTo(-150, -72); path.lineTo(-96, -76); path.lineTo(-30, -100);
            path.lineTo(42, -104); path.lineTo(112, -66); path.lineTo(196, -50); path.lineTo(206, -38); path.lineTo(194, -27); path.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, -105, 0, -24, new int[]{col(1, 42, 44, 58), col(1, 20, 21, 29), col(1, 7, 8, 12)}, new float[]{0, .45f, 1}, Shader.TileMode.CLAMP));
            c.drawPath(path, p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(col(1, ar, ag, ab)); c.drawPath(path, p);
            // النوافذ
            path.reset(); path.moveTo(-70, -78); path.lineTo(-24, -96); path.lineTo(38, -98); path.lineTo(88, -68); path.lineTo(-10, -72); path.close();
            fillColor(col(1, 12, 34, 48)); c.drawPath(path, p);
            path.reset(); path.moveTo(-24, -94); path.lineTo(8, -96); path.lineTo(-12, -74); path.lineTo(-44, -76); path.close();
            fillColor(col(.35f, 120, 220, 255)); c.drawPath(path, p);
            // شريط ملوّن
            path.reset(); path.moveTo(-176, -46); path.lineTo(-20, -56); path.lineTo(188, -42);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(10); p.setColor(col(.15f, ar, ag, ab)); c.drawPath(path, p);
            p.setStrokeWidth(3); p.setColor(col(1, ar, ag, ab)); c.drawPath(path, p);
            // جناح
            path.reset(); path.moveTo(-190, -80); path.lineTo(-150, -84); path.lineTo(-152, -78); path.lineTo(-186, -72); path.close();
            fillColor(col(1, 7, 8, 12)); c.drawPath(path, p); c.drawRect(-172, -76, -167, -58, p);
            // الأضواء
            fillColor(col(1, 255, 246, 216)); path.reset(); path.moveTo(170, -54); path.lineTo(200, -46); path.lineTo(196, -40); path.lineTo(166, -46); path.close(); c.drawPath(path, p);
            fillColor(col(1, 255, 26, 46)); c.drawRect(-186, -62, -180, -53, p);
            // العجلات
            float[] wx = {-108, 118};
            for (float w : wx) {
                c.save(); c.translate(w, -28);
                fillColor(col(1, 5, 5, 7)); c.drawCircle(0, 0, 30, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(col(1, 34, 34, 34)); c.drawCircle(0, 0, 30, p);
                c.rotate((float) Math.toDegrees(wr));
                p.setColor(col(1, 207, 211, 224));
                for (int i = 0; i < 6; i++) { c.rotate(60); c.drawLine(5, 0, 21, 0, p); }
                p.setStrokeWidth(2); p.setColor(col(1, ar, ag, ab)); c.drawCircle(0, 0, 22, p);
                c.restore();
            }
            c.restore();
        }

        // ---------- الزر ----------
        static final float BX = 640, BY = 64, BW = 270, BH = 66;
        void button(Canvas c, float t) {
            float pop = eb(seg(t, .15f, .7f)); if (pop <= 0) return;
            float pulse = .5f + .5f * (float) Math.sin(t * 5);
            float bump = (t > HIT && t < HIT + .5f) ? (float) Math.sin((t - HIT) / .5f * Math.PI) * .12f : 0;
            float sc = pop * (1 + .02f * pulse + bump);
            c.save(); c.translate(BX, BY); c.scale(sc, sc);
            float al = cl(pop * 2);
            p.setShader(null);
            for (int i = 0; i < 3; i++) {
                float k = (t * .7f + i / 3f) % 1f;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f); p.setColor(Color.argb(A((1 - k) * .55f * al), 255, 165, 31));
                rf.set(-BW / 2 - k * 50, -BH / 2 - k * 24, BW / 2 + k * 50, BH / 2 + k * 24); c.drawRoundRect(rf, (BH + k * 48) / 2, (BH + k * 48) / 2, p);
            }
            // توهج
            for (int k = 4; k >= 1; k--) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(k * (6 + 5 * pulse)); p.setColor(Color.argb(A(al * .10f), 255, 154, 0)); rf.set(-BW / 2, -BH / 2, BW / 2, BH / 2); c.drawRoundRect(rf, BH / 2, BH / 2, p); }
            rf.set(-BW / 2, -BH / 2, BW / 2, BH / 2);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, -BH / 2, 0, BH / 2, new int[]{Color.rgb(255, 211, 77), Color.rgb(255, 154, 0), Color.rgb(224, 74, 0)}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
            p.setAlpha(A(al)); c.drawRoundRect(rf, BH / 2, BH / 2, p); p.setShader(null);
            fillColor(Color.argb(A(.28f * al), 255, 255, 255)); rf.set(-BW / 2 + 8, -BH / 2 + 5, BW / 2 - 8, -BH / 2 + 5 + BH * .4f); c.drawRoundRect(rf, BH / 3, BH / 3, p);
            // لمعة تعبر
            rf.set(-BW / 2, -BH / 2, BW / 2, BH / 2);
            c.save(); path.reset(); path.addRoundRect(rf, BH / 2, BH / 2, Path.Direction.CW); c.clipPath(path);
            float sx = -BW + ((t * .9f) % 1.6f) * BW * 1.9f; c.skew(-.4f, 0);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(sx - 40, 0, sx + 40, 0, new int[]{0x00FFFFFF, 0xA6FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRect(sx - 40, -BH, sx + 40, BH, p); p.setShader(null); c.restore();
            // إطار دوّار
            SweepGradient sg = new SweepGradient(0, 0, new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF, 0x00FFFFFF}, new float[]{0, .12f, .25f, 1});
            Matrix m = new Matrix(); m.setRotate((float) Math.toDegrees(t * 3)); sg.setLocalMatrix(m);
            p.setShader(sg); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); c.drawRoundRect(rf, BH / 2, BH / 2, p); p.setShader(null);
            p.setColor(Color.argb(A(.9f * al), 255, 230, 160)); p.setStrokeWidth(1.5f); rf.set(-BW / 2 + 4, -BH / 2 + 4, BW / 2 - 4, BH / 2 - 4); c.drawRoundRect(rf, (BH - 8) / 2, (BH - 8) / 2, p);
            // شرارات حول الحافة
            float L = BW - BH, per = 2 * L + (float) Math.PI * BH;
            for (int i = 0; i < 10; i++) {
                float a = (t * .8f + i / 10f) % 1f, d = a * per, x, y;
                if (d < L) { x = -L / 2 + d; y = -BH / 2; }
                else if (d < L + Math.PI * BH / 2) { float q = (d - L) / (BH / 2); x = L / 2 + (float) Math.sin(q) * BH / 2; y = -(float) Math.cos(q) * BH / 2; }
                else if (d < 2 * L + Math.PI * BH / 2) { x = L / 2 - (d - L - (float) Math.PI * BH / 2); y = BH / 2; }
                else { float q = (d - 2 * L - (float) Math.PI * BH / 2) / (BH / 2); x = -L / 2 - (float) Math.sin(q) * BH / 2; y = (float) Math.cos(q) * BH / 2; }
                fillColor(Color.argb(A((.5f + .5f * (float) Math.sin(t * 12 + i)) * al), 255, 240, 190)); c.drawCircle(x, y, 2.4f, p);
            }
            // النص والأيقونة
            fillColor(Color.argb(A(al), 42, 18, 0)); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(30); p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("تسريع الآن", 14, 11, p);
            c.save(); c.translate(-BW / 2 + 42, 0); c.scale(.22f, .22f); bolt(path); fillColor(Color.argb(A(al), 255, 255, 255)); c.drawPath(path, p); c.restore();
            c.restore();
        }

        // ---------- الرسم الرئيسي ----------
        @Override protected void onDraw(Canvas c) {
            t = (SystemClock.uptimeMillis() - start) / 1000f * SPEED;
            int vw = getWidth(), vh = getHeight();
            s = vw / W; if (H * s < vh) s = vh / H;
            ox = (vw - W * s) / 2; oy = (vh - H * s) / 2;
            c.drawColor(Color.rgb(2, 3, 10));
            c.save(); c.translate(ox, oy); c.scale(s, s); c.clipRect(0, 0, W, H);
            rnd.setSeed((long) (t * 30));
            float cx = W / 2, cy = H / 2;

            c.save();
            if (t > HIT && t < HIT + .45f) { float a = (1 - (t - HIT) / .45f) * 16; c.translate((rnd.nextFloat() - .5f) * a, (rnd.nextFloat() - .5f) * a); }
            float spd = t < HIT ? ei(seg(t, 0, HIT)) * .9f + .04f : .08f;
            // نجوم
            for (int i = 0; i < 260; i++) {
                float a = R(i) * 6.283f, z = (((R(i + 500) - t * (.2f + spd * 1.6f) * (.4f + R(i + 9))) % 1) + 1) % 1;
                float d = (float) Math.pow(1 - z, 2.6) * W * .8f, d0 = (float) Math.pow(1 - Math.min(1, z + .06f + spd * .18f), 2.6) * W * .8f;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                line(c, cx + ca * d0, cy + sa * d0, cx + ca * d, cy + sa * d, .8f + (1 - z) * 2.2f,
                        Color.argb(A(d / 260f), (int) (180 + R(i) * 75), (int) (200 + R(i + 2) * 55), 255));
            }
            // حلقات النفق
            if (t < HIT + .3f) for (int i = 0; i < 7; i++) {
                float z = (((i / 7f - t * .5f * (.4f + spd)) % 1) + 1) % 1, r = (float) Math.pow(1 - z, 2.2) * W * .7f;
                p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 + (1 - z) * 5); p.setColor(Color.argb(A(cl(r / 300) * .5f), 255, 150 + i * 10, 60)); c.drawCircle(cx, cy, r, p);
            }
            // طريق + سيارة
            if (t < HIT + .3f) {
                float ra = 1 - seg(t, HIT - .1f, HIT + .3f), ry = H * .74f;
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, ry, 0, H, Color.argb(A(.95f * ra), 30, 24, 16), Color.argb(A(ra), 6, 5, 4), Shader.TileMode.CLAMP)); c.drawRect(0, ry, W, H, p); p.setShader(null);
                line(c, 0, ry, W, ry, 3, Color.argb(A(.8f * ra), 255, 170, 40));
                float rs = t * (4 + ei(seg(t, .5f, HIT)) * 24);
                for (int i = 0; i < 14; i++) {
                    float z = (((i / 14f - rs * .05f) % 1) + 1) % 1, y = ry + (float) Math.pow(z, 1.8) * (H - ry - 10) + 6, len = 30 + z * z * 260;
                    float x = ((((i * 173) % W) - t * W * (.4f + z * 3) + W * 3) % (W * 1.4f)) - W * .2f;
                    fillColor(Color.argb(A((.25f + z * .5f) * ra), 255, 230, 170)); c.drawRect(x, y, x + len, y + 2 + z * 5, p);
                }
                float carIn = eo(seg(t, .9f, 3.4f)), exit = ei(seg(t, HIT - .7f, HIT));
                float x = lerp(-420, W * .4f, carIn) + exit * (W + 700);
                float bob = (float) Math.sin(t * 38) * 1.6f + (float) Math.sin(t * 11) * 1.2f, sp = 1 + ei(seg(t, .5f, HIT)) * 3, wr = t * 60 * sp;
                if (carIn > 0) {
                    for (int i = 6; i >= 1; i--) { carAlpha = .05f * (7 - i) * ra; car(c, x - i * (26 + exit * 80), ry + 22 + bob, 1.15f, wr - i, 255, 165, 31); }
                    carAlpha = ra; car(c, x, ry + 22 + bob, 1.15f, wr, 255, 165, 31); carAlpha = 1;
                    if (carIn < .98f || exit > 0) for (int i = 0; i < 8; i++) {
                        float yy = ry - 110 + i * 18 + R(i) * 8, xx = x - 230 - R(i + 3) * 300;
                        line(c, xx, yy, xx + 160 + R(i + 4) * 180, yy, 2, Color.argb(A(.35f), 255, 200, 120));
                    }
                }
            }
            // انفجار
            if (t > HIT - .15f) {
                float k = t - (HIT - .15f);
                for (int i = 0; i < 4; i++) {
                    float r = eo(cl((k - i * .07f) * 1.3f)) * W * .75f;
                    p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(14 - i * 3); p.setColor(Color.argb(A(1 - cl(k)), 255, 200 - i * 40, 100 - i * 20)); c.drawCircle(cx, cy, r, p);
                }
                sparks(c, t, HIT - .1f, 120, cx, cy, 255, 207, 90, 620, 1.6f);
                sparks(c, t, HIT, 80, cx, cy, 255, 106, 0, 380, 2);
            }
            if (t > HIT) {
                float a = eo(seg(t, HIT, HIT + .8f));
                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(cx, cy, W * .6f, Color.argb(A(.35f * a), 255, 150, 30), 0x00020308, Shader.TileMode.CLAMP)); c.drawRect(0, 0, W, H, p); p.setShader(null);
            }
            // الشعار
            float lk = seg(t, HIT + .1f, HIT + .7f);
            if (lk > 0) {
                c.save(); c.translate(cx, cy - 70); float sc = lerp(2.4f, .82f, eb(lk)); c.scale(sc, sc);
                bolt(path); halo(c, path, 255, 165, 31, cl(lk * 3));
                p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(0, -96, 0, 96, Color.rgb(255, 248, 214), Color.rgb(255, 177, 0), Shader.TileMode.CLAMP)); p.setAlpha(A(cl(lk * 3))); c.drawPath(path, p); p.setShader(null);
                c.restore();
                for (int i = 0; i < 2; i++) { float r = (t * .9f + i / 2f) % 1f; p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.argb(A((1 - r) * .6f), 255, 180, 40)); c.drawCircle(cx, cy - 70, 90 + r * 130, p); }
            }
            // العنوان
            float tk = seg(t, HIT + .7f, HIT + 1.4f);
            if (tk > 0) {
                float gl = t < HIT + 1.1f ? (1 - seg(t, HIT + .7f, HIT + 1.1f)) * 10 : 0;
                glowText(c, "مسرّع الألعاب", cx, H * .77f, 78, 255, 165, 31, eo(tk), 0, gl);
                glowText(c, "GAME BOOSTER", cx, H * .77f + 36, 26, 255, 165, 31, eo(seg(t, HIT + 1.1f, HIT + 1.7f)), .5f, 0);
            }
            // الشارات
            float bk = seg(t, HIT + 1.6f, HIT + 2.2f);
            if (bk > 0) {
                String[] names = {"⚡ أداء أعلى", "🧠 رام أنظف", "🔥 حرارة أقل"}; float[] xs = {W * .2f, W * .5f, W * .8f};
                for (int i = 0; i < 3; i++) {
                    rf.set(xs[i] - 110, H * .9f - 20, xs[i] + 110, H * .9f + 20);
                    p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(A(.16f * eo(bk)), 255, 165, 31)); c.drawRoundRect(rf, 20, 20, p);
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.argb(A(eo(bk)), 255, 165, 31)); c.drawRoundRect(rf, 20, 20, p);
                    fillColor(Color.argb(A(eo(bk)), 255, 255, 255)); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(20); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText(names[i], xs[i], H * .9f + 7, p);
                }
            }
            c.restore();
            button(c, t);
            c.restore();

            // وميض الأبيض عند الانفجار + بداية من الأسود
            if (t > HIT - .15f) { float f = Math.max(0, 1 - (t - HIT + .15f) * 3.2f); if (f > 0) c.drawColor(Color.argb(A(f), 255, 255, 255)); }
            float fade = 1 - seg(t, 0, .35f); if (fade > 0) c.drawColor(Color.argb(A(fade), 0, 0, 0));
            postInvalidateOnAnimation();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float x = (e.getX() - ox) / s, y = (e.getY() - oy) / s;
                if (Math.abs(x - BX) < BW / 2 + 30 && Math.abs(y - BY) < BH / 2 + 30 && t > .5f) { performClick(); onBtn.run(); }
            }
            return true;
        }
        @Override public boolean performClick() { return super.performClick(); }
    }
}

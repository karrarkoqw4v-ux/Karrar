package com.thememaster.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** دخولية سينمائية: قطرات ألوان تتطاير وتتجمّع لترسم أيقونة الباليت، ثم اسم التطبيق. */
public class IntroActivity extends Activity {
    static final long DURATION = 3200;
    private boolean done;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final IntroView v = new IntroView(this);
        setContentView(v);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(DURATION); a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(an -> { v.t = (float) an.getAnimatedValue(); v.invalidate(); });
        a.addListener(new android.animation.AnimatorListenerAdapter() { @Override public void onAnimationEnd(android.animation.Animator x) { go(); } });
        a.start();
        v.setOnClickListener(c -> a.cancel());
    }

    private void go() {
        if (done) return; done = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    static class IntroView extends View {
        float t;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final long t0 = SystemClock.uptimeMillis();
        final int[] cols = {Color.rgb(255, 90, 90), Color.rgb(255, 196, 60), Color.rgb(90, 200, 255), Color.rgb(120, 220, 140), Color.rgb(200, 110, 255)};
        IntroView(android.content.Context c) { super(c); }

        static float seg(float t, float a, float b) { return Math.max(0, Math.min(1, (t - a) / (b - a))); }
        static float eo(float x) { float k = 1 - x; return 1 - k * k * k; }
        static float eb(float x) { float c1 = 1.70158f, c3 = c1 + 1, k = x - 1; return 1 + c3 * k * k * k + c1 * k * k; }
        static float R(float i) { double v = Math.sin(i * 127.1 + 311.7) * 43758.5453; return (float) (v - Math.floor(v)); }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h * .46f, u = Math.min(w, h) / 100f;
            // خلفية متدرّجة بنفسجية داكنة
            p.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(20, 14, 38), Color.rgb(10, 8, 20), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p); p.setShader(null);

            // نبض خلفي خفيف متزامن مع إيقاع الدخولية
            float bgPulse = .5f + .5f * (float) Math.sin(t * 9);
            p.setShader(new RadialGradient(cx, cy, Math.max(w, h) * .55f, Color.argb((int) (50 * bgPulse * seg(t, 0, .3f)), 150, 110, 255), 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p); p.setShader(null);

            // قطرات ألوان تتطاير من الحواف نحو المركز ثم تستقر كنقاط على الباليت
            float fly = eo(seg(t, .05f, .55f));
            for (int i = 0; i < 42; i++) {
                int col = cols[i % cols.length];
                float ang = R(i) * (float) (Math.PI * 2), dist = (40 + R(i + 9) * 55) * u;
                float sx = cx + (float) Math.cos(ang) * dist * 3.4f * (1 - fly), sy = cy + (float) Math.sin(ang) * dist * 3.4f * (1 - fly);
                float ex = cx + (float) Math.cos(ang) * dist * .55f, ey = cy + (float) Math.sin(ang) * dist * .3f;
                float x = sx + (ex - sx) * fly, y = sy + (ey - sy) * fly;
                float r = (2.2f + R(i + 3) * 2.2f) * u * (0.6f + 0.4f * fly);
                p.setStyle(Paint.Style.FILL); p.setColor(Color.argb((int) (255 * fly), Color.red(col), Color.green(col), Color.blue(col)));
                c.drawCircle(x, y, r, p);
                // ذيل القطرة أثناء الطيران
                if (fly < .96f) { p.setAlpha((int) (120 * (1 - fly))); c.drawLine(sx, sy, x, y, p); }
            }

            // انفجار ضوء عند لحظة اكتمال الباليت
            float burstK = seg(t, .58f, .78f);
            if (burstK > 0 && burstK < 1) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(u * .8f);
                for (int i = 0; i < 2; i++) {
                    float r = eo(Math.max(0, Math.min(1, burstK * 1.3f - i * .15f))) * u * 60;
                    p.setColor(Color.argb((int) (170 * (1 - burstK)), 220, 190, 255));
                    c.drawCircle(cx, cy, r, p);
                }
            }
            if (burstK > 0 && burstK < .35f) { p.setStyle(Paint.Style.FILL); p.setColor(Color.argb((int) (120 * (1 - burstK / .35f)), 255, 255, 255)); c.drawRect(0, 0, w, h, p); }

            // الباليت البيضاء
            float palette = eb(seg(t, .3f, .62f));
            if (palette > 0) {
                c.save(); c.translate(cx, cy); c.scale(palette, palette);
                p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(240, 255, 255, 255));
                RectF rf = new RectF(-32 * u, -24 * u, 32 * u, 24 * u);
                c.drawOval(rf, p);
                // ثقب الإبهام
                Path hole = new Path(); hole.addCircle(10 * u, 15 * u, 10 * u, Path.Direction.CW);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT)); c.drawPath(hole, p); p.setXfermode(null);
                // 4 نقاط ألوان على الباليت
                float dotIn = eo(seg(t, .5f, .68f));
                int[][] dots = {{-18, -9, 0}, {4, -14, 1}, {24, -4, 2}, {-6, 2, 3}};
                for (int[] d : dots) {
                    float r = 6.5f * u * dotIn;
                    p.setColor(cols[d[2]]); c.drawCircle(d[0] * u, d[1] * u, r, p);
                }
                c.restore();
            }

            // اسم التطبيق
            float nameA = eo(seg(t, .6f, .82f));
            if (nameA > 0) {
                p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                p.setTextSize(11 * u); p.setColor(Color.argb((int) (255 * nameA), 255, 255, 255));
                p.setShadowLayer(8 * u, 0, 0, Color.argb((int) (160 * nameA), 150, 110, 255));
                c.drawText("صانع الثيمات", cx, h * .68f + (1 - nameA) * 10 * u, p);
                p.setShadowLayer(0, 0, 0, 0);
                p.setTextSize(4 * u); p.setTypeface(Typeface.DEFAULT); p.setColor(Color.argb((int) (190 * nameA), 210, 195, 255));
                c.drawText("THEME MASTER", cx, h * .68f + 7 * u, p);
            }

            // شريط تحميل بسيط
            float bar = eo(seg(t, .72f, .97f));
            if (bar > 0) {
                float bw = w * .42f, bx = cx - bw / 2, by = h * .8f;
                p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(60, 255, 255, 255));
                c.drawRoundRect(new RectF(bx, by, bx + bw, by + u * 1.1f), u, u, p);
                p.setShader(new LinearGradient(bx, 0, bx + bw, 0, Color.rgb(109, 76, 230), Color.rgb(230, 76, 170), Shader.TileMode.CLAMP));
                c.drawRoundRect(new RectF(bx, by, bx + bw * bar, by + u * 1.1f), u, u, p); p.setShader(null);
            }

            // تلاشي الخروج
            float out = seg(t, .95f, 1f);
            if (out > 0) { p.setColor(Color.argb((int) (255 * out), 10, 8, 20)); c.drawRect(0, 0, w, h, p); }
            float in0 = 1 - seg(t, 0, .12f);
            if (in0 > 0) { p.setColor(Color.argb((int) (255 * in0), 10, 8, 20)); c.drawRect(0, 0, w, h, p); }
        }
    }
}

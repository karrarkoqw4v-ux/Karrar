package com.gamebooster.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** مقدمة سينمائية بملء الشاشة بوضع أفقي: حلقات نيون + ضربة ضوء + شعار + تحميل، ثم تدخل للتطبيق. */
public class IntroActivity extends Activity {
    static final long DURATION = 3600;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        final IntroView v = new IntroView(this);
        setContentView(v);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(DURATION);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(an -> { v.t = (float) an.getAnimatedValue(); v.invalidate(); });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator x) { go(); }
        });
        a.start();
        v.setOnClickListener(c -> { a.cancel(); });   // لمسة = تخطي
    }

    private boolean done;
    private void go() {
        if (done) return;
        done = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    static class IntroView extends View {
        float t;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path bolt = new Path();
        IntroView(Context c) { super(c); }

        static float seg(float t, float a, float b) { return Math.max(0, Math.min(1, (t - a) / (b - a))); }
        static float ease(float x) { return 1 - (1 - x) * (1 - x) * (1 - x); }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h * 0.42f, u = Math.min(w, h) / 100f;
            // خلفية
            p.setShader(new RadialGradient(cx, cy, Math.max(w, h) * 0.7f, new int[]{Color.rgb(40, 6, 14), Color.rgb(8, 8, 16), Color.BLACK},
                    new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);

            // خطوط سرعة مائلة
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(u * 0.25f);
            for (int i = 0; i < 18; i++) {
                float k = (t * 2.2f + i * 0.137f) % 1f, y = h * ((i * 53 % 100) / 100f);
                p.setColor(Color.argb((int) (90 * (1 - k)), 255, 40, 70));
                float x = w * (1.1f - k * 1.4f);
                c.drawLine(x, y, x + u * (8 + i % 5 * 4), y - u * 2, p);
            }

            // حلقات نيون تتوسع
            float ring = seg(t, 0.05f, 0.6f);
            for (int i = 0; i < 3; i++) {
                float r = ease(Math.max(0, Math.min(1, ring * 1.4f - i * 0.2f))) * u * (38 + i * 12);
                p.setStrokeWidth(u * (1.4f - i * 0.4f));
                p.setColor(Color.argb((int) (230 * (1 - ring * 0.7f) * (r > 0 ? 1 : 0)), 255, 30 + i * 30, 60));
                c.drawCircle(cx, cy, r, p);
            }

            // الشعار: صاعقة
            float logo = ease(seg(t, 0.18f, 0.5f));
            if (logo > 0) {
                c.save();
                c.translate(cx, cy);
                float s = u * 0.55f * (0.4f + 0.6f * logo) * (1 + 0.04f * (float) Math.sin(t * 40));
                c.scale(s, s);
                bolt.reset();
                bolt.moveTo(8, -50); bolt.lineTo(-26, 6); bolt.lineTo(-3, 6); bolt.lineTo(-12, 50);
                bolt.lineTo(26, -10); bolt.lineTo(3, -10); bolt.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb((int) (90 * logo), 255, 40, 70)); p.setMaskFilter(new BlurMaskFilter(18, BlurMaskFilter.Blur.NORMAL));
                c.drawPath(bolt, p); p.setMaskFilter(null);
                p.setShader(new LinearGradient(0, -50, 0, 50, Color.WHITE, Color.rgb(255, 40, 70), Shader.TileMode.CLAMP));
                p.setAlpha((int) (255 * logo));
                c.drawPath(bolt, p); p.setShader(null);
                c.restore();
            }

            // ضربة ضوء تعبر الشاشة
            float sw = seg(t, 0.35f, 0.62f);
            if (sw > 0 && sw < 1) {
                float x = -w * 0.3f + sw * w * 1.6f;
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(x - u * 12, 0, x + u * 12, 0, new int[]{0, Color.argb(120, 255, 255, 255), 0}, null, Shader.TileMode.CLAMP));
                c.save(); c.skew(-0.35f, 0); c.drawRect(x - u * 12, 0, x + u * 12, h, p); c.restore();
                p.setShader(null);
            }

            // النص
            float tx = ease(seg(t, 0.45f, 0.72f));
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
            p.setTextSize(u * 11);
            p.setColor(Color.argb((int) (255 * tx), 255, 255, 255));
            p.setShadowLayer(u * 2, 0, 0, Color.argb((int) (255 * tx), 255, 30, 60));
            c.drawText("مسرّع الألعاب", cx, h * 0.72f + (1 - tx) * u * 8, p);
            p.setShadowLayer(0, 0, 0, 0);
            p.setTextSize(u * 4.2f); p.setLetterSpacing(0.5f);
            p.setColor(Color.argb((int) (200 * tx), 255, 90, 110));
            c.drawText("GAME BOOSTER", cx, h * 0.72f + u * 7 + (1 - tx) * u * 8, p);
            p.setLetterSpacing(0);

            // شريط التحميل
            float bar = seg(t, 0.55f, 0.95f);
            float bw = w * 0.5f, bx = cx - bw / 2, by = h * 0.9f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(60, 255, 255, 255));
            c.drawRoundRect(bx, by, bx + bw, by + u * 1.2f, u, u, p);
            p.setShader(new LinearGradient(bx, 0, bx + bw, 0, Color.rgb(255, 30, 60), Color.rgb(255, 160, 60), Shader.TileMode.CLAMP));
            c.drawRoundRect(bx, by, bx + bw * ease(bar), by + u * 1.2f, u, u, p);
            p.setShader(null);

            // وميض خروج
            float out = seg(t, 0.93f, 1f);
            if (out > 0) { p.setColor(Color.argb((int) (255 * out), 0, 0, 0)); c.drawRect(0, 0, w, h, p); }
        }
    }
}

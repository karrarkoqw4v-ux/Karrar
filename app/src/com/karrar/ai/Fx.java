package com.karrar.ai;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/** Motion + visual effects (the "CSS effects" of the app). */
public final class Fx {
    /** Press feedback: shrink on touch, springy release. */
    public static void press(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View x, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        x.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
                        break;
                }
                return false;
            }
        });
    }

    /** Slide-up + fade-in entrance, used with a delay to stagger lists. */
    public static void enter(View v, long delay) {
        float d = v.getResources().getDisplayMetrics().density * 22;
        v.setAlpha(0f);
        v.setTranslationY(d);
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(460)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
    }

    public static void pop(View v) {
        v.setScaleX(0.6f);
        v.setScaleY(0.6f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(new OvershootInterpolator(3.2f)).start();
    }

    /** Looping "breathing" scale + alpha. */
    public static ObjectAnimator breathe(View v, float to) {
        android.animation.PropertyValuesHolder sx = android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, to);
        android.animation.PropertyValuesHolder sy = android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, to);
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, sx, sy);
        a.setDuration(1800);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        a.start();
        return a;
    }

    public static ObjectAnimator spin(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "rotation", 0f, 360f);
        a.setDuration(1400);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        a.start();
        return a;
    }

    /** Animated height expand / collapse. */
    public static void expand(final View v, boolean show) {
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (show) {
            int w = v.getWidth() > 0 ? v.getWidth() : ((View) v.getParent()).getWidth();
            if (w <= 0) w = v.getResources().getDisplayMetrics().widthPixels - Theme.dp(v.getContext(), 40);
            v.setVisibility(View.VISIBLE);
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            final int target = v.getMeasuredHeight();
            lp.height = 0;
            v.setLayoutParams(lp);
            v.setAlpha(0f);
            ValueAnimator a = ValueAnimator.ofInt(0, target);
            a.setDuration(260);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator an) {
                    lp.height = (Integer) an.getAnimatedValue();
                    v.setLayoutParams(lp);
                    v.setAlpha(an.getAnimatedFraction());
                }
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                public void onAnimationEnd(android.animation.Animator an) { lp.height = ViewGroup.LayoutParams.WRAP_CONTENT; v.setLayoutParams(lp); v.setAlpha(1f); }
            });
            a.start();
        } else {
            final int from = v.getHeight();
            ValueAnimator a = ValueAnimator.ofInt(from, 0);
            a.setDuration(220);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator an) {
                    lp.height = (Integer) an.getAnimatedValue();
                    v.setLayoutParams(lp);
                    v.setAlpha(1f - an.getAnimatedFraction());
                }
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                public void onAnimationEnd(android.animation.Animator an) { v.setVisibility(View.GONE); lp.height = ViewGroup.LayoutParams.WRAP_CONTENT; v.setLayoutParams(lp); v.setAlpha(1f); }
            });
            a.start();
        }
    }

    /** Paints a TextView's glyphs with a flowing accent gradient (colour keeps moving). */
    public static void gradientText(final TextView t) {
        final LinearGradient[] sh = {null};
        final Matrix mx = new Matrix();
        final ValueAnimator an = ValueAnimator.ofFloat(0f, 1f);
        an.setDuration(3600);
        an.setRepeatCount(ValueAnimator.INFINITE);
        an.setInterpolator(new LinearInterpolator());
        an.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                if (sh[0] == null) return;
                mx.setTranslate((Float) a.getAnimatedValue() * t.getWidth() * 1.7f, 0);
                sh[0].setLocalMatrix(mx);
                t.invalidate();
            }
        });
        t.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int tp, int r, int b, int ol, int ot, int or, int ob) {
                float w = Math.max(1, r - l);
                sh[0] = new LinearGradient(0, 0, w * 0.85f, 0, new int[]{Theme.accent, Theme.accent2, 0xFF22D3EE},
                        null, Shader.TileMode.MIRROR);
                t.getPaint().setShader(sh[0]);
            }
        });
        t.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { an.start(); }
            public void onViewDetachedFromWindow(View v) { an.cancel(); }
        });
        if (t.isAttachedToWindow()) an.start();
    }

    /** Typewriter: types the text char by char with a cursor. */
    public static void typeInto(final TextView tv, final String text, final long ms) {
        Object old = tv.getTag();
        if (old instanceof Runnable) tv.removeCallbacks((Runnable) old);
        final int[] i = {0};
        Runnable r = new Runnable() {
            public void run() {
                i[0]++;
                tv.setText(text.substring(0, i[0]) + (i[0] < text.length() ? "▍" : ""));
                if (i[0] < text.length()) tv.postDelayed(this, ms);
            }
        };
        tv.setTag(r);
        tv.setText("▍");
        tv.postDelayed(r, 120);
    }

    /** Types a phrase, holds, erases it, then moves to the next one — forever. */
    public static void typeLoop(final TextView tv, final String[] phrases) {
        final Runnable[] step = new Runnable[1];
        final int[] st = {0, 0, 0}; // phrase, chars, mode(0 type,1 hold,2 erase)
        step[0] = new Runnable() {
            public void run() {
                if (!tv.isAttachedToWindow()) { tv.postDelayed(this, 600); return; }
                String ph = phrases[st[0]];
                long next;
                if (st[2] == 0) {
                    st[1]++;
                    next = 62;
                    if (st[1] >= ph.length()) { st[2] = 1; next = 2200; }
                } else if (st[2] == 1) {
                    st[2] = 2;
                    next = 30;
                } else {
                    st[1]--;
                    next = 26;
                    if (st[1] <= 0) { st[1] = 0; st[2] = 0; st[0] = (st[0] + 1) % phrases.length; next = 320; }
                }
                tv.setText(ph.substring(0, Math.min(st[1], ph.length())) + "▍");
                tv.postDelayed(this, next);
            }
        };
        tv.setText("▍");
        tv.postDelayed(step[0], 500);
    }

    // ------------------------------------------------------------------ views
    /** Slowly drifting colour blobs (aurora) used as a living background. */
    public static class Aurora extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator anim;
        private float t;
        private final int[] cols;
        private final Shader[] sh = new Shader[3];
        private int lw, lh;

        public Aurora(Context c, int a1, int a2, int a3) {
            super(c);
            cols = new int[]{a1, a2, a3};
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(22000);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
            });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0) return;
            float r = Math.max(w, h) * 0.62f;
            if (w != lw || h != lh) {
                for (int i = 0; i < 3; i++) sh[i] = new RadialGradient(0, 0, r, cols[i], cols[i] & 0x00FFFFFF, Shader.TileMode.CLAMP);
                lw = w; lh = h;
            }
            double tau = Math.PI * 2;
            float[][] base = {{0.15f, 0.08f}, {0.95f, 0.30f}, {0.4f, 0.75f}};
            for (int i = 0; i < 3; i++) {
                double ang = tau * (t + i / 3.0);
                float x = w * base[i][0] + (float) Math.sin(ang) * w * 0.22f;
                float y = h * base[i][1] + (float) Math.cos(ang * 1.0) * h * 0.10f;
                p.setShader(sh[i]);
                c.save();
                c.translate(x, y);
                c.drawCircle(0, 0, r, p);
                c.restore();
            }
        }
    }

    /** Text with a light sweep running across it (loading / thinking state). */
    public static class Shimmer extends TextView {
        private ValueAnimator anim;
        private float phase;
        private int base, hi;
        private boolean active = true;

        public Shimmer(Context c, int base, int hi) {
            super(c);
            this.base = base;
            this.hi = hi;
            setTextColor(base);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1500);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { phase = (Float) a.getAnimatedValue(); invalidate(); }
            });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        public void setActive(boolean a) { active = a; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            if (!active) { super.onDraw(c); return; }
            float w = Math.max(1, getWidth());
            float x = -w * 0.5f + phase * w * 2f;
            getPaint().setShader(new LinearGradient(x - w * 0.35f, 0, x + w * 0.35f, 0,
                    new int[]{base, hi, base}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            super.onDraw(c);
            getPaint().setShader(null);
        }
    }

    /** Three bouncing dots ("typing"). */
    public static class Dots extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator anim;
        private float t;

        public Dots(Context c) { super(c); }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1100);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
            });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            int[] cols = {Theme.accent, 0xFF6F7FFF, Theme.accent2};
            for (int i = 0; i < 3; i++) {
                float ph = (float) Math.sin((t - i * 0.16f) * Math.PI * 2);
                float k = 0.5f + 0.5f * ph;
                p.setColor(cols[i]);
                p.setAlpha((int) (110 + 145 * k));
                float cy = getHeight() / 2f - k * 4 * d;
                c.drawCircle(d * 8 + i * d * 14, cy, d * (3.4f + 1.2f * k), p);
            }
        }

        @Override protected void onMeasure(int w, int h) {
            float d = getResources().getDisplayMetrics().density;
            setMeasuredDimension((int) (d * 46), (int) (d * 26));
        }
    }

    /** Coloured characters being "typed" from the top of the screen and falling (behind the UI). */
    public static class Rain extends View {
        private static final String G = "ابتثجحخدذرزسشصضطعغفقكلمنهوي01<>{}/=;AIkarrar";
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator anim;
        private float[] head, speed;
        private int cols, rows, len = 11;
        private float cell, time;
        private long last;
        private int lw, lh;
        private final java.util.Random rnd = new java.util.Random(7);

        public Rain(Context c) {
            super(c);
            p.setTypeface(Fonts.b);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            last = System.nanoTime();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1000);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    long now = System.nanoTime();
                    float dt = Math.min(0.05f, (now - last) / 1e9f);
                    last = now;
                    time += dt;
                    if (head != null) {
                        for (int i = 0; i < cols; i++) {
                            head[i] += speed[i] * dt;
                            if (head[i] - len > rows * 0.8f) { head[i] = -rnd.nextInt(14); speed[i] = 3f + rnd.nextFloat() * 5f; }
                        }
                    }
                    invalidate();
                }
            });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0) return;
            float d = getResources().getDisplayMetrics().density;
            if (w != lw || h != lh) {
                cell = d * 17;
                cols = (int) (w / cell);
                rows = (int) (h / cell);
                head = new float[cols];
                speed = new float[cols];
                for (int i = 0; i < cols; i++) { head[i] = -rnd.nextInt(30); speed[i] = 3f + rnd.nextFloat() * 5f; }
                p.setTextSize(cell * 0.78f);
                p.setTextAlign(Paint.Align.CENTER);
                lw = w; lh = h;
            }
            int[] pal = {Theme.accent, Theme.accent2, 0xFF22D3EE, 0xFF34D399};
            float fadeH = h * 0.78f;
            for (int i = 0; i < cols; i += 2) {
                int base = pal[(i / 2 + (int) (time * 0.6f)) % pal.length];
                for (int k = 0; k < len; k++) {
                    int row = (int) head[i] - k;
                    if (row < 0) continue;
                    float y = row * cell + cell;
                    float topFade = 1f - y / fadeH;
                    if (topFade <= 0) continue;
                    float trail = 1f - k / (float) len;
                    int idx = (int) ((((long) i * 131 + row * 31 + (long) (time * (k == 0 ? 9 : 2))) * 2654435761L >>> 5) % G.length());
                    int a = (int) (255 * (k == 0 ? 0.9f : 0.42f * trail) * topFade * (Theme.dark ? 1f : 0.7f));
                    p.setColor(k == 0 ? 0xFFFFFFFF : base);
                    p.setAlpha(Math.max(0, Math.min(255, a)));
                    if (k == 0) p.setShadowLayer(d * 6, 0, 0, base); else p.clearShadowLayer();
                    c.drawText(String.valueOf(G.charAt(idx)), i * cell + cell / 2, y, p);
                }
            }
            p.clearShadowLayer();
        }
    }
}

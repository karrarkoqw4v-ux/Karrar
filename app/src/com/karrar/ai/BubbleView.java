package com.karrar.ai;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** The floating circular button itself: a ring that cycles black/white/red while idle (so the user can see it
 * and reach it), and a tap opens Bawsala's own voice/command mode. Drawn only inside this small round view;
 * it does not read or touch whatever app is behind it. */
public class BubbleView extends View {
    private float hue = 0f;
    private ValueAnimator loop;

    public BubbleView(Context c) {
        super(c);
        loop = ValueAnimator.ofFloat(0f, 1f);
        loop.setDuration(4200);
        loop.setRepeatCount(ValueAnimator.INFINITE);
        loop.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { hue = (float) a.getAnimatedValue(); invalidate(); }
        });
        loop.start();
    }

    public void destroy() {
        if (loop != null) loop.cancel();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float r = Math.min(w, h) / 2f;
        // cycles through black -> red -> white -> black
        int c1, c2;
        float t = hue * 3f;
        int seg = (int) t;
        float f = t - seg;
        int[] palette = {0xFF0E0E10, 0xFFE53935, 0xFFF5F5F5};
        c1 = palette[seg % 3];
        c2 = palette[(seg + 1) % 3];
        int mixed = Color.rgb(
                (int) (Color.red(c1) * (1 - f) + Color.red(c2) * f),
                (int) (Color.green(c1) * (1 - f) + Color.green(c2) * f),
                (int) (Color.blue(c1) * (1 - f) + Color.blue(c2) * f));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(w / 2f, h / 2f, r, mixed, 0xCC1A1A1E, Shader.TileMode.CLAMP));
        c.drawCircle(w / 2f, h / 2f, r - 4, p);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(5);
        ring.setColor(mixed);
        c.drawCircle(w / 2f, h / 2f, r - 4, ring);
    }
}

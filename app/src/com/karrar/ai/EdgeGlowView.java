package com.karrar.ai;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Draws a soft colored glow around the four edges of the screen (click-through, no touch interception).
 * Used to show Bawsala's state while it listens to / executes a command: black = listening, white = thinking,
 * red = speaking/acting. Purely visual; it never captures input from, or draws over the content of, other apps
 * beyond this border effect, and only appears while the user is actively using Bawsala's own voice/command mode. */
public class EdgeGlowView extends View {
    private int color = 0x00000000;
    private float alpha = 0f;
    private ValueAnimator anim;
    private static final int THICKNESS_DP = 42;

    public EdgeGlowView(Context c) {
        super(c);
        setAlpha(0f);
    }

    /** state: "listening" (black), "thinking" (white), "speaking" (red), or null/"" to hide. */
    public void setState(String state) {
        int target;
        if ("listening".equals(state)) target = 0xFF101010;
        else if ("thinking".equals(state)) target = 0xFFF5F5F5;
        else if ("speaking".equals(state)) target = 0xFFE53935;
        else target = 0;
        color = target;
        float targetAlpha = target == 0 ? 0f : 1f;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(alpha, targetAlpha);
        anim.setDuration(260);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { alpha = (float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (alpha <= 0.01f || color == 0) return;
        float d = getResources().getDisplayMetrics().density * THICKNESS_DP;
        int w = getWidth(), h = getHeight();
        int opaque = color, clear = color & 0x00FFFFFF;
        Paint p = new Paint();
        p.setAlpha(Math.round(alpha * 255));

        p.setShader(new LinearGradient(0, 0, 0, d, opaque, clear, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, d, p);
        p.setShader(new LinearGradient(0, h, 0, h - d, opaque, clear, Shader.TileMode.CLAMP));
        c.drawRect(0, h - d, w, h, p);
        p.setShader(new LinearGradient(0, 0, d, 0, opaque, clear, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, d, h, p);
        p.setShader(new LinearGradient(w, 0, w - d, 0, opaque, clear, Shader.TileMode.CLAMP));
        c.drawRect(w - d, 0, w, h, p);
    }
}

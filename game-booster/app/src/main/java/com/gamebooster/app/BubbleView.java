package com.gamebooster.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** زر فتح لوحة التحكم: دائرة أو مربع مستدير فوق اللعبة، يطفو وينبض بهدوء. */
public class BubbleView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private final long t0 = SystemClock.uptimeMillis(); private final boolean square;
    private final RectF rf = new RectF(); boolean pressed; long burstT = -10000;
    public BubbleView(Context c, boolean square) { super(c); this.square = square; }
    void burst() { burstT = SystemClock.uptimeMillis(); }
    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis(); float t = (now - t0) / 1000f, w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2 + (float) Math.sin(t * 2f) * h * .02f, r = Math.min(w, h) * .32f * (pressed ? .9f : 1f);
        // موجتان تنتشران
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 2; i++) { float k = (t * .6f + i * .5f) % 1f; p.setStrokeWidth(2.5f); p.setColor(Color.argb((int) (150 * (1 - k)), 255, 165, 31)); float rr = r * (1.05f + k * .6f); rf.set(cx - rr, cy - rr, cx + rr, cy + rr); if (square) c.drawRoundRect(rf, rr * .35f, rr * .35f, p); else c.drawCircle(cx, cy, rr, p); }
        float bk = (now - burstT) / 400f; if (bk >= 0 && bk < 1) { p.setStrokeWidth(5); p.setColor(Color.argb((int) (255 * (1 - bk)), 255, 255, 255)); float rr = r * (1.1f + bk * 1.2f); rf.set(cx - rr, cy - rr, cx + rr, cy + rr); if (square) c.drawRoundRect(rf, rr * .35f, rr * .35f, p); else c.drawCircle(cx, cy, rr, p); }
        // الجسم
        rf.set(cx - r, cy - r, cx + r, cy + r); p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, r * 1.6f, Color.argb(120, 255, 140, 20), 0x00000000, Shader.TileMode.CLAMP)); c.drawCircle(cx, cy, r * 1.6f, p); p.setShader(null);
        p.setShader(new LinearGradient(0, cy - r, 0, cy + r, Color.rgb(255, 200, 80), Color.rgb(230, 110, 10), Shader.TileMode.CLAMP));
        if (square) c.drawRoundRect(rf, r * .38f, r * .38f, p); else c.drawCircle(cx, cy, r, p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f); p.setColor(Color.argb(230, 255, 255, 255)); if (square) c.drawRoundRect(rf, r * .38f, r * .38f, p); else c.drawCircle(cx, cy, r, p);
        // لمعة دوّارة على الحافة
        p.setStrokeWidth(3.5f); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.argb(220, 255, 255, 255)); if (!square) c.drawArc(rf, t * 120, 50, false, p); p.setStrokeCap(Paint.Cap.BUTT);
        Icons.draw(c, "bolt", cx, cy, r * 1.1f, Color.rgb(60, 25, 0), p);
        postInvalidateOnAnimation();
    }
}

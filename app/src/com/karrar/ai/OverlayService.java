package com.karrar.ai;

import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;

/** The floating bubble + edge-glow overlay. Draws ONLY: (1) a small draggable circular button the user can tap
 * to jump into Bawsala's own voice/command mode, and (2) a thin colored glow at the screen edges while Bawsala
 * is listening/thinking/speaking. It never reads, intercepts or injects input into any other app's window —
 * there is no accessibility service and no input injection here, only two overlay views drawn on top. */
public class OverlayService extends Service {
    public static volatile OverlayService instance;
    private WindowManager wm;
    private BubbleView bubble;
    private EdgeGlowView glow;
    private WindowManager.LayoutParams bubbleParams;
    private float downX, downY, startX, startY;
    private long downTime;
    private boolean dragging = false;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        addGlow();
        addBubble();
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void addGlow() {
        glow = new EdgeGlowView(this);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        try { wm.addView(glow, p); } catch (Exception ignored) { }
    }

    private void addBubble() {
        bubble = new BubbleView(this);
        int sizePx = (int) (56 * getResources().getDisplayMetrics().density);
        bubbleParams = new WindowManager.LayoutParams(
                sizePx, sizePx, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        bubbleParams.x = dm.widthPixels - sizePx - (int) (12 * dm.density);
        bubbleParams.y = dm.heightPixels - sizePx - (int) (140 * dm.density);
        bubble.setOnTouchListener(new android.view.View.OnTouchListener() { public boolean onTouch(android.view.View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = bubbleParams.x; startY = bubbleParams.y;
                    downTime = System.currentTimeMillis();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (Math.abs(dx) > 14 || Math.abs(dy) > 14) dragging = true;
                    if (dragging) {
                        bubbleParams.x = (int) (startX + dx);
                        bubbleParams.y = (int) (startY + dy);
                        try { wm.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) { }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (!dragging && System.currentTimeMillis() - downTime < 400) {
                        onTap();
                    } else {
                        snapToEdge();
                    }
                    return true;
            }
            return false;
        } });
        try {
            bubble.setAlpha(0f);
            wm.addView(bubble, bubbleParams);
            bubble.animate().alpha(1f).setDuration(220).start();
        } catch (Exception ignored) { }
    }

    private void snapToEdge() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int mid = bubbleParams.x + bubbleParams.width / 2;
        int targetX = mid < dm.widthPixels / 2
                ? (int) (12 * dm.density)
                : dm.widthPixels - bubbleParams.width - (int) (12 * dm.density);
        ValueAnimator a = ValueAnimator.ofInt(bubbleParams.x, targetX);
        a.setDuration(220);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator an) {
            bubbleParams.x = (int) an.getAnimatedValue();
            try { wm.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) { }
        } });
        a.start();
    }

    private void onTap() {
        glow.setState("listening");
        Intent i = new Intent(this, WebActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra("bawsala_overlay_voice", true);
        startActivity(i);
    }

    /** Called from the web layer (via Bridge) with "listening" | "thinking" | "speaking" | "" (hide). */
    public void setGlowState(String state) {
        if (glow != null) glow.setState(state);
    }

    @Override
    public void onDestroy() {
        instance = null;
        try { if (bubble != null) { bubble.destroy(); wm.removeView(bubble); } } catch (Exception ignored) { }
        try { if (glow != null) wm.removeView(glow); } catch (Exception ignored) { }
        super.onDestroy();
    }
}

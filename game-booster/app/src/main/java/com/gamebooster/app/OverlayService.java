package com.gamebooster.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.*;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** خدمة اللوحة فوق التطبيقات: لسان جانبي (اسحبه للداخل) أو فقاعة حرّة، وكلاهما يفتح لوحة اللعبة. */
public class OverlayService extends Service {
    static boolean running;
    private WindowManager wm;
    private View handleL, handleR, bubble; private PanelView panel; private final android.os.Handler ah = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable autoClean = new Runnable() { @Override public void run() { if (Booster.prefs(OverlayService.this).getBoolean("autoClean", false)) Booster.killBackground(OverlayService.this, null); ah.postDelayed(this, 90000); } };
    private WindowManager.LayoutParams bp;
    private int type;

    @Override public IBinder onBind(Intent i) { return null; }

    /** مقبض مضيء على حافة الشاشة: اسحب للداخل أو اضغط لفتح اللوحة */
    static class Handle extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final long t0 = SystemClock.uptimeMillis(); final boolean left;
        Handle(android.content.Context c, boolean left) { super(c); this.left = left; }
        @Override protected void onDraw(Canvas c) {
            float t = (SystemClock.uptimeMillis() - t0) / 1000f, w = getWidth(), h = getHeight(), a = .55f + .45f * (float) Math.sin(t * 3);
            p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(left ? 0 : w, 0, left ? w : 0, 0, Color.argb((int) (255 * a), 255, 140, 30), Color.argb(0, 255, 140, 30), Shader.TileMode.CLAMP));
            c.drawRoundRect(left ? -w : 0, 0, left ? w : 2 * w, h, w, w, p); p.setShader(null);
            p.setColor(Color.argb((int) (230 * a), 255, 230, 180)); c.drawRoundRect(left ? 2 : w * .55f, h * .38f, left ? w * .45f : w - 2, h * .62f, 6, 6, p);
            postInvalidateOnAnimation();
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("booster", "مسرّع الألعاب", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "booster").setContentTitle("مسرّع الألعاب يعمل").setContentText("اسحب من حافة الشاشة لفتح لوحة اللعبة").setSmallIcon(android.R.drawable.stat_notify_more).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE); else startForeground(1, n);
        if (!Booster.hasOverlay(this) || Booster.flag(this, "disableBubble", false)) { stopSelf(); return; }
        running = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        showTriggers(); ah.postDelayed(autoClean, 90000);
    }

    private void showTriggers() {
        if (Booster.num(this, "bubbleStyle", 1) == 1) {
            handleL = addHandle(true); handleR = addHandle(false);
        } else {
            TextView b = new TextView(this); b.setText("⚡"); b.setTextSize(24); b.setGravity(Gravity.CENTER); b.setTextColor(Color.rgb(42, 18, 0)); b.setBackground(UiUtil.round(UiUtil.ACC, 200, Color.WHITE, 3));
            int sz = UiUtil.dp(b, 52); b.setWidth(sz); b.setHeight(sz); bubble = b;
            bp = new WindowManager.LayoutParams(-2, -2, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
            bp.gravity = Gravity.TOP | Gravity.START; bp.x = 0; bp.y = 200;
            b.setOnTouchListener(new View.OnTouchListener() {
                float sx, sy; int ox, oy; boolean moved;
                @Override public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getAction()) {
                        case MotionEvent.ACTION_DOWN: sx = e.getRawX(); sy = e.getRawY(); ox = bp.x; oy = bp.y; moved = false; v.animate().scaleX(.88f).scaleY(.88f).setDuration(80).start(); return true;
                        case MotionEvent.ACTION_MOVE: if (Math.abs(e.getRawX() - sx) + Math.abs(e.getRawY() - sy) > 12) moved = true; bp.x = ox + (int) (e.getRawX() - sx); bp.y = oy + (int) (e.getRawY() - sy); wm.updateViewLayout(bubble, bp); return true;
                        case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: v.animate().scaleX(1f).scaleY(1f).setDuration(140).start(); if (!moved) openPanel(); return true;
                    }
                    return false;
                }
            });
            wm.addView(bubble, bp);
        }
    }

    private View addHandle(final boolean left) {
        Handle hv = new Handle(this, left);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(UiUtil.dp(hv, 20), UiUtil.dp(hv, 170), type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = (left ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL;
        hv.setOnTouchListener(new View.OnTouchListener() {
            float sx; boolean fired;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: sx = e.getRawX(); fired = false; return true;
                    case MotionEvent.ACTION_MOVE: { float dx = (e.getRawX() - sx) * (left ? 1 : -1); if (!fired && dx > UiUtil.dp(v, 24)) { fired = true; openPanel(); } return true; }
                    case MotionEvent.ACTION_UP: if (!fired && Math.abs(e.getRawX() - sx) < 10) openPanel(); return true;
                }
                return false;
            }
        });
        wm.addView(hv, lp); return hv;
    }

    private void openPanel() {
        if (panel != null) return;
        hideTriggers();
        panel = new PanelView(this, () -> { closePanel(); showTriggers(); });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        wm.addView(panel, lp);
    }
    private void closePanel() { try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) {} panel = null; }
    private void hideTriggers() {
        for (View v : new View[]{handleL, handleR, bubble}) try { if (v != null) wm.removeView(v); } catch (Exception ignored) {}
        handleL = handleR = bubble = null;
    }

    @Override public void onDestroy() {
        running = false; ah.removeCallbacksAndMessages(null); closePanel(); if (wm != null) hideTriggers(); Extras.clear(this); super.onDestroy();
    }
}

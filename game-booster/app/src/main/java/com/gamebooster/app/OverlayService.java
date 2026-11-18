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

/** خدمة اللوحة فوق التطبيقات: لسان جانبي (اسحبه للداخل) أو فقاعة حرّة، وكلاهما يفتح لوحة اللعبة. */
public class OverlayService extends Service {
    static boolean running;
    private WindowManager wm;
    private View bubble; private PanelView panel; private final android.os.Handler ah = new android.os.Handler(android.os.Looper.getMainLooper());
    private int missCount; private boolean seenGame;
    /** يراقب الخروج من اللعبة: عند خروجك يُرجع كل شيء تلقائياً ويُغلق اللوحة. */
    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            ShizukuHelper.run(() -> {
                Booster.thermalGuard(OverlayService.this);
                final String top = Booster.topPackage(OverlayService.this);
                ah.post(() -> onTop(top));
            });
            ah.postDelayed(this, 2000);
        }
    };
    private void onTop(String top) {
        if (!running || top == null || !Booster.prefs(this).getBoolean("sessionOn", false)) return;
        String game = Booster.prefs(this).getString("sessionPkg", null);
        if (top.equals(game)) { missCount = 0; seenGame = true; return; }
        if (top.equals("com.android.systemui") || top.equals(getPackageName())) { if (top.equals(getPackageName())) missCount += 2; return; }
        if (seenGame && ++missCount >= 2) { Booster.endSession(this); Extras.clear(this); stopSelf(); }
    }
    private final Runnable autoClean = new Runnable() { @Override public void run() { if (Booster.prefs(OverlayService.this).getBoolean("autoClean", false)) Booster.killBackground(OverlayService.this, null); ah.postDelayed(this, 90000); } };
    private WindowManager.LayoutParams bp;
    private int type;

    @Override public IBinder onBind(Intent i) { return null; }

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
        waitThenShowTriggers(0); ah.postDelayed(autoClean, 90000); ah.postDelayed(monitor, 3000);
    }

    /** ينتظر حتى تكون اللعبة فعلياً في المقدّمة قبل إظهار زر اللوحة، حتى لا يظهر فوق واجهة المسرّع نفسه أثناء الانتقال. */
    private void waitThenShowTriggers(final int tries) {
        if (tries >= 8) { showTriggers(); return; }         // بعد ~4 ثوانٍ أظهره على أي حال (قد لا يتوفر كشف المقدّمة)
        if (ShizukuHelper.granted() || Booster.hasUsage(this)) {
            ShizukuHelper.run(() -> {
                final String top = Booster.topPackage(OverlayService.this);
                final boolean ready = top != null && !top.equals(getPackageName());
                ah.post(() -> { if (ready) showTriggers(); else ah.postDelayed(() -> waitThenShowTriggers(tries + 1), 500); });
            });
        } else ah.postDelayed(() -> waitThenShowTriggers(tries + 1), 500);
    }

    private void showTriggers() {
        final BubbleView b = new BubbleView(this, Booster.num(this, "bubbleStyle", 0) == 1); bubble = b;
        final int size = UiUtil.dp(b, 84);
        bp = new WindowManager.LayoutParams(size, size, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        bp.gravity = Gravity.CENTER;           // وسط الشاشة افتراضياً
        bp.x = Booster.prefs(this).getInt("bubX", 0); bp.y = Booster.prefs(this).getInt("bubY", 0);
        b.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: sx = e.getRawX(); sy = e.getRawY(); ox = bp.x; oy = bp.y; moved = false; b.pressed = true; return true;
                    case MotionEvent.ACTION_MOVE: if (Math.abs(e.getRawX() - sx) + Math.abs(e.getRawY() - sy) > 14) moved = true; if (moved) { bp.x = ox + (int) (e.getRawX() - sx); bp.y = oy + (int) (e.getRawY() - sy); try { wm.updateViewLayout(bubble, bp); } catch (Exception ignored) {} } return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: b.pressed = false; if (moved) Booster.prefs(OverlayService.this).edit().putInt("bubX", bp.x).putInt("bubY", bp.y).apply(); else { b.burst(); ah.postDelayed(OverlayService.this::openPanel, 160); } return true;
                }
                return false;
            }
        });
        wm.addView(b, bp);
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
    private void hideTriggers() { try { if (bubble != null) wm.removeView(bubble); } catch (Exception ignored) {} bubble = null; }

    @Override public void onDestroy() {
        running = false; ah.removeCallbacksAndMessages(null); closePanel(); if (wm != null) hideTriggers(); Extras.clear(this); super.onDestroy();
    }
}

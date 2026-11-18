package com.gamebooster.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** فقاعة عائمة فوق التطبيقات (الألعاب) مع لوحة أدوات سريعة. */
public class OverlayService extends Service {
    static boolean running;
    private WindowManager wm;
    private TextView bubble;
    private LinearLayout panel;
    private WindowManager.LayoutParams bp, pp;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("booster", "مسرّع الألعاب", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "booster").setContentTitle("مسرّع الألعاب يعمل").setContentText("الفقاعة العائمة مفعّلة")
                .setSmallIcon(android.R.drawable.stat_notify_more).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
        if (!Booster.hasOverlay(this) || Booster.flag(this, "disableBubble", false)) { stopSelf(); return; }
        running = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        bp = new WindowManager.LayoutParams(-2, -2, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        bp.gravity = Gravity.TOP | Gravity.START; bp.x = 0; bp.y = 200;
        pp = new WindowManager.LayoutParams(-2, -2, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        pp.gravity = Gravity.TOP | Gravity.START; pp.x = 140; pp.y = 160;

        bubble = new TextView(this);
        bubble.setText("⚡"); bubble.setTextSize(24); bubble.setGravity(Gravity.CENTER); bubble.setTextColor(Color.rgb(42, 18, 0));
        bubble.setBackground(UiUtil.round(UiUtil.ACC, 200, Color.WHITE, 3));
        int sz = UiUtil.dp(bubble, 52);
        bubble.setWidth(sz); bubble.setHeight(sz);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: sx = e.getRawX(); sy = e.getRawY(); ox = bp.x; oy = bp.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - sx) + Math.abs(e.getRawY() - sy) > 12) moved = true;
                        bp.x = ox + (int) (e.getRawX() - sx); bp.y = oy + (int) (e.getRawY() - sy); wm.updateViewLayout(bubble, bp); return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) togglePanel();
                        else if (Booster.num(OverlayService.this, "bubbleStyle", 0) == 1) {
                            int sw = getResources().getDisplayMetrics().widthPixels, half = bubble.getWidth() / 2;
                            bp.x = (bp.x + half < sw / 2) ? -half : sw - half; wm.updateViewLayout(bubble, bp); bubble.setAlpha(.75f);
                        }
                        return true;
                }
                return false;
            }
        });
        wm.addView(bubble, bp);
    }

    private void togglePanel() {
        if (panel != null) { wm.removeView(panel); panel = null; return; }
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(UiUtil.round(Color.argb(240, 20, 22, 36), 40, UiUtil.ACC, 2));
        panel.setPadding(24, 16, 24, 16);
        panel.addView(item("🧹 تنظيف الخلفية", () -> toast("تحرّر ~" + Booster.killBackground(this, null) + " MB")));
        panel.addView(item("🔕 كتم الإشعارات والمكالمات", () -> {
            boolean on = !Booster.dndOn(this);
            toast(Booster.setDnd(this, on) ? (on ? "تم الكتم" : "أُلغي الكتم") : "امنح صلاحية عدم الإزعاج أولاً");
        }));
        panel.addView(item("🔊 وضع صوت الألعاب", () -> { Booster.setGameSound(this, !Booster.gameSoundOn(this)); toast(Booster.gameSoundOn(this) ? "مفعّل" : "أُلغي"); }));
        panel.addView(item("↩ فتح المسرّع", () -> {
            Intent i = new Intent(this, HubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);
        }));
        panel.addView(item("✖ إغلاق الفقاعة", this::stopSelf));
        wm.addView(panel, pp);
    }

    private TextView item(String s, Runnable r) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(15); t.setPadding(16, 14, 16, 14);
        t.setOnClickListener(v -> r.run());
        return t;
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override public void onDestroy() {
        running = false;
        try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) {}
        try { if (bubble != null) wm.removeView(bubble); } catch (Exception ignored) {}
        super.onDestroy();
    }
}

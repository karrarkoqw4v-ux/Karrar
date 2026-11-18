package com.gamebooster.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

final class UiUtil {
    private UiUtil() {}
    static final int BG = Color.rgb(8, 9, 18), CARD = Color.rgb(24, 26, 42), ACC = Color.rgb(255, 165, 31), OK = Color.rgb(60, 220, 140), WARN = Color.rgb(255, 90, 90);

    static void immersive(Activity a) {
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    private static float sc = -1;
    /** معامل تكيّف المقاسات مع حجم الجهاز (هاتف صغير ← 0.72، تابلت ← حتى 1.5). */
    static float scale(android.content.Context c) {
        if (sc < 0) { android.util.DisplayMetrics m = c.getResources().getDisplayMetrics(); float a = Math.min(m.widthPixels, m.heightPixels) / m.density, b = Math.max(m.widthPixels, m.heightPixels) / m.density; sc = Math.max(.72f, Math.min(1.5f, Math.min(a / 360f, b / 760f))); }
        return sc;
    }
    static int dp(View v, int x) { return (int) (x * v.getResources().getDisplayMetrics().density * scale(v.getContext())); }
    static float px(android.content.Context c, float dp) { return dp * c.getResources().getDisplayMetrics().density * scale(c); }

    /** أيقونة خطية من مجموعة Icons كعنصر واجهة. */
    static class IconView extends View {
        final String key; int color; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        IconView(android.content.Context c, String key, int color) { super(c); this.key = key; this.color = color; }
        @Override protected void onDraw(Canvas cv) { float s = Math.min(getWidth(), getHeight()); Icons.draw(cv, key, getWidth() / 2f, getHeight() / 2f, s * .86f, color, p); }
    }
    static IconView icon(android.content.Context c, String key, int color) { return new IconView(c, key, color); }
    static GradientDrawable round(int color, int radiusPx, int strokeColor, int strokePx) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor); return g;
    }
    static TextView text(android.content.Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(c, sp) * Math.min(1.15f, c.getResources().getConfiguration().fontScale)); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t;
    }

    /** حركة ضغط ناعمة: يصغر قليلاً عند اللمس ثم يعود (لا تمنع النقر). */
    static void pressable(final View v) {
        v.setOnTouchListener((x, e) -> {
            switch (e.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN: x.animate().scaleX(.94f).scaleY(.94f).setDuration(90).start(); break;
                case android.view.MotionEvent.ACTION_UP: case android.view.MotionEvent.ACTION_CANCEL: x.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start(); break;
            }
            return false;
        });
    }
}

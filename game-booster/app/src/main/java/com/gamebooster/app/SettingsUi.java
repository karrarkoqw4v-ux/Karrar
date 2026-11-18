package com.gamebooster.app;

import android.content.Context;
import android.graphics.*;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;

/** مكوّنات الواجهة المشتركة لصفحات الإعدادات (نفس ألوان وشكل المرجع). */
final class SettingsUi {
    private SettingsUi() {}
    static final int TOP = Color.rgb(35, 38, 52), SIDE = Color.rgb(34, 38, 56), CONTENT = Color.rgb(18, 20, 29), LINE = Color.rgb(42, 45, 58),
            RED = Color.rgb(232, 55, 42), MUTED = Color.rgb(184, 188, 200), TRACK = Color.rgb(38, 42, 56);

    interface OnToggle { void on(boolean v); }

    /** مفتاح تبديل: مسار داكن وإبهام أحمر (مفعّل) / رمادي (متوقف) */
    static class Toggle extends View {
        boolean on; OnToggle cb; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float pos;
        Toggle(Context c, boolean on, OnToggle cb) { super(c); this.on = on; this.cb = cb; pos = on ? 1 : 0; setOnClickListener(v -> set(!this.on, true)); }
        void set(boolean v, boolean notify) { on = v; slide(); if (notify && cb != null) cb.on(v); }
        private void slide() { android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(pos, on ? 1 : 0); a.setDuration(160); a.addUpdateListener(an -> { pos = (float) an.getAnimatedValue(); invalidate(); }); a.start(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            p.setStyle(Paint.Style.FILL); p.setColor(TRACK); c.drawRoundRect(0, h * .15f, w, h * .85f, h * .35f, h * .35f, p);
            float cx = r + pos * (w - 2 * r);
            p.setColor(Color.rgb((int) (176 + pos * 56), (int) (176 - pos * 121), (int) (176 - pos * 134))); c.drawCircle(cx, r, r * .78f, p);
        }
    }

    /** شريط علوي: زر رجوع بشكل شبه منحرف + العنوان */
    static View topBar(Context c, String title, Runnable back) {
        LinearLayout bar = new LinearLayout(c); bar.setBackgroundColor(TOP); bar.setGravity(Gravity.CENTER_VERTICAL);
        View b = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas cv) {
                float w = getWidth(), h = getHeight();
                p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(44, 48, 66));
                Path pa = new Path(); pa.moveTo(0, 0); pa.lineTo(w, 0); pa.lineTo(w * .72f, h); pa.lineTo(0, h); pa.close(); cv.drawPath(pa, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(h * .09f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(Color.rgb(205, 210, 225));
                float cx = w * .36f, cy = h * .5f, s = h * .2f;
                cv.drawLine(cx + s, cy, cx - s, cy, p); cv.drawLine(cx - s, cy, cx - s * .2f, cy - s * .8f, p); cv.drawLine(cx - s, cy, cx - s * .2f, cy + s * .8f, p);
            }
        };
        b.setOnClickListener(v -> back.run());
        bar.addView(b, new LinearLayout.LayoutParams(UiUtil.dp(bar, 120), -1));
        TextView t = UiUtil.text(c, title, 22, Color.WHITE, true); t.setPadding(UiUtil.dp(bar, 18), 0, 0, 0);
        bar.addView(t);
        return bar;
    }

    /** سطر بمفتاح: عنوان + وصف (اختياري) على اليسار والمفتاح على اليمين */
    static View toggleRow(Context c, String title, String sub, boolean on, OnToggle cb) {
        LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiUtil.dp(row, 18), UiUtil.dp(row, 10), UiUtil.dp(row, 24), UiUtil.dp(row, 10));
        LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UiUtil.text(c, title, 17, Color.WHITE, true));
        if (sub != null) { TextView s = UiUtil.text(c, sub, 12, MUTED, false); s.setPadding(0, UiUtil.dp(row, 3), 0, 0); col.addView(s); }
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(new Toggle(c, on, cb), new LinearLayout.LayoutParams(UiUtil.dp(row, 54), UiUtil.dp(row, 30)));
        LinearLayout wrap = new LinearLayout(c); wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row); View l = new View(c); l.setBackgroundColor(LINE); wrap.addView(l, new LinearLayout.LayoutParams(-1, 2));
        return wrap;
    }

    /** زر اختيار (مختار = أحمر معبّأ، غير مختار = داكن بإطار) */
    static TextView choice(Context c, String s, boolean sel) {
        TextView t = UiUtil.text(c, s, 15, Color.WHITE, true); t.setGravity(Gravity.CENTER);
        t.setBackground(UiUtil.round(sel ? Color.rgb(192, 57, 43) : Color.rgb(31, 35, 56), UiUtil.dp(t, 8), sel ? Color.rgb(229, 115, 101) : Color.rgb(86, 92, 133), 2));
        return t;
    }

    /** بطاقة معاينة داكنة بإطار (أحمر إذا مختارة) */
    static FrameLayout card(Context c, View content, boolean sel) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(UiUtil.round(Color.BLACK, UiUtil.dp(f, 8), sel ? RED : Color.rgb(207, 211, 222), sel ? 3 : 2));
        f.addView(content, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    /** معاينة عدّاد بأحد ثلاثة أشكال */
    static class GaugePreview extends View {
        final int style, color; final String top, label; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        GaugePreview(Context c, int style, int color, String top, String label) { super(c); this.style = style; this.color = color; this.top = top; this.label = label; }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h * .62f, r = Math.min(w, h) * .3f;
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)); p.setTextSize(h * .16f);
            cv.drawText(top, cx, h * .34f, p);
            RectF rf = new RectF(cx - r, cy - r * .32f, cx + r, cy + r * .32f);
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 3; i++) { p.setColor(Color.argb(60 + i * 40, Color.red(color), Color.green(color), Color.blue(color))); p.setStrokeWidth(h * (.05f - i * .012f)); RectF q = new RectF(rf); q.inset(-i * 5, -i * 2); if (style != 1 || i < 2) cv.drawOval(q, p); }
            if (style == 2) { p.setStrokeWidth(3); p.setColor(color); for (int i = 0; i < 12; i++) { double a = Math.PI * 2 * i / 12; cv.drawLine((float) (cx + Math.cos(a) * r * 1.1f), (float) (cy + Math.sin(a) * r * .36f), (float) (cx + Math.cos(a) * r * 1.25f), (float) (cy + Math.sin(a) * r * .42f), p); } }
            p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(h * .08f); p.setTextAlign(Paint.Align.CENTER); cv.drawText(label, cx, h * .93f, p);
        }
    }

    static LinearLayout verticalScroll(Context c, ScrollView[] out) {
        ScrollView sv = new ScrollView(c); sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col); out[0] = sv; return col;
    }
}

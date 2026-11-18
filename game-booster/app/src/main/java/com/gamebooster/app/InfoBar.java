package com.gamebooster.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** الشريط الصغير فوق اللعبة: الوقت، FPS، سرعة الإنترنت، البطارية، المدة المتبقية. */
public class InfoBar extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] icons = {"clock", "fps", "net", "batt", "hour"};
    private final String[] txt = {"--:--", "--FPS", "-- KB/S", "--%", "--h"};
    private float d;

    public InfoBar(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }
    void set(String clock, String fps, String net, String batt, String hours) { txt[0] = clock; txt[1] = fps; txt[2] = net; txt[3] = batt; txt[4] = hours; requestLayout(); invalidate(); }

    private float textW(String s) { p.setTextSize(12 * d); p.setTypeface(Typeface.DEFAULT_BOLD); return p.measureText(s); }
    @Override protected void onMeasure(int w, int h) {
        float tot = 12 * d;
        for (String s : txt) tot += 15 * d + 4 * d + textW(s) + 12 * d;
        setMeasuredDimension((int) tot, (int) (28 * d));
    }
    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(215, 36, 36, 38)); c.drawRoundRect(0, 0, w, h, 8 * d, 8 * d, p);
        float x = 12 * d;
        for (int i = 0; i < 5; i++) {
            Icons.draw(c, icons[i], x + 7.5f * d, h / 2, 15 * d, Color.WHITE, p); x += 15 * d + 4 * d;
            p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextSize(12 * d); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.LEFT);
            c.drawText(txt[i], x, h / 2 + 4.2f * d, p); x += textW(txt[i]) + 12 * d;
        }
    }
}

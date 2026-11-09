package com.karrar.ai;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.widget.TextView;

/** Heavy, iPhone-style typography: Inter for English, Tajawal for Arabic (both open-licence). */
public final class Fonts {
    public static Typeface r = Typeface.DEFAULT, m = Typeface.DEFAULT, b = Typeface.DEFAULT_BOLD, x = Typeface.DEFAULT_BOLD;
    private static String loaded = "";

    public static void init(Context c) {
        String p = Theme.rtl ? "ar" : "en";
        if (p.equals(loaded)) return;
        try {
            android.content.res.AssetManager am = c.getAssets();
            r = Typeface.createFromAsset(am, "fonts/" + p + "_400.ttf");
            m = Typeface.createFromAsset(am, "fonts/" + p + "_500.ttf");
            b = Typeface.createFromAsset(am, "fonts/" + p + "_700.ttf");
            x = Typeface.createFromAsset(am, "fonts/" + p + "_800.ttf");
        } catch (Throwable e) {
            r = m = Typeface.DEFAULT;
            b = x = Typeface.DEFAULT_BOLD;
        }
        loaded = p;
    }

    /** Span that swaps the typeface of part of a text. */
    public static class Span extends MetricAffectingSpan {
        private final Typeface tf;
        public Span(Typeface tf) { this.tf = tf; }
        @Override public void updateDrawState(TextPaint p) { p.setTypeface(tf); }
        @Override public void updateMeasureState(TextPaint p) { p.setTypeface(tf); }
    }

    public static void heavy(TextView t) { t.setTypeface(x); }
}

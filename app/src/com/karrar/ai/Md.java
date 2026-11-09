package com.karrar.ai;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Small Markdown renderer: headings, bold, inline code, bullets, quotes and fenced code blocks. */
public final class Md {
    public static void copy(Context c, String s) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("karrar", s));
        Toast.makeText(c, T.s("copied"), Toast.LENGTH_SHORT).show();
    }

    public static void render(final Activity a, LinearLayout box, String text, boolean selectable) {
        box.removeAllViews();
        String[] lines = text.split("\n", -1);
        StringBuilder para = new StringBuilder();
        StringBuilder code = null;
        String lang = "";
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            if (ln.trim().startsWith("```")) {
                if (code == null) {
                    flush(a, box, para, selectable);
                    code = new StringBuilder();
                    lang = ln.trim().substring(3).trim();
                } else {
                    addCode(a, box, lang, code.toString());
                    code = null;
                }
                continue;
            }
            if (code != null) {
                if (code.length() > 0) code.append('\n');
                code.append(ln);
            } else {
                if (para.length() > 0) para.append('\n');
                para.append(ln);
            }
        }
        if (code != null) addCode(a, box, lang, code.toString());
        flush(a, box, para, selectable);
    }

    private static void flush(Activity a, LinearLayout box, StringBuilder para, boolean selectable) {
        String s = para.toString();
        para.setLength(0);
        if (s.trim().isEmpty()) return;
        TextView tv = new TextView(a);
        tv.setText(block(s));
        tv.setTextColor(Theme.text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f * Theme.font);
        tv.setLineSpacing(0, 1.28f);
        tv.setTextIsSelectable(selectable);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Theme.dp(a, 2);
        lp.bottomMargin = Theme.dp(a, 4);
        box.addView(tv, lp);
    }

    private static CharSequence block(String s) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = s.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            int start = out.length();
            float size = 1f;
            boolean bold = false, quote = false;
            if (ln.startsWith("### ")) { ln = ln.substring(4); size = 1.1f; bold = true; }
            else if (ln.startsWith("## ")) { ln = ln.substring(3); size = 1.2f; bold = true; }
            else if (ln.startsWith("# ")) { ln = ln.substring(2); size = 1.32f; bold = true; }
            else if (ln.startsWith("- ") || ln.startsWith("* ")) { ln = "•  " + ln.substring(2); }
            else if (ln.startsWith("> ")) { ln = ln.substring(2); quote = true; }
            else if (ln.trim().equals("---")) { ln = ""; }
            inline(out, ln);
            int end = out.length();
            if (bold) out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (size != 1f) out.setSpan(new RelativeSizeSpan(size), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (quote) {
                out.setSpan(new ForegroundColorSpan(Theme.text2), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out;
    }

    private static void inline(SpannableStringBuilder out, String ln) {
        int i = 0;
        while (i < ln.length()) {
            if (ln.startsWith("**", i)) {
                int e = ln.indexOf("**", i + 2);
                if (e > 0) {
                    int s = out.length();
                    out.append(ln.substring(i + 2, e));
                    out.setSpan(new StyleSpan(Typeface.BOLD), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = e + 2;
                    continue;
                }
            }
            if (ln.charAt(i) == '`') {
                int e = ln.indexOf('`', i + 1);
                if (e > 0) {
                    int s = out.length();
                    out.append(' ').append(ln.substring(i + 1, e)).append(' ');
                    out.setSpan(new TypefaceSpan("monospace"), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(Theme.surface2), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = e + 1;
                    continue;
                }
            }
            out.append(ln.charAt(i));
            i++;
        }
    }

    private static void addCode(final Activity a, LinearLayout box, String lang, final String code) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.rect(a, Theme.codeBg, 12, Theme.border, 1));

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Theme.dp(a, 14), Theme.dp(a, 6), Theme.dp(a, 8), Theme.dp(a, 6));
        TextView l = new TextView(a);
        l.setText(lang.isEmpty() ? "code" : lang);
        l.setTextColor(Theme.text2);
        l.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        head.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        TextView cp = new TextView(a);
        cp.setText(T.s("copy"));
        cp.setTextColor(Theme.text2);
        cp.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        cp.setPadding(Theme.dp(a, 10), Theme.dp(a, 6), Theme.dp(a, 10), Theme.dp(a, 6));
        cp.setBackground(Theme.rippleRect(a, 0x00000000, 8));
        cp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { copy(a, code); }
        });
        head.addView(cp);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        View div = new View(a);
        div.setBackgroundColor(Theme.border);
        card.addView(div, new LinearLayout.LayoutParams(-1, 1));

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        TextView t = new TextView(a);
        t.setText(code);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(Theme.text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f * Theme.font);
        t.setTextIsSelectable(true);
        t.setTextDirection(View.TEXT_DIRECTION_LTR);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        t.setPadding(Theme.dp(a, 14), Theme.dp(a, 12), Theme.dp(a, 14), Theme.dp(a, 12));
        hs.addView(t);
        card.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Theme.dp(a, 6);
        lp.bottomMargin = Theme.dp(a, 8);
        box.addView(card, lp);
    }
}

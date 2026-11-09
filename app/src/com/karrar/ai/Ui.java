package com.karrar.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared UI builders + themed dialogs. */
public final class Ui {
    public interface Str { void on(String s); }
    public interface Idx { void on(int i); }

    public static int dialogTheme() {
        return Theme.dark ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert;
    }

    public static FrameLayout iconBtn(Activity a, int type, int sizeDp, int iconDp, int color, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(Theme.ripple(Theme.circle(0x00000000)));
        IconView iv = new IconView(a, type, color);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Theme.dp(a, iconDp), Theme.dp(a, iconDp), Gravity.CENTER);
        f.addView(iv, lp);
        f.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(a, sizeDp), Theme.dp(a, sizeDp)));
        if (l != null) f.setOnClickListener(l);
        return f;
    }

    public static IconView iconOf(View btn) { return (IconView) ((FrameLayout) btn).getChildAt(0); }

    public static TextView label(Activity a, String t, float sp, int color, boolean bold) {
        TextView tv = new TextView(a);
        tv.setText(t);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp * 0.86f * Theme.font);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    public static void edit(Activity a, String title, String initial, String hint, boolean multi, boolean secret, final Str cb) {
        final EditText et = new EditText(a);
        et.setText(initial);
        et.setHint(hint);
        et.setTextColor(Theme.text);
        et.setHintTextColor(Theme.text3);
        et.setSelectAllOnFocus(!multi);
        if (multi) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            et.setMinLines(3);
            et.setGravity(Gravity.TOP);
        } else {
            et.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_URI));
            et.setSingleLine(true);
        }
        FrameLayout box = new FrameLayout(a);
        int pad = Theme.dp(a, 20);
        box.setPadding(pad, Theme.dp(a, 8), pad, 0);
        box.addView(et, new FrameLayout.LayoutParams(-1, -2));
        AlertDialog d = new AlertDialog.Builder(a, dialogTheme())
                .setTitle(title).setView(box)
                .setPositiveButton(T.s("save"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) { cb.on(et.getText().toString().trim()); }
                })
                .setNegativeButton(T.s("cancel"), null).create();
        d.show();
        et.requestFocus();
        d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    public static void choose(Activity a, String title, String[] items, int checked, final Idx cb) {
        AlertDialog.Builder b = new AlertDialog.Builder(a, dialogTheme()).setTitle(title);
        if (checked >= 0) {
            b.setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface di, int w) { di.dismiss(); cb.on(w); }
            });
        } else {
            b.setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface di, int w) { cb.on(w); }
            });
        }
        b.setNegativeButton(T.s("cancel"), null).show();
    }

    public static void confirm(Activity a, String msg, final Runnable yes) {
        new AlertDialog.Builder(a, dialogTheme()).setMessage(msg)
                .setPositiveButton(T.s("ok"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) { yes.run(); }
                })
                .setNegativeButton(T.s("cancel"), null).show();
    }
}

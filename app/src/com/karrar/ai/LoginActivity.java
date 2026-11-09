package com.karrar.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class LoginActivity extends Activity {
    private boolean signup = false, busy = false;
    private LinearLayout form;
    private TextView error, title, sub, switchLink;
    private TextView primary;
    private EditText fName, fEmail, fPass, fPass2;

    private int dp(float v) { return Theme.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Store.init(this);
        Store.applyTheme();
        Theme.styleWindow(this);
        build();
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg);
        root.setFitsSystemWindows(true);
        root.setLayoutDirection(Theme.rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(8), dp(24), dp(28));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));

        // language toggle
        TextView lang = Ui.label(this, T.s("l_lang"), 14, Theme.text2, false);
        lang.setPadding(dp(12), dp(10), dp(12), dp(10));
        lang.setBackground(Theme.rippleRect(this, 0x00000000, 12));
        lang.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Store.put("lang", Theme.rtl ? "en" : "ar");
                Store.applyTheme();
                recreate();
            }
        });
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.gravity = Gravity.END;
        col.addView(lang, ll);

        // logo with soft glow
        FrameLayout logoBox = new FrameLayout(this);
        View glow = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Theme.accentSoft, 0x00000000});
        g.setShape(GradientDrawable.OVAL);
        glow.setBackground(g);
        logoBox.addView(glow, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        logoBox.addView(new IconView(this, IconView.LOGO, Theme.accent), new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(-1, dp(120));
        lb.topMargin = dp(12);
        col.addView(logoBox, lb);

        title = Ui.label(this, "", 25, Theme.text, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));
        sub = Ui.label(this, "", 15, Theme.text2, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = dp(6);
        sl.bottomMargin = dp(26);
        col.addView(sub, sl);

        // Google button
        LinearLayout gb = new LinearLayout(this);
        gb.setGravity(Gravity.CENTER);
        gb.setBackground(Theme.ripple(Theme.rect(this, Theme.surface, 16, Theme.border, 1)));
        TextView gl = new TextView(this);
        gl.setText("G");
        gl.setGravity(Gravity.CENTER);
        gl.setTextColor(0xFF4285F4);
        gl.setTypeface(Typeface.DEFAULT_BOLD);
        gl.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        gl.setBackground(Theme.circle(0xFFFFFFFF));
        gb.addView(gl, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView gt = Ui.label(this, T.s("l_google"), 16, Theme.text, true);
        LinearLayout.LayoutParams gtl = new LinearLayout.LayoutParams(-2, -2);
        gtl.setMarginStart(dp(12));
        gb.addView(gt, gtl);
        gb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { googleLogin(); }
        });
        col.addView(gb, new LinearLayout.LayoutParams(-1, dp(52)));

        // divider
        LinearLayout dv = new LinearLayout(this);
        dv.setGravity(Gravity.CENTER_VERTICAL);
        View d1 = new View(this); d1.setBackgroundColor(Theme.border);
        View d2 = new View(this); d2.setBackgroundColor(Theme.border);
        dv.addView(d1, new LinearLayout.LayoutParams(0, 1, 1));
        TextView or = Ui.label(this, T.s("l_or"), 14, Theme.text3, false);
        or.setPadding(dp(14), 0, dp(14), 0);
        dv.addView(or);
        dv.addView(d2, new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout.LayoutParams dvl = new LinearLayout.LayoutParams(-1, dp(40));
        dvl.topMargin = dp(8);
        col.addView(dv, dvl);

        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        col.addView(form, new LinearLayout.LayoutParams(-1, -2));

        error = Ui.label(this, "", 14, Theme.danger, false);
        error.setVisibility(View.GONE);
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, -2);
        el.topMargin = dp(10);
        col.addView(error, el);

        primary = new TextView(this);
        primary.setGravity(Gravity.CENTER);
        primary.setTextColor(0xFFFFFFFF);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        primary.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16 * Theme.font);
        GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF4D6BFE, 0xFF7A8FFF});
        pg.setCornerRadius(dp(16));
        primary.setBackground(Theme.ripple(pg));
        primary.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { submit(); }
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(52));
        pl.topMargin = dp(16);
        col.addView(primary, pl);

        switchLink = Ui.label(this, "", 15, Theme.accent, true);
        switchLink.setGravity(Gravity.CENTER);
        switchLink.setPadding(0, dp(14), 0, dp(14));
        switchLink.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { signup = !signup; render(); }
        });
        LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(-1, -2);
        swl.topMargin = dp(8);
        col.addView(switchLink, swl);

        TextView guest = Ui.label(this, T.s("l_guest"), 14, Theme.text3, false);
        guest.setGravity(Gravity.CENTER);
        guest.setPadding(0, dp(8), 0, dp(8));
        guest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Accounts.guest(); enter(); }
        });
        col.addView(guest, new LinearLayout.LayoutParams(-1, -2));

        render();
        setContentView(root);
        col.setAlpha(0f);
        col.setTranslationY(dp(18));
        col.animate().alpha(1f).translationY(0).setDuration(380).start();
    }

    private void render() {
        form.removeAllViews();
        error.setVisibility(View.GONE);
        title.setText(signup ? T.s("l_signup") : T.s("l_welcome"));
        sub.setText(signup ? T.s("l_sub_up") : T.s("l_sub"));
        primary.setText(signup ? T.s("l_signup") : T.s("l_login"));
        switchLink.setText(signup ? T.s("l_to_login") : T.s("l_to_signup"));
        fName = null;
        if (signup) fName = field(IconView.USER, T.s("l_name"), InputType.TYPE_TEXT_FLAG_CAP_WORDS, false);
        fEmail = field(IconView.CHAT, T.s("l_email"), InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false);
        fPass = field(IconView.KEY, T.s("l_pass"), InputType.TYPE_TEXT_VARIATION_PASSWORD, true);
        fPass2 = signup ? field(IconView.CHECK, T.s("l_pass2"), InputType.TYPE_TEXT_VARIATION_PASSWORD, true) : null;
        form.setAlpha(0f);
        form.animate().alpha(1f).setDuration(220).start();
    }

    private EditText field(int icon, String hint, int type, final boolean pass) {
        final LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), 0, dp(8), 0);
        box.setBackground(Theme.rect(this, Theme.surface, 14, Theme.border, 1));
        box.addView(new IconView(this, icon, Theme.text3), new LinearLayout.LayoutParams(dp(20), dp(20)));
        final EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Theme.text3);
        et.setTextColor(Theme.text);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15 * Theme.font);
        et.setBackground(null);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | type);
        et.setPadding(dp(12), 0, dp(12), 0);
        et.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(0, -1, 1);
        box.addView(et, el);
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean f) {
                box.setBackground(Theme.rect(LoginActivity.this, Theme.surface, 14, f ? Theme.accent : Theme.border, f ? 1.5f : 1));
            }
        });
        if (pass) {
            final TextView eye = Ui.label(this, T.s("l_show"), 13, Theme.text2, false);
            eye.setPadding(dp(8), dp(8), dp(8), dp(8));
            eye.setOnClickListener(new View.OnClickListener() {
                boolean shown = false;
                public void onClick(View v) {
                    shown = !shown;
                    int sel = et.getSelectionStart();
                    et.setTransformationMethod(shown ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
                    et.setSelection(Math.max(0, sel));
                    eye.setText(shown ? T.s("l_hide") : T.s("l_show"));
                }
            });
            box.addView(eye);
        }
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(52));
        bl.bottomMargin = dp(12);
        form.addView(box, bl);
        return et;
    }

    private void showError(String key) {
        String t = T.s(key);
        error.setText(t.equals(key) ? key : t);
        error.setVisibility(View.VISIBLE);
        error.setAlpha(0f);
        error.animate().alpha(1f).setDuration(180).start();
        form.animate().translationX(dp(6)).setDuration(60).withEndAction(new Runnable() {
            public void run() { form.animate().translationX(0).setDuration(90).start(); }
        }).start();
    }

    private void submit() {
        if (busy) return;
        error.setVisibility(View.GONE);
        String err;
        if (signup) {
            err = Accounts.signUp(fName.getText().toString(), fEmail.getText().toString(),
                    fPass.getText().toString(), fPass2.getText().toString());
        } else {
            err = Accounts.login(fEmail.getText().toString(), fPass.getText().toString());
        }
        if (err != null) showError(err); else enter();
    }

    private void googleLogin() {
        if (busy) return;
        busy = true;
        error.setVisibility(View.GONE);
        primary.setAlpha(0.5f);
        sub.setText(T.s("l_wait"));
        GoogleAuth.start(this, new GoogleAuth.Cb() {
            public void done(String err, String name, String email) {
                busy = false;
                primary.setAlpha(1f);
                sub.setText(signup ? T.s("l_sub_up") : T.s("l_sub"));
                if (err != null) { showError(err); return; }
                Accounts.google(name, email);
                enter();
            }
        });
    }

    private void enter() {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (getCurrentFocus() != null) im.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}

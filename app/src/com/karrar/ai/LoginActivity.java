package com.karrar.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;

public class LoginActivity extends Activity {
    private boolean signup = false, busy = false;
    private LinearLayout form, col;
    private TextView error, title, sub, switchLink, primary;
    private EditText fName, fEmail, fPass, fPass2;
    private Fx.Dots dots;

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

        // living background
        int a1 = Theme.dark ? 0x664D6BFE : 0x334D6BFE;
        int a2 = Theme.dark ? 0x558B5CF6 : 0x228B5CF6;
        int a3 = Theme.dark ? 0x3322D3EE : 0x1A22D3EE;
        root.addView(new Fx.Aurora(this, a1, a2, a3), new FrameLayout.LayoutParams(-1, -1));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(8), dp(24), dp(28));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));

        long d = 0;
        TextView lang = Ui.label(this, T.s("l_lang"), 14, Theme.text2, true);
        lang.setPadding(dp(14), dp(10), dp(14), dp(10));
        lang.setBackground(Theme.ripple(Theme.rect(this, Theme.alpha(Theme.surface, 150), 14)));
        lang.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Store.put("lang", Theme.rtl ? "en" : "ar");
                Store.applyTheme();
                recreate();
            }
        });
        Fx.press(lang);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.gravity = Gravity.END;
        col.addView(lang, ll);

        // logo + pulsing glow
        FrameLayout logoBox = new FrameLayout(this);
        View glow = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(dp(70));
        g.setColors(new int[]{Theme.alpha(Theme.accent, 120), 0x00000000});
        glow.setBackground(g);
        logoBox.addView(glow, new FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER));
        Fx.breathe(glow, 1.22f);
        IconView logo = new IconView(this, IconView.LOGO, Theme.accent);
        logoBox.addView(logo, new FrameLayout.LayoutParams(dp(70), dp(70), Gravity.CENTER));
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(-1, dp(140));
        lb.topMargin = dp(4);
        col.addView(logoBox, lb);
        Fx.enter(logoBox, d); d += 70;
        Fx.pop(logo);

        title = Ui.label(this, "", 30, Theme.text, true);
        Fonts.heavy(title);
        title.setGravity(Gravity.CENTER);
        Fx.gradientText(title);
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));
        Fx.enter(title, d); d += 70;
        sub = Ui.label(this, "", 16, Theme.text2, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = dp(8);
        sl.bottomMargin = dp(28);
        col.addView(sub, sl);
        Fx.enter(sub, d); d += 70;

        // official-style white Google button
        LinearLayout gb = new LinearLayout(this);
        gb.setGravity(Gravity.CENTER);
        GradientDrawable gbg = Theme.rect(this, 0xFFFFFFFF, 28, 0xFFDADCE0, 1);
        gb.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22000000), gbg, null));
        gb.setElevation(dp(4));
        ImageView gi = new ImageView(this);
        try {
            InputStream in = getAssets().open("google_g.png");
            Bitmap bm = BitmapFactory.decodeStream(in);
            in.close();
            gi.setImageBitmap(bm);
        } catch (Exception ignored) { }
        gb.addView(gi, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView gt = Ui.label(this, T.s("l_google"), 16, 0xFF1F1F1F, true);
        LinearLayout.LayoutParams gtl = new LinearLayout.LayoutParams(-2, -2);
        gtl.setMarginStart(dp(12));
        gb.addView(gt, gtl);
        gb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { googleLogin(); }
        });
        Fx.press(gb);
        LinearLayout.LayoutParams gbl = new LinearLayout.LayoutParams(-1, dp(54));
        gbl.bottomMargin = dp(2);
        col.addView(gb, gbl);
        Fx.enter(gb, d); d += 70;

        LinearLayout dv = new LinearLayout(this);
        dv.setGravity(Gravity.CENTER_VERTICAL);
        View d1 = new View(this); d1.setBackgroundColor(Theme.border);
        View d2 = new View(this); d2.setBackgroundColor(Theme.border);
        dv.addView(d1, new LinearLayout.LayoutParams(0, 1, 1));
        TextView or = Ui.label(this, T.s("l_or"), 14, Theme.text3, true);
        or.setPadding(dp(14), 0, dp(14), 0);
        dv.addView(or);
        dv.addView(d2, new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout.LayoutParams dvl = new LinearLayout.LayoutParams(-1, dp(44));
        dvl.topMargin = dp(8);
        col.addView(dv, dvl);
        Fx.enter(dv, d); d += 70;

        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        col.addView(form, new LinearLayout.LayoutParams(-1, -2));

        error = Ui.label(this, "", 14, Theme.danger, true);
        error.setVisibility(View.GONE);
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, -2);
        el.topMargin = dp(10);
        col.addView(error, el);

        FrameLayout pf = new FrameLayout(this);
        primary = new TextView(this);
        primary.setGravity(Gravity.CENTER);
        primary.setTextColor(0xFFFFFFFF);
        primary.setTypeface(Fonts.x);
        primary.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16.5f * Theme.font);
        primary.setBackground(Theme.ripple(Theme.gradRect(this, 28)));
        primary.setElevation(dp(6));
        primary.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { submit(); }
        });
        Fx.press(primary);
        pf.addView(primary, new FrameLayout.LayoutParams(-1, -1));
        dots = new Fx.Dots(this);
        dots.setVisibility(View.GONE);
        pf.addView(dots, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(54));
        pl.topMargin = dp(18);
        col.addView(pf, pl);
        Fx.enter(pf, d + 140);

        switchLink = Ui.label(this, "", 15, Theme.accent, true);
        switchLink.setGravity(Gravity.CENTER);
        switchLink.setPadding(0, dp(14), 0, dp(14));
        switchLink.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { signup = !signup; render(); }
        });
        LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(-1, -2);
        swl.topMargin = dp(10);
        col.addView(switchLink, swl);
        Fx.enter(switchLink, d + 200);

        TextView guest = Ui.label(this, T.s("l_guest"), 14, Theme.text3, true);
        guest.setGravity(Gravity.CENTER);
        guest.setPadding(0, dp(8), 0, dp(8));
        guest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Accounts.guest(); enter(); }
        });
        col.addView(guest, new LinearLayout.LayoutParams(-1, -2));
        Fx.enter(guest, d + 260);

        render();
        setContentView(root);
    }

    private void render() {
        form.removeAllViews();
        error.setVisibility(View.GONE);
        title.setText(signup ? T.s("l_signup") : T.s("l_welcome"));
        sub.setText(signup ? T.s("l_sub_up") : T.s("l_sub"));
        primary.setText(signup ? T.s("l_signup") : T.s("l_login"));
        switchLink.setText(signup ? T.s("l_to_login") : T.s("l_to_signup"));
        fName = null;
        long d = 0;
        if (signup) { fName = field(IconView.USER, T.s("l_name"), InputType.TYPE_TEXT_FLAG_CAP_WORDS, false, d); d += 60; }
        fEmail = field(IconView.CHAT, T.s("l_email"), InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false, d); d += 60;
        fPass = field(IconView.KEY, T.s("l_pass"), InputType.TYPE_TEXT_VARIATION_PASSWORD, true, d); d += 60;
        fPass2 = signup ? field(IconView.CHECK, T.s("l_pass2"), InputType.TYPE_TEXT_VARIATION_PASSWORD, true, d) : null;
    }

    private EditText field(int icon, String hint, int type, final boolean pass, long delay) {
        final LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16), 0, dp(10), 0);
        box.setBackground(Theme.rect(this, Theme.alpha(Theme.surface, 235), 16, Theme.border, 1));
        final IconView ic = new IconView(this, icon, Theme.text3);
        box.addView(ic, new LinearLayout.LayoutParams(dp(20), dp(20)));
        final EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Theme.text3);
        et.setTextColor(Theme.text);
        et.setTypeface(Fonts.m);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f * Theme.font);
        et.setBackground(null);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | type);
        et.setPadding(dp(12), 0, dp(12), 0);
        et.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        box.addView(et, new LinearLayout.LayoutParams(0, -1, 1));
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean f) {
                box.setBackground(Theme.rect(LoginActivity.this, Theme.alpha(Theme.surface, 235), 16, f ? Theme.accent : Theme.border, f ? 1.6f : 1));
                ic.setColor(f ? Theme.accent : Theme.text3);
                box.animate().scaleX(f ? 1.015f : 1f).scaleY(f ? 1.015f : 1f).setDuration(160).start();
            }
        });
        if (pass) {
            final TextView eye = Ui.label(this, T.s("l_show"), 13, Theme.text2, true);
            eye.setPadding(dp(10), dp(10), dp(10), dp(10));
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
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(54));
        bl.bottomMargin = dp(12);
        form.addView(box, bl);
        Fx.enter(box, delay);
        return et;
    }

    private void showError(String key) {
        String t = T.s(key);
        error.setText(t.equals(key) ? key : t);
        error.setVisibility(View.VISIBLE);
        error.setAlpha(0f);
        error.animate().alpha(1f).setDuration(180).start();
        // shake
        android.animation.ObjectAnimator.ofFloat(form, "translationX", 0, dp(10), -dp(10), dp(7), -dp(7), 0).setDuration(380).start();
    }

    private void setBusy(boolean b) {
        busy = b;
        primary.setText(b ? "" : (signup ? T.s("l_signup") : T.s("l_login")));
        dots.setVisibility(b ? View.VISIBLE : View.GONE);
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
        setBusy(true);
        error.setVisibility(View.GONE);
        sub.setText(T.s("l_wait"));
        GoogleAuth.start(this, new GoogleAuth.Cb() {
            public void done(String err, String name, String email) {
                setBusy(false);
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

package com.karrar.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

public class SettingsActivity extends Activity {
    private static final int ICON_G = -1;
    private LinearLayout page;
    private String pageId = "main";
    private int secIdx = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private static final String[][] PRESETS = {
            {"DeepSeek", "https://api.deepseek.com", "deepseek-chat", "deepseek-reasoner"},
            {"OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "o3-mini"},
            {"OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat", "deepseek/deepseek-r1"},
            {"Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "deepseek-r1-distill-llama-70b"},
            {"Ollama (local)", "http://127.0.0.1:11434/v1", "llama3.2", "deepseek-r1"},
    };

    private int dp(float v) { return Theme.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Store.init(this);
        Store.applyTheme();
        Theme.styleWindow(this, Theme.bg2);
        String p = getIntent().getStringExtra("page");
        if (p != null) pageId = p;
        build();
    }

    private void open(String p) {
        Intent i = new Intent(this, SettingsActivity.class);
        i.putExtra("page", p);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private String pageTitle() {
        if (pageId.equals("account")) return T.s("s_acc_settings");
        if (pageId.equals("data")) return T.s("s_data_ctl");
        if (pageId.equals("custom")) return T.s("s_custom2");
        if (pageId.equals("font")) return T.s("s_font");
        return T.s("settings");
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg2);
        root.setFitsSystemWindows(true);
        root.setLayoutDirection(Theme.rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // header: round back button + centred title
        FrameLayout head = new FrameLayout(this);
        FrameLayout back = new FrameLayout(this);
        back.setBackground(Theme.ripple(Theme.circle(Theme.surface2)));
        back.addView(new IconView(this, Theme.rtl ? IconView.CHEV_RIGHT : IconView.CHEV_LEFT, Theme.text),
                new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        Fx.press(back);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER_VERTICAL | Gravity.START);
        bl.setMarginStart(dp(14));
        head.addView(back, bl);
        TextView t = Ui.label(this, pageTitle(), 19, Theme.text, true);
        t.setGravity(Gravity.CENTER);
        head.addView(t, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        col.addView(head, new LinearLayout.LayoutParams(-1, dp(62)));

        if (pageId.equals("font")) {
            fontPage(col);
        } else {
            ScrollView sv = new ScrollView(this);
            sv.setVerticalScrollBarEnabled(false);
            page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(dp(16), dp(2), dp(16), dp(30));
            sv.addView(page, new FrameLayout.LayoutParams(-1, -2));
            col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
            if (pageId.equals("account")) accountPage();
            else if (pageId.equals("data")) dataPage();
            else if (pageId.equals("custom")) customPage();
            else mainPage();
        }
        setContentView(root);
    }

    private void rebuild() { Store.version++; recreate(); }

    // ------------------------------------------------------------------ builders
    private LinearLayout section(String label) {
        if (label != null) {
            TextView h = Ui.label(this, label, 14.5f, Theme.text2, false);
            h.setTypeface(Fonts.r);
            h.setPadding(dp(8), dp(22), dp(8), dp(8));
            h.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            page.addView(h, new LinearLayout.LayoutParams(-1, -2));
            Fx.enter(h, secIdx * 70L);
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.rect(this, Theme.card, 26));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        if (label == null) lp.topMargin = dp(22);
        page.addView(card, lp);
        Fx.enter(card, secIdx * 70L + 30);
        secIdx++;
        return card;
    }

    private void note(String s) {
        TextView n = Ui.label(this, s, 14, Theme.text2, false);
        n.setTypeface(Fonts.r);
        n.setLineSpacing(0, 1.25f);
        n.setPadding(dp(10), dp(12), dp(10), 0);
        n.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        page.addView(n, new LinearLayout.LayoutParams(-1, -2));
    }

    private View iconView(int icon) {
        if (icon == ICON_G) {
            ImageView iv = new ImageView(this);
            try {
                InputStream in = getAssets().open("google_g.png");
                Bitmap bm = BitmapFactory.decodeStream(in);
                in.close();
                iv.setImageBitmap(bm);
            } catch (Exception ignored) { }
            return iv;
        }
        return new IconView(this, icon, Theme.text);
    }

    private TextView row(LinearLayout card, int icon, String title, String value, final Runnable click) {
        return row(card, icon, title, value, click, Theme.text);
    }

    private TextView row(LinearLayout card, int icon, String title, String value, final Runnable click, int titleColor) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(18), dp(14), dp(18), dp(14));
        r.setMinimumHeight(dp(66));
        r.setBackground(Theme.rippleRect(this, 0x00000000, 26));
        r.addView(iconView(icon), new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView tt = Ui.label(this, title, 17, titleColor, true);
        tt.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
        tl.setMarginStart(dp(16));
        r.addView(tt, tl);
        TextView vt = Ui.label(this, value == null ? "" : value, 16, Theme.text2, true);
        vt.setSingleLine(true);
        vt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        vt.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.42f));
        vt.setVisibility(value == null || value.isEmpty() ? View.GONE : View.VISIBLE);
        r.addView(vt);
        if (click != null) {
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(18), dp(18));
            cl.setMarginStart(dp(8));
            r.addView(new IconView(this, Theme.rtl ? IconView.CHEV_LEFT : IconView.CHEV_RIGHT, Theme.text3), cl);
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { click.run(); }
            });
        }
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return vt;
    }

    private Switch iosSwitch(boolean checked) {
        Switch sw = new Switch(this);
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFF34C759);
        on.setCornerRadius(dp(16));
        on.setSize(dp(54), dp(32));
        GradientDrawable off = new GradientDrawable();
        off.setColor(Theme.surface2);
        off.setCornerRadius(dp(16));
        off.setSize(dp(54), dp(32));
        StateListDrawable track = new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked}, on);
        track.addState(new int[]{}, off);
        GradientDrawable th = new GradientDrawable();
        th.setShape(GradientDrawable.OVAL);
        th.setColor(0xFFFFFFFF);
        th.setSize(dp(30), dp(30));
        sw.setTrackDrawable(track);
        sw.setThumbDrawable(new InsetDrawable(th, dp(1)));
        sw.setChecked(checked);
        return sw;
    }

    private void toggle(LinearLayout card, int icon, String title, String sub, final String key, boolean def, final boolean uiChange) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(18), dp(12), dp(18), dp(12));
        r.setMinimumHeight(dp(66));
        r.addView(iconView(icon), new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(0, -2, 1);
        xl.setMarginStart(dp(16));
        r.addView(txt, xl);
        TextView tt = Ui.label(this, title, 17, Theme.text, true);
        tt.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        txt.addView(tt);
        if (sub != null) {
            TextView st = Ui.label(this, sub, 13.5f, Theme.text2, false);
            st.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            txt.addView(st);
        }
        final Switch sw = iosSwitch(Store.bool(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Store.putBool(key, on);
                if (uiChange) rebuild();
            }
        });
        r.addView(sw);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sw.toggle(); }
        });
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    private String mask(String k) {
        if (k.isEmpty()) return T.s("s_not_set");
        if (k.length() <= 8) return "••••••••";
        return k.substring(0, 3) + "••••••••" + k.substring(k.length() - 4);
    }

    private String maskEmail(String e) {
        int at = e.indexOf('@');
        if (at < 5) return e;
        return e.substring(0, 4) + "••••••" + e.substring(at - 2);
    }

    // ------------------------------------------------------------------ main page (reference layout)
    private void mainPage() {
        LinearLayout c = section(T.s("s_profile"));
        row(c, IconView.USER, T.s("s_acc_settings"), null, new Runnable() { public void run() { open("account"); } });
        row(c, IconView.DATABASE, T.s("s_data_ctl"), null, new Runnable() { public void run() { open("data"); } });

        c = section(T.s("s_app2"));
        row(c, IconView.GLOBE, T.s("s_lang"), Theme.rtl ? "العربية" : "English", new Runnable() {
            public void run() {
                Ui.choose(SettingsActivity.this, T.s("s_lang"), new String[]{"العربية", "English"}, Theme.rtl ? 0 : 1, new Ui.Idx() {
                    public void on(int i) { Store.put("lang", i == 0 ? "ar" : "en"); rebuild(); }
                });
            }
        });
        row(c, IconView.MOON, T.s("s_theme"), Theme.dark ? T.s("s_dark") : T.s("s_light"), new Runnable() {
            public void run() {
                Ui.choose(SettingsActivity.this, T.s("s_theme"), new String[]{T.s("s_dark"), T.s("s_light")}, Theme.dark ? 0 : 1, new Ui.Idx() {
                    public void on(int i) { Store.put("theme", i == 0 ? "dark" : "light"); rebuild(); }
                });
            }
        });
        row(c, IconView.TEXTSIZE, T.s("s_font"), null, new Runnable() { public void run() { open("font"); } });
        row(c, IconView.WAND, T.s("s_custom2"), null, new Runnable() { public void run() { open("custom"); } });

        c = section(T.s("s_audio"));
        row(c, IconView.MIC, T.s("s_main_lang"), "en".equals(Store.get("voice_lang", "ar")) ? "English" : "العربية", new Runnable() {
            public void run() {
                Ui.choose(SettingsActivity.this, T.s("s_main_lang"), new String[]{"العربية", "English"}, "en".equals(Store.get("voice_lang", "ar")) ? 1 : 0, new Ui.Idx() {
                    public void on(int i) { Store.put("voice_lang", i == 0 ? "ar" : "en"); rebuild(); }
                });
            }
        });
        toggle(c, IconView.SOUND_ON, T.s("s_sound"), T.s("s_sound_sub"), "sound", false, false);
        note(T.s("s_audio_note"));

        c = section(T.s("s_about2"));
        row(c, IconView.INFO, T.s("s_update"), "1.0(1)", new Runnable() {
            public void run() { Toast.makeText(SettingsActivity.this, T.s("s_up_to_date"), Toast.LENGTH_SHORT).show(); }
        });
        row(c, IconView.DOC, T.s("s_terms"), null, new Runnable() {
            public void run() { info(T.s("s_terms"), T.s("s_terms_body")); }
        });

        c = section(null);
        row(c, IconView.HELP, T.s("s_help"), null, new Runnable() {
            public void run() { info(T.s("s_help"), T.s("s_help_body")); }
        });

        c = section(null);
        row(c, IconView.LOGOUT, T.s("s_logout"), null, new Runnable() {
            public void run() {
                Ui.confirm(SettingsActivity.this, T.s("s_logout_q"), new Runnable() {
                    public void run() {
                        Accounts.logout();
                        Intent i = new Intent(SettingsActivity.this, LoginActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    }
                });
            }
        });

        TextView foot = Ui.label(this, T.s("s_footer"), 13.5f, Theme.text2, false);
        foot.setGravity(Gravity.CENTER);
        foot.setTypeface(Fonts.r);
        foot.setLineSpacing(0, 1.25f);
        foot.setPadding(dp(20), dp(26), dp(20), 0);
        page.addView(foot, new LinearLayout.LayoutParams(-1, -2));
    }

    private void info(String title, String body) {
        new AlertDialog.Builder(this, Ui.dialogTheme()).setTitle(title).setMessage(body).setPositiveButton(T.s("ok"), null).show();
    }

    // ------------------------------------------------------------------ account
    private void accountPage() {
        String email = Store.get("user_email", "");
        LinearLayout c = section(T.s("s_profile"));
        row(c, IconView.USER, T.s("s_name"), Store.get("name", ""), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_name"), Store.get("name", ""), "", false, false, new Ui.Str() {
                    public void on(String s) { if (!s.isEmpty()) { Store.put("name", s); rebuild(); } }
                });
            }
        });
        row(c, IconView.MAIL, T.s("s_email"), email.isEmpty() ? T.s("guest") : maskEmail(email), null);
        if (Store.bool("user_google", false)) row(c, ICON_G, "Google", T.s("s_connected2"), null);

        c = section(T.s("s_security"));
        row(c, IconView.PHONE, T.s("s_devices"), null, new Runnable() {
            public void run() { info(T.s("s_devices"), "• " + T.s("s_this_device") + " (" + android.os.Build.MODEL + ")"); }
        });

        TextView del = Ui.label(this, T.s("s_delete_acc"), 18, 0xFFFF5A5F, true);
        del.setGravity(Gravity.CENTER);
        del.setPadding(0, dp(40), 0, dp(10));
        del.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Ui.confirm(SettingsActivity.this, T.s("s_delete_acc_q"), new Runnable() {
                    public void run() { deleteAccount(); }
                });
            }
        });
        page.addView(del, new LinearLayout.LayoutParams(-1, -2));
    }

    private void deleteAccount() {
        String email = Store.get("user_email", "");
        try {
            org.json.JSONObject a = new org.json.JSONObject(Store.get("accounts", "{}"));
            a.remove(email);
            Store.put("accounts", a.toString());
        } catch (Exception ignored) { }
        Store.convs.clear();
        Store.save();
        Store.sp.edit().remove("convs_" + email).apply();
        Accounts.logout();
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }

    // ------------------------------------------------------------------ data controls
    private void dataPage() {
        LinearLayout c = section(T.s("s_ctl_hist"));
        toggle(c, IconView.DATABASE, T.s("s_save_hist"), null, "save_hist", true, false);
        row(c, IconView.SHARE, T.s("s_export"), null, new Runnable() {
            public void run() {
                StringBuilder sb = new StringBuilder();
                for (Store.Conv cv : Store.convs) {
                    sb.append("# ").append(cv.title).append("\n");
                    for (Store.Msg m : cv.msgs) sb.append(m.role.equals("user") ? "User: " : "AI: ").append(m.content).append("\n\n");
                }
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, sb.toString());
                startActivity(Intent.createChooser(i, T.s("s_export")));
            }
        });
        row(c, IconView.TRASH, T.s("s_clear"), null, new Runnable() {
            public void run() {
                Ui.confirm(SettingsActivity.this, T.s("s_clear_q"), new Runnable() {
                    public void run() {
                        Store.convs.clear();
                        Store.put("cur", "");
                        Store.save();
                        Store.version++;
                        Toast.makeText(SettingsActivity.this, "✓", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, 0xFFFF5A5F);
    }

    // ------------------------------------------------------------------ personalization = AI + connections
    private void customPage() {
        String cur = T.s("s_custom");
        for (String[] p : PRESETS) if (Store.baseUrl().equals(p[1])) cur = p[0];
        LinearLayout c = section(T.s("s_connect"));
        row(c, IconView.LINK, T.s("s_provider"), cur, new Runnable() { public void run() { pickPreset(); } });
        row(c, IconView.KEY, T.s("s_key"), mask(Store.get("api_key", "")), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_key"), Store.get("api_key", ""), "sk-...", false, true, new Ui.Str() {
                    public void on(String s) { Store.put("api_key", s); rebuild(); }
                });
            }
        });
        row(c, IconView.LINK, T.s("s_url"), Store.baseUrl(), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_url"), Store.baseUrl(), "https://api.deepseek.com", false, false, new Ui.Str() {
                    public void on(String s) { if (!s.isEmpty()) { Store.put("base_url", s); rebuild(); } }
                });
            }
        });
        final TextView[] testV = new TextView[1];
        testV[0] = row(c, IconView.CHECK, T.s("s_test"), null, new Runnable() {
            public void run() {
                testV[0].setVisibility(View.VISIBLE);
                testV[0].setTextColor(Theme.text2);
                testV[0].setText(T.s("s_testing"));
                new Thread(new Runnable() {
                    public void run() {
                        final String r = Api.test();
                        ui.post(new Runnable() {
                            public void run() {
                                testV[0].setTextColor(r == null ? 0xFF34C759 : Theme.danger);
                                testV[0].setText(r == null ? T.s("s_ok") : r);
                                testV[0].setSingleLine(false);
                            }
                        });
                    }
                }).start();
            }
        });

        c = section(T.s("s_ai"));
        row(c, IconView.CHAT, T.s("s_model"), Store.get("model", "deepseek-chat"), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_model"), Store.get("model", "deepseek-chat"), "deepseek-chat", false, false, new Ui.Str() {
                    public void on(String s) { if (!s.isEmpty()) { Store.put("model", s); rebuild(); } }
                });
            }
        });
        row(c, IconView.ATOM, T.s("s_think_model"), Store.get("think_model", "deepseek-reasoner"), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_think_model"), Store.get("think_model", "deepseek-reasoner"), "deepseek-reasoner", false, false, new Ui.Str() {
                    public void on(String s) { if (!s.isEmpty()) { Store.put("think_model", s); rebuild(); } }
                });
            }
        });
        String sysv = Store.get("system", "");
        row(c, IconView.SPARK, T.s("s_system"), sysv.isEmpty() ? T.s("s_none") : sysv.replace('\n', ' '), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_system"), Store.get("system", ""), "", true, false, new Ui.Str() {
                    public void on(String s) { Store.put("system", s); rebuild(); }
                });
            }
        });
        final String[] tv = {"0.2", "0.5", "0.7", "1.0", "1.3"};
        int ti = 2;
        for (int i = 0; i < tv.length; i++) if (tv[i].equals(Store.get("temp", "0.7"))) ti = i;
        final int tiF = ti;
        row(c, IconView.SETTINGS, T.s("s_temp"), Store.get("temp", "0.7"), new Runnable() {
            public void run() {
                Ui.choose(SettingsActivity.this, T.s("s_temp"), tv, tiF, new Ui.Idx() {
                    public void on(int i) { Store.put("temp", tv[i]); rebuild(); }
                });
            }
        });
        toggle(c, IconView.ATOM, T.s("s_def_think"), null, "def_think", false, false);
        toggle(c, IconView.GLOBE, T.s("s_def_search"), T.s("s_search_src_sub"), "def_search", false, false);

        c = section(T.s("s_app"));
        toggle(c, IconView.SPARK, T.s("s_stream"), null, "stream", true, false);
        toggle(c, IconView.SETTINGS, T.s("s_haptic"), null, "haptic", true, false);
        toggle(c, IconView.WAND, T.s("s_effects"), null, "fx", false, true);

        c = section(T.s("s_google"));
        row(c, IconView.KEY, "Client ID", GoogleAuth.CLIENT_ID.substring(0, 12) + "…", null);
        row(c, IconView.LINK, T.s("s_gsecret"), Store.get("google_secret", "").isEmpty() ? T.s("s_none") : mask(Store.get("google_secret", "")), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_gsecret"), Store.get("google_secret", ""), "GOCSPX-...", false, true, new Ui.Str() {
                    public void on(String s) { Store.put("google_secret", s); rebuild(); }
                });
            }
        });
    }

    private void pickPreset() {
        String[] names = new String[PRESETS.length];
        for (int i = 0; i < PRESETS.length; i++) names[i] = PRESETS[i][0];
        Ui.choose(this, T.s("s_provider"), names, -1, new Ui.Idx() {
            public void on(int i) {
                Store.put("base_url", PRESETS[i][1]);
                Store.put("model", PRESETS[i][2]);
                Store.put("think_model", PRESETS[i][3]);
                rebuild();
            }
        });
    }

    // ------------------------------------------------------------------ font size page (preview + slider)
    private void fontPage(LinearLayout col) {
        final LinearLayout previewBox = new LinearLayout(this);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        previewBox.setPadding(dp(20), dp(24), dp(20), dp(10));
        final TextView bubble = new TextView(this);
        bubble.setText(T.s("s_preview"));
        bubble.setTextColor(Theme.text);
        bubble.setTypeface(Fonts.m);
        bubble.setBackground(Theme.rect(this, Theme.userBubble, 26));
        bubble.setPadding(dp(22), dp(16), dp(22), dp(16));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.gravity = Gravity.END;
        previewBox.addView(bubble, bl);
        final TextView p1 = new TextView(this);
        p1.setText(T.s("s_preview1"));
        final TextView p2 = new TextView(this);
        p2.setText(T.s("s_preview2"));
        for (TextView p : new TextView[]{p1, p2}) {
            p.setTextColor(Theme.text);
            p.setTypeface(Fonts.m);
            p.setLineSpacing(0, 1.3f);
            p.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(-1, -2);
        l1.topMargin = dp(28);
        previewBox.addView(p1, l1);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(-1, -2);
        l2.topMargin = dp(20);
        previewBox.addView(p2, l2);
        col.addView(previewBox, new LinearLayout.LayoutParams(-1, 0, 1));

        // bottom sheet
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(Theme.card);
        float r = dp(30);
        sg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(sg);
        sheet.setPadding(dp(22), dp(24), dp(22), dp(34));

        LinearLayout sysRow = new LinearLayout(this);
        sysRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView st = Ui.label(this, T.s("s_follow_sys"), 18, Theme.text, true);
        sysRow.addView(st, new LinearLayout.LayoutParams(0, -2, 1));
        final Switch sw = iosSwitch(Store.bool("font_sys", false));
        sysRow.addView(sw);
        sheet.addView(sysRow, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout labels = new LinearLayout(this);
        labels.setGravity(Gravity.BOTTOM);
        TextView small = Ui.label(this, "A", 16, Theme.text, true);
        final TextView mid = Ui.label(this, T.s("s_default"), 15, Theme.text, false);
        mid.setGravity(Gravity.CENTER);
        TextView big = Ui.label(this, "A", 26, Theme.text, true);
        labels.addView(small, new LinearLayout.LayoutParams(dp(30), -2));
        labels.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(big, new LinearLayout.LayoutParams(dp(30), -2));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.topMargin = dp(18);
        sheet.addView(labels, ll);

        final SeekBar sb = new SeekBar(this);
        sb.setMax(6);
        sb.setProgress(Integer.parseInt(Store.get("font_i", "2")));
        GradientDrawable track = new GradientDrawable();
        track.setColor(Theme.text3);
        track.setSize(dp(10), dp(3));
        track.setCornerRadius(dp(2));
        sb.setProgressDrawable(track);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(0xFFFFFFFF);
        thumb.setSize(dp(34), dp(34));
        sb.setThumb(thumb);
        GradientDrawable tick = new GradientDrawable();
        tick.setColor(Theme.text3);
        tick.setSize(dp(2), dp(14));
        sb.setTickMark(tick);
        sb.setSplitTrack(false);
        sb.setPadding(dp(16), 0, dp(16), 0);
        sheet.addView(sb, new LinearLayout.LayoutParams(-1, dp(46)));
        col.addView(sheet, new LinearLayout.LayoutParams(-1, -2));

        final Runnable apply = new Runnable() {
            public void run() {
                boolean sys = Store.bool("font_sys", false);
                Store.applyTheme();
                float f = Theme.font;
                bubble.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18 * f);
                p1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18 * f);
                p2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18 * f);
                int i = sb.getProgress();
                mid.setText(sys ? T.s("s_follow_sys") : (i == 2 ? T.s("s_default") : (i < 2 ? T.s("s_small") : T.s("s_large"))));
                sb.setEnabled(!sys);
                sb.setAlpha(sys ? 0.4f : 1f);
                Store.version++;
            }
        };
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { Store.putBool("font_sys", on); apply.run(); }
        });
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) { Store.put("font_i", String.valueOf(p)); apply.run(); }
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        apply.run();
    }
}

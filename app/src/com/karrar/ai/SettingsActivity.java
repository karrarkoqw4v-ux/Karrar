package com.karrar.ai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private LinearLayout page;
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
        Theme.styleWindow(this);
        build();
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg);
        root.setFitsSystemWindows(true);
        root.setLayoutDirection(Theme.rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), 0, dp(6), 0);
        top.addView(Ui.iconBtn(this, Theme.rtl ? IconView.CHEV_RIGHT : IconView.CHEV_LEFT, 46, 26, Theme.text, new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        }));
        TextView t = Ui.label(this, T.s("settings"), 18, Theme.text, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
        tl.setMarginStart(dp(4));
        top.addView(t, tl);
        col.addView(top, new LinearLayout.LayoutParams(-1, dp(56)));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(4), dp(16), dp(28));
        sv.addView(page, new FrameLayout.LayoutParams(-1, -2));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        fill();
        setContentView(root);
    }

    private void rebuild() { Store.version++; recreate(); }

    private LinearLayout section(String title) {
        TextView h = Ui.label(this, title, 13.5f, Theme.accent, true);
        h.setPadding(dp(6), dp(20), dp(6), dp(8));
        page.addView(h, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.rect(this, Theme.surface, 18));
        page.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private void divider(LinearLayout card) {
        View d = new View(this);
        d.setBackgroundColor(Theme.border);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.setMarginStart(dp(54));
        card.addView(d, lp);
    }

    /** Row with icon, title, optional value/subtitle. */
    private TextView row(LinearLayout card, int icon, String title, String sub, final Runnable click, boolean first) {
        if (!first) divider(card);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(12), dp(14), dp(12));
        r.setMinimumHeight(dp(56));
        r.setBackground(Theme.rippleRect(this, 0x00000000, 18));
        r.addView(new IconView(this, icon, Theme.text2), new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(0, -2, 1);
        xl.setMarginStart(dp(16));
        r.addView(txt, xl);
        TextView tt = Ui.label(this, title, 15.5f, Theme.text, false);
        tt.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        txt.addView(tt);
        TextView st = Ui.label(this, sub == null ? "" : sub, 13, Theme.text3, false);
        st.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        st.setSingleLine(true);
        st.setEllipsize(android.text.TextUtils.TruncateAt.END);
        st.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
        txt.addView(st);
        if (click != null) {
            r.addView(new IconView(this, Theme.rtl ? IconView.CHEV_LEFT : IconView.CHEV_RIGHT, Theme.text3),
                    new LinearLayout.LayoutParams(dp(18), dp(18)));
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { click.run(); }
            });
        }
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return st;
    }

    private void toggle(LinearLayout card, int icon, String title, String sub, final String key, boolean def, boolean first, final boolean uiChange) {
        if (!first) divider(card);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(10), dp(14), dp(10));
        r.setMinimumHeight(dp(56));
        r.addView(new IconView(this, icon, Theme.text2), new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(0, -2, 1);
        xl.setMarginStart(dp(16));
        r.addView(txt, xl);
        TextView tt = Ui.label(this, title, 15.5f, Theme.text, false);
        tt.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        txt.addView(tt);
        if (sub != null) {
            TextView st = Ui.label(this, sub, 13, Theme.text3, false);
            st.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            txt.addView(st);
        }
        final Switch sw = new Switch(this);
        sw.setChecked(Store.bool(key, def));
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

    private void fill() {
        // ---------------- account
        LinearLayout c = section(T.s("s_account"));
        final TextView nameSub = row(c, IconView.USER, T.s("s_name"), Store.get("name", "").isEmpty() ? T.s("guest") : Store.get("name", ""), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_name"), Store.get("name", ""), "", false, false, new Ui.Str() {
                    public void on(String s) { Store.put("name", s); rebuild(); }
                });
            }
        }, true);

        row(c, IconView.CHAT, T.s("s_email"), Store.get("user_email", "").isEmpty() ? T.s("guest") : Store.get("user_email", ""), null, false);
        row(c, IconView.CLOSE, T.s("s_logout"), null, new Runnable() {
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
        }, false);

        c = section(T.s("s_google"));
        row(c, IconView.KEY, "Client ID", GoogleAuth.CLIENT_ID.substring(0, 12) + "…", null, true);
        row(c, IconView.LINK, T.s("s_gsecret"), Store.get("google_secret", "").isEmpty() ? T.s("s_gsecret_sub") : mask(Store.get("google_secret", "")), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_gsecret"), Store.get("google_secret", ""), "GOCSPX-...", false, true, new Ui.Str() {
                    public void on(String s) { Store.put("google_secret", s); rebuild(); }
                });
            }
        }, false);

        // ---------------- appearance
        c = section(T.s("s_appearance"));
        row(c, IconView.PALETTE, T.s("s_theme"), Theme.dark ? T.s("s_dark") : T.s("s_light"), new Runnable() {
            public void run() {
                Ui.choose(SettingsActivity.this, T.s("s_theme"), new String[]{T.s("s_dark"), T.s("s_light")}, Theme.dark ? 0 : 1, new Ui.Idx() {
                    public void on(int i) { Store.put("theme", i == 0 ? "dark" : "light"); rebuild(); }
                });
            }
        }, true);
        final String[] fk = {"s", "m", "l", "xl"};
        final String[] fn = {T.s("s_small"), T.s("s_medium"), T.s("s_large"), T.s("s_xl")};
        int fi = 1;
        for (int i = 0; i < fk.length; i++) if (fk[i].equals(Store.get("font", "m"))) fi = i;
        final int fiF = fi;
        row(c, IconView.EDIT, T.s("s_font"), fn[fi], new Runnable() {
            public void run() {
                Ui.choose(SettingsActivity.this, T.s("s_font"), fn, fiF, new Ui.Idx() {
                    public void on(int i) { Store.put("font", fk[i]); rebuild(); }
                });
            }
        }, false);
        row(c, IconView.LANG, T.s("s_lang"), Theme.rtl ? "العربية" : "English", new Runnable() {
            public void run() {
                Ui.choose(SettingsActivity.this, T.s("s_lang"), new String[]{"العربية", "English"}, Theme.rtl ? 0 : 1, new Ui.Idx() {
                    public void on(int i) { Store.put("lang", i == 0 ? "ar" : "en"); rebuild(); }
                });
            }
        }, false);

        // ---------------- connected apps
        c = section(T.s("s_connect"));
        String cur = T.s("s_custom");
        for (String[] p : PRESETS) if (Store.baseUrl().equals(p[1])) cur = p[0];
        final TextView provSub = row(c, IconView.LINK, T.s("s_provider"), cur + "  •  " + Store.baseUrl(), new Runnable() {
            public void run() { pickPreset(); }
        }, true);
        row(c, IconView.KEY, T.s("s_key"), mask(Store.get("api_key", "")), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_key"), Store.get("api_key", ""), "sk-...", false, true, new Ui.Str() {
                    public void on(String s) { Store.put("api_key", s); rebuild(); }
                });
            }
        }, false);
        row(c, IconView.LINK, T.s("s_url"), Store.baseUrl(), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_url"), Store.baseUrl(), "https://api.deepseek.com", false, false, new Ui.Str() {
                    public void on(String s) { if (!s.isEmpty()) { Store.put("base_url", s); rebuild(); } }
                });
            }
        }, false);
        final TextView testSub = row(c, IconView.CHECK, T.s("s_test"), "", new Runnable() { public void run() { } }, false);
        // attach real handler to the test row (last child)
        View testRow = c.getChildAt(c.getChildCount() - 1);
        testRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                testSub.setVisibility(View.VISIBLE);
                testSub.setTextColor(Theme.text3);
                testSub.setText(T.s("s_testing"));
                new Thread(new Runnable() {
                    public void run() {
                        final String r = Api.test();
                        ui.post(new Runnable() {
                            public void run() {
                                testSub.setTextColor(r == null ? 0xFF4CAF50 : Theme.danger);
                                testSub.setText(r == null ? T.s("s_ok") : r);
                                testSub.setSingleLine(false);
                            }
                        });
                    }
                }).start();
            }
        });

        // ---------------- model
        c = section(T.s("s_ai"));
        row(c, IconView.CHAT, T.s("s_model"), Store.get("model", "deepseek-chat"), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_model"), Store.get("model", "deepseek-chat"), "deepseek-chat", false, false, new Ui.Str() {
                    public void on(String s) { if (!s.isEmpty()) { Store.put("model", s); rebuild(); } }
                });
            }
        }, true);
        row(c, IconView.ATOM, T.s("s_think_model"), Store.get("think_model", "deepseek-reasoner"), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_think_model"), Store.get("think_model", "deepseek-reasoner"), "deepseek-reasoner", false, false, new Ui.Str() {
                    public void on(String s) { if (!s.isEmpty()) { Store.put("think_model", s); rebuild(); } }
                });
            }
        }, false);
        String sysv = Store.get("system", "");
        row(c, IconView.SPARK, T.s("s_system"), sysv.isEmpty() ? T.s("s_none") : sysv.replace('\n', ' '), new Runnable() {
            public void run() {
                Ui.edit(SettingsActivity.this, T.s("s_system"), Store.get("system", ""), "", true, false, new Ui.Str() {
                    public void on(String s) { Store.put("system", s); rebuild(); }
                });
            }
        }, false);
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
        }, false);
        toggle(c, IconView.ATOM, T.s("s_def_think"), null, "def_think", false, false, false);
        toggle(c, IconView.GLOBE, T.s("s_def_search"), T.s("s_search_src_sub"), "def_search", false, false, false);

        // ---------------- app
        c = section(T.s("s_app"));
        toggle(c, IconView.SPARK, T.s("s_stream"), null, "stream", true, true, false);
        toggle(c, IconView.SETTINGS, T.s("s_haptic"), null, "haptic", true, false, false);
        toggle(c, IconView.DATABASE, T.s("s_save_hist"), null, "save_hist", true, false, false);

        // ---------------- data
        c = section(T.s("s_data"));
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
        }, true);
        TextView delSub = row(c, IconView.TRASH, T.s("s_clear"), null, new Runnable() {
            public void run() {
                Ui.confirm(SettingsActivity.this, T.s("s_clear_q"), new Runnable() {
                    public void run() {
                        Store.convs.clear();
                        Store.put("cur", "");
                        Store.save();
                        Toast.makeText(SettingsActivity.this, "✓", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, false);

        // ---------------- about
        c = section(T.s("s_about"));
        row(c, IconView.INFO, T.s("s_version"), "1.0", null, true);
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
}

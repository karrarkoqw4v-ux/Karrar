package com.karrar.ai;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private LinearLayout drawer, list, emptyBox, histBox;
    private View scrim;
    private ScrollView scroll;
    private EditText input;
    private FrameLayout sendBtn;
    private IconView sendIcon;
    private TextView title, profileName;
    private Pill pillThink, pillSearch;

    private Store.Conv cur;
    private boolean think, search, streaming, drawerOpen;
    private Api.Handle handle;
    private AiRow liveRow;
    private int seenVersion;
    private float downX, downY;
    private boolean trackSwipe;

    // ------------------------------------------------------------------ lifecycle
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Store.init(this);
        Store.applyTheme();
        if (!Store.bool("logged", false)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        seenVersion = Store.version;
        Theme.styleWindow(this);
        think = Store.bool("def_think", false);
        search = Store.bool("def_search", false);
        build();
        cur = Store.find(Store.get("cur", ""));
        if (cur == null) cur = new Store.Conv();
        showConv();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Store.version != seenVersion) { recreate(); return; }
        if (profileName != null) profileName.setText(userName());
    }

    @Override
    protected void onPause() {
        super.onPause();
        Store.save();
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) closeDrawer(); else super.onBackPressed();
    }

    private int dp(float v) { return Theme.dp(this, v); }

    private String userName() {
        String n = Store.get("name", "").trim();
        return n.isEmpty() ? T.s("guest") : n;
    }

    // ------------------------------------------------------------------ UI construction
    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg);
        root.setFitsSystemWindows(true);
        root.setLayoutDirection(Theme.rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), 0, dp(6), 0);
        top.addView(Ui.iconBtn(this, IconView.MENU, 46, 26, Theme.text, new View.OnClickListener() {
            public void onClick(View v) { hideKeyboard(); openDrawer(); }
        }));
        title = Ui.label(this, T.s("new_chat"), 17, Theme.text, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(Ui.iconBtn(this, IconView.NEWCHAT, 46, 24, Theme.text, new View.OnClickListener() {
            public void onClick(View v) { newChat(); }
        }));
        main.addView(top, new LinearLayout.LayoutParams(-1, dp(56)));

        // messages + empty state
        FrameLayout mid = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(16), dp(12));
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        mid.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        IconView logo = new IconView(this, IconView.LOGO, Theme.accent);
        emptyBox.addView(logo, new LinearLayout.LayoutParams(dp(68), dp(68)));
        TextView hi = Ui.label(this, T.s("hi"), 26, Theme.text, true);
        hi.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = dp(18);
        emptyBox.addView(hi, hl);
        TextView help = Ui.label(this, T.s("help"), 15, Theme.text2, false);
        help.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl2 = new LinearLayout.LayoutParams(-2, -2);
        hl2.topMargin = dp(6);
        emptyBox.addView(help, hl2);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        ep.bottomMargin = dp(40);
        mid.addView(emptyBox, ep);
        main.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        // input area
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(12), dp(4), dp(12), dp(6));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.rect(this, Theme.surface, 26));
        card.setPadding(dp(16), dp(8), dp(10), dp(10));

        input = new EditText(this);
        input.setHint(T.s("hint"));
        input.setHintTextColor(Theme.text3);
        input.setTextColor(Theme.text);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f * Theme.font);
        input.setBackground(null);
        input.setMinHeight(dp(44));
        input.setMaxLines(6);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setPadding(0, dp(6), 0, dp(6));
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { updateSend(); }
        });
        card.addView(input, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout plus = new FrameLayout(this);
        plus.setBackground(Theme.ripple(Theme.circle(Theme.surface2)));
        IconView pi = new IconView(this, IconView.PLUS, Theme.text2);
        plus.addView(pi, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        plus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { attachMenu(); }
        });
        row.addView(plus, new LinearLayout.LayoutParams(dp(36), dp(36)));

        pillThink = new Pill(IconView.ATOM, T.s("deepthink"));
        pillThink.set(think);
        pillThink.box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { think = !think; pillThink.set(think); haptic(v); }
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, dp(36));
        pl.setMarginStart(dp(8));
        row.addView(pillThink.box, pl);

        pillSearch = new Pill(IconView.GLOBE, T.s("search"));
        pillSearch.set(search);
        pillSearch.box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { search = !search; pillSearch.set(search); haptic(v); }
        });
        LinearLayout.LayoutParams pl2 = new LinearLayout.LayoutParams(-2, dp(36));
        pl2.setMarginStart(dp(8));
        row.addView(pillSearch.box, pl2);

        View sp = new View(this);
        row.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));

        sendBtn = new FrameLayout(this);
        sendIcon = new IconView(this, IconView.SEND, Theme.text3);
        sendBtn.addView(sendIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        sendBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { send(); }
        });
        row.addView(sendBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        area.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView disc = Ui.label(this, T.s("disclaimer"), 11, Theme.text3, false);
        disc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = dp(6);
        area.addView(disc, dl);
        main.addView(area, new LinearLayout.LayoutParams(-1, -2));
        updateSend();

        buildDrawer();
        setContentView(root);
    }

    private class Pill {
        LinearLayout box;
        IconView ic;
        TextView tx;
        boolean on;

        Pill(int icon, String label) {
            box = new LinearLayout(MainActivity.this);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(12), 0, dp(14), 0);
            ic = new IconView(MainActivity.this, icon, Theme.text2);
            box.addView(ic, new LinearLayout.LayoutParams(dp(18), dp(18)));
            tx = Ui.label(MainActivity.this, label, 13.5f, Theme.text2, false);
            tx.setSingleLine(true);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            l.setMarginStart(dp(6));
            box.addView(tx, l);
        }

        void set(boolean v) {
            on = v;
            int c = v ? Theme.accent : Theme.text2;
            box.setBackground(Theme.ripple(Theme.rect(MainActivity.this, v ? Theme.accentSoft : 0x00000000, 18,
                    v ? Theme.accent : Theme.border, 1)));
            ic.setColor(c);
            tx.setTextColor(c);
        }
    }

    private void buildDrawer() {
        int w = Math.min(dp(320), (int) (getResources().getDisplayMetrics().widthPixels * 0.84f));
        scrim = new View(this);
        scrim.setBackgroundColor(Theme.scrim);
        scrim.setAlpha(0);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeDrawer(); }
        });
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(Theme.bg);
        drawer.setClickable(true);
        drawer.setElevation(dp(16));
        drawer.setVisibility(View.GONE);
        drawer.setPadding(dp(16), dp(14), dp(16), dp(10));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(w, -1, Gravity.START);
        root.addView(drawer, dlp);
        drawer.setTranslationX(closedX(w));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new IconView(this, IconView.LOGO, Theme.accent), new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView nm = Ui.label(this, T.s("app"), 21, Theme.text, true);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
        nl.setMarginStart(dp(10));
        head.addView(nm, nl);
        head.addView(Ui.iconBtn(this, IconView.CLOSE, 40, 22, Theme.text2, new View.OnClickListener() {
            public void onClick(View v) { closeDrawer(); }
        }));
        drawer.addView(head, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout nb = new LinearLayout(this);
        nb.setGravity(Gravity.CENTER);
        nb.setBackground(Theme.ripple(Theme.rect(this, Theme.accent, 16)));
        nb.addView(new IconView(this, IconView.NEWCHAT, 0xFFFFFFFF), new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView nt = Ui.label(this, T.s("new_chat"), 15.5f, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams ntl = new LinearLayout.LayoutParams(-2, -2);
        ntl.setMarginStart(dp(8));
        nb.addView(nt, ntl);
        nb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { newChat(); }
        });
        LinearLayout.LayoutParams nbl = new LinearLayout.LayoutParams(-1, dp(48));
        nbl.topMargin = dp(14);
        nbl.bottomMargin = dp(8);
        drawer.addView(nb, nbl);

        ScrollView hs = new ScrollView(this);
        hs.setVerticalScrollBarEnabled(false);
        histBox = new LinearLayout(this);
        histBox.setOrientation(LinearLayout.VERTICAL);
        hs.addView(histBox, new FrameLayout.LayoutParams(-1, -2));
        drawer.addView(hs, new LinearLayout.LayoutParams(-1, 0, 1));

        View div = new View(this);
        div.setBackgroundColor(Theme.border);
        LinearLayout.LayoutParams dv = new LinearLayout.LayoutParams(-1, 1);
        dv.topMargin = dp(6);
        drawer.addView(div, dv);

        LinearLayout prof = new LinearLayout(this);
        prof.setGravity(Gravity.CENTER_VERTICAL);
        prof.setPadding(dp(4), dp(8), dp(4), dp(8));
        prof.setBackground(Theme.rippleRect(this, 0x00000000, 14));
        TextView av = Ui.label(this, "", 16, 0xFFFFFFFF, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Theme.circle(Theme.accent));
        String n = userName();
        av.setText(n.substring(0, 1).toUpperCase());
        prof.addView(av, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout pcol = new LinearLayout(this);
        pcol.setOrientation(LinearLayout.VERTICAL);
        profileName = Ui.label(this, n, 16, Theme.text, true);
        profileName.setSingleLine(true);
        pcol.addView(profileName);
        String em = Store.get("user_email", "");
        if (!em.isEmpty()) {
            TextView et = Ui.label(this, em, 12.5f, Theme.text3, false);
            et.setSingleLine(true);
            et.setEllipsize(android.text.TextUtils.TruncateAt.END);
            pcol.addView(et);
        }
        LinearLayout.LayoutParams pn = new LinearLayout.LayoutParams(0, -2, 1);
        pn.setMarginStart(dp(12));
        prof.addView(pcol, pn);
        prof.addView(new IconView(this, IconView.SETTINGS, Theme.text2), new LinearLayout.LayoutParams(dp(24), dp(24)));
        prof.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeDrawer();
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        drawer.addView(prof, new LinearLayout.LayoutParams(-1, -2));
    }

    private float closedX(int w) { return Theme.rtl ? w : -w; }

    private void openDrawer() {
        refreshHistory();
        drawerOpen = true;
        scrim.setVisibility(View.VISIBLE);
        drawer.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(220).start();
        drawer.animate().translationX(0).setDuration(240).setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        scrim.animate().alpha(0f).setDuration(200).start();
        drawer.animate().translationX(closedX(drawer.getWidth())).setDuration(210)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(new Runnable() {
                    public void run() {
                        if (!drawerOpen) { drawer.setVisibility(View.GONE); scrim.setVisibility(View.GONE); }
                    }
                }).start();
    }

    private void refreshHistory() {
        histBox.removeAllViews();
        List<Store.Conv> all = new ArrayList<Store.Conv>();
        for (Store.Conv c : Store.convs) if (!c.msgs.isEmpty()) all.add(c);
        Collections.sort(all, new Comparator<Store.Conv>() {
            public int compare(Store.Conv a, Store.Conv b) { return a.ts == b.ts ? 0 : (a.ts < b.ts ? 1 : -1); }
        });
        if (all.isEmpty()) {
            TextView e = Ui.label(this, T.s("no_history"), 14, Theme.text3, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(40), 0, 0);
            histBox.addView(e, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        long now = System.currentTimeMillis();
        String lastGroup = "";
        for (final Store.Conv c : all) {
            long age = now - c.ts;
            String g = age < 86400000L ? T.s("today") : age < 7 * 86400000L ? T.s("week") : T.s("earlier");
            if (!g.equals(lastGroup)) {
                lastGroup = g;
                TextView gh = Ui.label(this, g, 12.5f, Theme.text3, true);
                gh.setPadding(dp(8), dp(14), dp(8), dp(6));
                histBox.addView(gh, new LinearLayout.LayoutParams(-1, -2));
            }
            boolean sel = c == cur;
            TextView it = Ui.label(this, c.title.isEmpty() ? T.s("new_chat") : c.title, 15, Theme.text, false);
            it.setSingleLine(true);
            it.setEllipsize(android.text.TextUtils.TruncateAt.END);
            it.setGravity(Gravity.CENTER_VERTICAL);
            it.setPadding(dp(12), 0, dp(12), 0);
            it.setBackground(Theme.rippleRect(this, sel ? Theme.surface2 : 0x00000000, 12));
            it.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openConv(c); }
            });
            it.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { convMenu(c); return true; }
            });
            histBox.addView(it, new LinearLayout.LayoutParams(-1, dp(44)));
        }
    }

    private void convMenu(final Store.Conv c) {
        Ui.choose(this, c.title, new String[]{T.s("rename"), T.s("share"), T.s("delete")}, -1, new Ui.Idx() {
            public void on(int i) {
                if (i == 0) {
                    Ui.edit(MainActivity.this, T.s("rename"), c.title, "", false, false, new Ui.Str() {
                        public void on(String s) { if (!s.isEmpty()) { c.title = s; Store.save(); refreshHistory(); if (c == cur) title.setText(s); } }
                    });
                } else if (i == 1) {
                    shareText(transcript(c));
                } else {
                    Store.convs.remove(c);
                    Store.save();
                    if (c == cur) newChat(); else refreshHistory();
                }
            }
        });
    }

    // ------------------------------------------------------------------ conversation handling
    private void openConv(Store.Conv c) {
        stopQuiet();
        cur = c;
        Store.put("cur", c.id);
        showConv();
        closeDrawer();
    }

    private void newChat() {
        stopQuiet();
        cur = new Store.Conv();
        Store.put("cur", cur.id);
        showConv();
        closeDrawer();
        input.requestFocus();
    }

    private void showConv() {
        list.removeAllViews();
        title.setText(cur.title.isEmpty() ? T.s("new_chat") : cur.title);
        int lastAi = -1;
        for (int i = 0; i < cur.msgs.size(); i++) if (cur.msgs.get(i).role.equals("assistant")) lastAi = i;
        for (int i = 0; i < cur.msgs.size(); i++) {
            Store.Msg m = cur.msgs.get(i);
            if (m.role.equals("user")) addUserRow(m);
            else { AiRow r = new AiRow(m, true, i == lastAi); list.addView(r.root); }
        }
        emptyBox.setVisibility(cur.msgs.isEmpty() ? View.VISIBLE : View.GONE);
        scrollToEnd(false);
    }

    private void addUserRow(final Store.Msg m) {
        emptyBox.setVisibility(View.GONE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.END);
        TextView t = new TextView(this);
        t.setText(m.content);
        t.setTextColor(Theme.text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f * Theme.font);
        t.setLineSpacing(0, 1.2f);
        t.setTextIsSelectable(true);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        t.setPadding(dp(16), dp(11), dp(16), dp(11));
        t.setBackground(Theme.rect(this, Theme.userBubble, 20));
        t.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
        wrap.addView(t, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(4);
        wrap.setAlpha(0f);
        wrap.setTranslationY(dp(10));
        list.addView(wrap, lp);
        wrap.animate().alpha(1f).translationY(0).setDuration(220).start();
    }

    // ------------------------------------------------------------------ sending
    private void updateSend() {
        boolean has = input != null && input.getText().toString().trim().length() > 0;
        if (streaming) {
            sendBtn.setBackground(Theme.ripple(Theme.circle(Theme.accent)));
            sendIcon.setType(IconView.STOP);
            sendIcon.setColor(0xFFFFFFFF);
        } else {
            sendBtn.setBackground(Theme.ripple(Theme.circle(has ? Theme.accent : Theme.surface2)));
            sendIcon.setType(IconView.SEND);
            sendIcon.setColor(has ? 0xFFFFFFFF : Theme.text3);
        }
    }

    private void haptic(View v) {
        if (Store.bool("haptic", true)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void send() {
        if (streaming) { stopGen(); return; }
        String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        haptic(sendBtn);
        input.setText("");
        Store.Msg u = new Store.Msg();
        u.role = "user";
        u.content = q;
        cur.msgs.add(u);
        if (cur.title.isEmpty()) {
            String t = q.replace('\n', ' ');
            cur.title = t.length() > 40 ? t.substring(0, 40) : t;
            title.setText(cur.title);
        }
        cur.ts = System.currentTimeMillis();
        if (!Store.convs.contains(cur)) Store.convs.add(0, cur);
        Store.put("cur", cur.id);
        addUserRow(u);
        startAnswer();
    }

    private void startAnswer() {
        final Store.Msg ai = new Store.Msg();
        ai.role = "assistant";
        ai.demo = !Api.hasKey();
        cur.msgs.add(ai);
        final AiRow row = new AiRow(ai, false, true);
        row.hideRegenOthers();
        list.addView(row.root);
        liveRow = row;
        streaming = true;
        updateSend();
        scrollToEnd(true);

        final List<Store.Msg> hist = new ArrayList<Store.Msg>(cur.msgs);
        hist.remove(hist.size() - 1);
        String lastUser = "";
        for (int i = hist.size() - 1; i >= 0; i--) if (hist.get(i).role.equals("user")) { lastUser = hist.get(i).content; break; }
        final boolean useThink = think, useSearch = search;
        final String query = lastUser;

        final Api.Cb cb = new Api.Cb() {
            long thinkStart = 0;
            public void onThink(String d) {
                if (row.finished) return;
                if (thinkStart == 0) thinkStart = System.currentTimeMillis();
                ai.reasoning += d;
                row.thinkOpen = true;
                row.thinkingNow = true;
                row.dirty();
            }
            public void onText(String d) {
                if (row.finished) return;
                if (ai.content.isEmpty() && thinkStart > 0 && ai.thinkSecs == 0) {
                    ai.thinkSecs = Math.max(1, (int) ((System.currentTimeMillis() - thinkStart) / 1000));
                    row.thinkOpen = false;
                    row.thinkingNow = false;
                }
                ai.content += d;
                row.dirty();
            }
            public void onDone() {
                if (thinkStart > 0 && ai.thinkSecs == 0) ai.thinkSecs = Math.max(1, (int) ((System.currentTimeMillis() - thinkStart) / 1000));
                finish(row, ai);
            }
            public void onError(String e) {
                ai.content += (ai.content.isEmpty() ? "" : "\n\n") + "⚠ " + e;
                if (thinkStart > 0 && ai.thinkSecs == 0) ai.thinkSecs = 1;
                finish(row, ai);
            }
        };

        if (useSearch) {
            row.statusText(T.s("searching"));
            new Thread(new Runnable() {
                public void run() {
                    final JSONArray res = Search.run(query);
                    ui.post(new Runnable() {
                        public void run() {
                            if (row.finished) return;
                            ai.sources = res.toString();
                            row.statusText(null);
                            row.buildSources();
                            String ctx = res.length() > 0 ? Search.asContext(res) : "";
                            handle = Api.chat(hist, useThink, ctx, cb, ui);
                        }
                    });
                }
            }).start();
            // allow cancelling during the search phase
            handle = new Api.Handle();
        } else {
            handle = Api.chat(hist, useThink, "", cb, ui);
        }
    }

    private void finish(AiRow row, Store.Msg ai) {
        if (row.finished) return;
        row.finished = true;
        row.thinkingNow = false;
        row.done = true;
        streaming = false;
        liveRow = null;
        handle = null;
        if (ai.content.isEmpty() && ai.reasoning.isEmpty()) ai.content = "—";
        Store.save();
        row.statusText(null);
        row.refresh();
        updateSend();
        scrollToEnd(true);
    }

    private void stopGen() {
        if (handle != null) handle.cancel();
        if (liveRow != null) {
            // finish immediately; late callbacks are ignored through row.finished
            for (int i = cur.msgs.size() - 1; i >= 0; i--) {
                Store.Msg m = cur.msgs.get(i);
                if (m.role.equals("assistant")) {
                    if (m.thinkSecs == 0 && !m.reasoning.isEmpty()) m.thinkSecs = 1;
                    finish(liveRow, m);
                    break;
                }
            }
        }
    }

    private void stopQuiet() {
        if (streaming) stopGen();
    }

    private void regenerate() {
        if (streaming || cur.msgs.isEmpty()) return;
        int last = cur.msgs.size() - 1;
        if (!cur.msgs.get(last).role.equals("assistant")) return;
        cur.msgs.remove(last);
        list.removeViewAt(list.getChildCount() - 1);
        startAnswer();
    }

    private void scrollToEnd(final boolean smooth) {
        scroll.post(new Runnable() {
            public void run() {
                View c = scroll.getChildAt(0);
                if (c == null) return;
                int y = c.getHeight() - scroll.getHeight();
                if (y < 0) y = 0;
                if (smooth) scroll.smoothScrollTo(0, y); else scroll.scrollTo(0, y);
            }
        });
    }

    private boolean nearBottom() {
        View c = scroll.getChildAt(0);
        if (c == null) return true;
        return scroll.getScrollY() + scroll.getHeight() >= c.getHeight() - dp(140);
    }

    // ------------------------------------------------------------------ attach / share
    private void attachMenu() {
        Ui.choose(this, T.s("app"), new String[]{T.s("attach_file"), T.s("paste")}, -1, new Ui.Idx() {
            public void on(int i) {
                if (i == 0) {
                    Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                    it.setType("*/*");
                    it.addCategory(Intent.CATEGORY_OPENABLE);
                    try { startActivityForResult(it, 77); } catch (Exception e) { Toast.makeText(MainActivity.this, "—", Toast.LENGTH_SHORT).show(); }
                } else {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                        CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                        if (t != null) input.append(t);
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 77 || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            InputStream in = getContentResolver().openInputStream(data.getData());
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 40000) { bo.write(buf, 0, n); total += n; }
            in.close();
            String txt = new String(bo.toByteArray(), "UTF-8");
            input.append("\n```\n" + txt + "\n```\n");
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareText(String s) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, s);
        startActivity(Intent.createChooser(i, T.s("share")));
    }

    private String transcript(Store.Conv c) {
        StringBuilder sb = new StringBuilder();
        for (Store.Msg m : c.msgs) sb.append(m.role.equals("user") ? "▌" : "◆").append(' ').append(m.content).append("\n\n");
        return sb.toString();
    }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------ swipe to open drawer
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                trackSwipe = true;
                break;
            case MotionEvent.ACTION_UP:
                if (trackSwipe) {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (Math.abs(dx) > dp(70) && Math.abs(dx) > Math.abs(dy) * 2) {
                        boolean opening = Theme.rtl ? dx < 0 : dx > 0;
                        int w = getResources().getDisplayMetrics().widthPixels;
                        boolean nearEdge = Theme.rtl ? downX > w - dp(48) : downX < dp(48);
                        if (!drawerOpen && opening && nearEdge) { hideKeyboard(); openDrawer(); }
                        else if (drawerOpen && !opening) closeDrawer();
                    }
                }
                trackSwipe = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                trackSwipe = false;
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    // ------------------------------------------------------------------ assistant message row
    private class AiRow {
        final Store.Msg m;
        LinearLayout root, thinkBox, thinkBodyWrap, content, actions, srcBox, srcList;
        TextView thinkTitle, thinkBody, status, demoNote;
        IconView thinkIcon, thinkChev, like, dislike;
        FrameLayout regenBtn;
        boolean thinkOpen, thinkingNow, done, finished, isLast, pending;
        android.animation.ObjectAnimator pulse;

        AiRow(final Store.Msg msg, boolean done, boolean isLast) {
            this.m = msg;
            this.done = done;
            this.finished = done;
            this.isLast = isLast;
            root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(0, dp(10), 0, dp(6));

            // avatar line
            LinearLayout who = new LinearLayout(MainActivity.this);
            who.setGravity(Gravity.CENTER_VERTICAL);
            who.addView(new IconView(MainActivity.this, IconView.LOGO, Theme.accent), new LinearLayout.LayoutParams(dp(24), dp(24)));
            root.addView(who, new LinearLayout.LayoutParams(-2, -2));

            // sources chip
            srcBox = new LinearLayout(MainActivity.this);
            srcBox.setOrientation(LinearLayout.VERTICAL);
            srcBox.setVisibility(View.GONE);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.topMargin = dp(8);
            root.addView(srcBox, sl);

            status = Ui.label(MainActivity.this, "", 14, Theme.text2, false);
            status.setVisibility(View.GONE);
            LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(-2, -2);
            stl.topMargin = dp(10);
            root.addView(status, stl);

            // thinking block
            thinkBox = new LinearLayout(MainActivity.this);
            thinkBox.setOrientation(LinearLayout.VERTICAL);
            LinearLayout hdr = new LinearLayout(MainActivity.this);
            hdr.setGravity(Gravity.CENTER_VERTICAL);
            hdr.setPadding(dp(12), dp(7), dp(12), dp(7));
            hdr.setBackground(Theme.ripple(Theme.rect(MainActivity.this, Theme.surface, 16)));
            thinkIcon = new IconView(MainActivity.this, IconView.ATOM, Theme.text2);
            hdr.addView(thinkIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
            thinkTitle = Ui.label(MainActivity.this, "", 13.5f, Theme.text2, false);
            LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(-2, -2);
            ttl.setMarginStart(dp(8));
            hdr.addView(thinkTitle, ttl);
            thinkChev = new IconView(MainActivity.this, IconView.CHEV_DOWN, Theme.text2);
            LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(dp(16), dp(16));
            chl.setMarginStart(dp(6));
            hdr.addView(thinkChev, chl);
            hdr.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { thinkOpen = !thinkOpen; refreshThink(); }
            });
            thinkBox.addView(hdr, new LinearLayout.LayoutParams(-2, -2));

            thinkBodyWrap = new LinearLayout(MainActivity.this);
            View bar = new View(MainActivity.this);
            bar.setBackgroundColor(Theme.border);
            thinkBodyWrap.addView(bar, new LinearLayout.LayoutParams(dp(2), -1));
            thinkBody = Ui.label(MainActivity.this, "", 14, Theme.text2, false);
            thinkBody.setLineSpacing(0, 1.25f);
            thinkBody.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            thinkBody.setTextIsSelectable(true);
            thinkBody.setPadding(dp(12), dp(2), 0, dp(2));
            thinkBodyWrap.addView(thinkBody, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams twl = new LinearLayout.LayoutParams(-1, -2);
            twl.topMargin = dp(8);
            twl.setMarginStart(dp(4));
            thinkBox.addView(thinkBodyWrap, twl);
            LinearLayout.LayoutParams tbl = new LinearLayout.LayoutParams(-1, -2);
            tbl.topMargin = dp(10);
            root.addView(thinkBox, tbl);

            content = new LinearLayout(MainActivity.this);
            content.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
            cl.topMargin = dp(8);
            root.addView(content, cl);

            demoNote = Ui.label(MainActivity.this, "ⓘ " + T.s("demo_badge"), 12, Theme.text3, false);
            demoNote.setVisibility(View.GONE);
            LinearLayout.LayoutParams dnl = new LinearLayout.LayoutParams(-2, -2);
            dnl.topMargin = dp(4);
            root.addView(demoNote, dnl);

            buildActions();
            thinkOpen = false;
            thinkingNow = false;
            buildSources();
            refresh();
            if (!done) {
                status.setVisibility(View.VISIBLE);
                status.setText("●");
                pulse = android.animation.ObjectAnimator.ofFloat(status, "alpha", 1f, 0.2f);
                pulse.setDuration(650);
                pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                pulse.start();
            }
        }

        void hideRegenOthers() {
            // when a new answer starts, no earlier row may keep a regenerate button
            for (int i = 0; i < list.getChildCount() - 1; i++) {
                View v = list.getChildAt(i);
                Object t = v.getTag();
                if (t instanceof AiRow && ((AiRow) t).regenBtn != null) ((AiRow) t).regenBtn.setVisibility(View.GONE);
            }
        }

        void buildActions() {
            root.setTag(null);
            actions = new LinearLayout(MainActivity.this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            int ic = Theme.text3;
            actions.addView(Ui.iconBtn(MainActivity.this, IconView.COPY, 38, 19, ic, new View.OnClickListener() {
                public void onClick(View v) { haptic(v); Md.copy(MainActivity.this, m.content); }
            }));
            regenBtn = (FrameLayout) Ui.iconBtn(MainActivity.this, IconView.REFRESH, 38, 19, ic, new View.OnClickListener() {
                public void onClick(View v) { haptic(v); regenerate(); }
            });
            regenBtn.setVisibility(isLast ? View.VISIBLE : View.GONE);
            actions.addView(regenBtn);
            final View lk = Ui.iconBtn(MainActivity.this, IconView.LIKE, 38, 19, m.vote == 1 ? Theme.accent : ic, null);
            final View dl = Ui.iconBtn(MainActivity.this, IconView.DISLIKE, 38, 19, m.vote == -1 ? Theme.accent : ic, null);
            like = Ui.iconOf(lk);
            dislike = Ui.iconOf(dl);
            lk.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { haptic(v); m.vote = m.vote == 1 ? 0 : 1; paintVote(); Store.save(); }
            });
            dl.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { haptic(v); m.vote = m.vote == -1 ? 0 : -1; paintVote(); Store.save(); }
            });
            actions.addView(lk);
            actions.addView(dl);
            actions.addView(Ui.iconBtn(MainActivity.this, IconView.SHARE, 38, 19, ic, new View.OnClickListener() {
                public void onClick(View v) { shareText(m.content); }
            }));
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
            al.topMargin = dp(2);
            root.addView(actions, al);
            actions.setVisibility(done ? View.VISIBLE : View.GONE);
            root.setTag(this);
        }

        void paintVote() {
            like.setColor(m.vote == 1 ? Theme.accent : Theme.text3);
            dislike.setColor(m.vote == -1 ? Theme.accent : Theme.text3);
        }

        void statusText(String s) {
            if (pulse != null) { pulse.cancel(); pulse = null; status.setAlpha(1f); }
            if (s == null) { status.setVisibility(View.GONE); return; }
            status.setVisibility(View.VISIBLE);
            status.setText(s);
            pulse = android.animation.ObjectAnimator.ofFloat(status, "alpha", 1f, 0.35f);
            pulse.setDuration(700);
            pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulse.start();
        }

        void buildSources() {
            srcBox.removeAllViews();
            JSONArray a;
            try { a = new JSONArray(m.sources); } catch (Exception e) { a = new JSONArray(); }
            if (a.length() == 0) { srcBox.setVisibility(View.GONE); return; }
            srcBox.setVisibility(View.VISIBLE);
            final LinearLayout listBox = new LinearLayout(MainActivity.this);
            listBox.setOrientation(LinearLayout.VERTICAL);
            listBox.setVisibility(View.GONE);
            LinearLayout chip = new LinearLayout(MainActivity.this);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(12), dp(7), dp(12), dp(7));
            chip.setBackground(Theme.ripple(Theme.rect(MainActivity.this, Theme.surface, 16)));
            chip.addView(new IconView(MainActivity.this, IconView.GLOBE, Theme.text2), new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView t = Ui.label(MainActivity.this, String.format(T.s("searched"), a.length()), 13.5f, Theme.text2, false);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.setMarginStart(dp(8));
            chip.addView(t, tl);
            final IconView chev = new IconView(MainActivity.this, IconView.CHEV_DOWN, Theme.text2);
            LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(dp(16), dp(16));
            chl.setMarginStart(dp(6));
            chip.addView(chev, chl);
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    boolean open = listBox.getVisibility() != View.VISIBLE;
                    listBox.setVisibility(open ? View.VISIBLE : View.GONE);
                    chev.setRotation(open ? 180 : 0);
                }
            });
            srcBox.addView(chip, new LinearLayout.LayoutParams(-2, -2));
            for (int i = 0; i < a.length(); i++) {
                final JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                LinearLayout it = new LinearLayout(MainActivity.this);
                it.setOrientation(LinearLayout.VERTICAL);
                it.setPadding(dp(12), dp(8), dp(12), dp(8));
                it.setBackground(Theme.rippleRect(MainActivity.this, 0x00000000, 10));
                TextView tt = Ui.label(MainActivity.this, (i + 1) + ". " + o.optString("title"), 14, Theme.accent, false);
                tt.setSingleLine(true);
                tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tt.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                it.addView(tt);
                String host = Uri.parse(o.optString("url")).getHost();
                TextView ut = Ui.label(MainActivity.this, host == null ? o.optString("url") : host, 12, Theme.text3, false);
                ut.setSingleLine(true);
                ut.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                it.addView(ut);
                it.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(o.optString("url")))); } catch (Exception ignored) { }
                    }
                });
                listBox.addView(it, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            ll.topMargin = dp(4);
            srcBox.addView(listBox, ll);
        }

        void refreshThink() {
            boolean has = !m.reasoning.isEmpty();
            thinkBox.setVisibility(has ? View.VISIBLE : View.GONE);
            if (!has) return;
            thinkBody.setText(m.reasoning);
            thinkBodyWrap.setVisibility(thinkOpen ? View.VISIBLE : View.GONE);
            thinkChev.setRotation(thinkOpen ? 180 : 0);
            thinkTitle.setText(thinkingNow ? T.s("thinking") : String.format(T.s("thought_for"), Math.max(1, m.thinkSecs)));
            thinkIcon.setColor(thinkingNow ? Theme.accent : Theme.text2);
        }

        private boolean queued;
        private long lastRender;

        void dirty() {
            if (queued) return;
            queued = true;
            long wait = Math.max(0, 60 - (System.currentTimeMillis() - lastRender));
            ui.postDelayed(new Runnable() {
                public void run() {
                    queued = false;
                    if (finished) return;
                    boolean follow = nearBottom();
                    refresh();
                    if (follow) scrollToEnd(false);
                }
            }, wait);
        }

        void refresh() {
            lastRender = System.currentTimeMillis();
            if (pulse != null && (!m.content.isEmpty() || !m.reasoning.isEmpty()) && status.getText().toString().equals("●")) {
                statusText(null);
            }
            refreshThink();
            String txt = m.content;
            if (!done && !txt.isEmpty()) txt += " ▍";
            Md.render(MainActivity.this, content, txt, done);
            actions.setVisibility(done ? View.VISIBLE : View.GONE);
            demoNote.setVisibility(done && m.demo ? View.VISIBLE : View.GONE);
            regenBtn.setVisibility(done && isLast ? View.VISIBLE : View.GONE);
            paintVote();
        }
    }
}

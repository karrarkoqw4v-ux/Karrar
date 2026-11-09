package com.karrar.ai;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 77, REQ_ALBUM = 78, REQ_CAMERA = 79, REQ_VOICE = 80, REQ_PERM = 81;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private LinearLayout drawer, list, emptyBox, histBox, main, attachPanel, galleryRow, previewRow, bottomBar;
    private View scrim;
    private ScrollView scroll;
    private HorizontalScrollView previewScroll;
    private EditText input, searchBox;
    private FrameLayout sendBtn, plusBtn;
    private IconView sendIcon, plusIcon, soundIcon;
    private TextView title, profileName, delCount;
    private Pill pillThink, pillSearch;
    private LinearLayout inputCard;
    private final List<View> emptyItems = new ArrayList<View>();
    private final List<Att> atts = new ArrayList<Att>();
    private final List<Store.Conv> selected = new ArrayList<Store.Conv>();
    private boolean selectMode = false, panelOpen = false;

    private Store.Conv cur;
    private boolean think, search, streaming, drawerOpen;
    private Api.Handle handle;
    private AiRow liveRow;
    private int seenVersion;
    private float downX, downY;
    private boolean trackSwipe;
    private TextToSpeech tts;
    private boolean ttsReady;

    private static class Att { Bitmap thumb; String b64; Uri uri; }

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
        if (soundIcon != null) soundIcon.setType(Store.bool("sound", false) ? IconView.SOUND_ON : IconView.SOUND_OFF);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Store.save();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) { togglePanel(false); return; }
        if (selectMode) { exitSelect(); return; }
        if (drawerOpen) closeDrawer(); else super.onBackPressed();
    }

    private int dp(float v) { return Theme.dp(this, v); }

    private String userName() {
        String n = Store.get("name", "").trim();
        return n.isEmpty() ? T.s("guest") : n;
    }

    /** Round outlined button (as in the reference input bar). */
    private FrameLayout ringBtn(int icon, int sizeDp, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x00000000);
        g.setStroke(dp(1.6f), Theme.text);
        f.setBackground(Theme.ripple(g));
        f.addView(new IconView(this, icon, Theme.text), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        f.setOnClickListener(l);
        Fx.press(f);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return f;
    }

    // ------------------------------------------------------------------ UI construction
    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg);
        root.setFitsSystemWindows(true);
        root.setLayoutDirection(Theme.rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        if (Store.bool("fx", false)) {
            root.addView(new Fx.Aurora(this, 0x334D6BFE, 0x288B5CF6, 0x00000000), new FrameLayout.LayoutParams(-1, -1));
            root.addView(new Fx.Rain(this), new FrameLayout.LayoutParams(-1, -1));
        }

        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(Theme.bg);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // top bar: menu (start) ... sound, new chat (end)
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), 0, dp(10), 0);
        View menu = Ui.iconBtn(this, IconView.MENU, 48, 28, Theme.text, new View.OnClickListener() {
            public void onClick(View v) { hideKeyboard(); openDrawer(); }
        });
        if (Theme.rtl) Ui.iconOf(menu).setScaleX(-1f);
        top.addView(menu);
        title = Ui.label(this, "", 15, Theme.text2, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        View snd = Ui.iconBtn(this, Store.bool("sound", false) ? IconView.SOUND_ON : IconView.SOUND_OFF, 48, 26, Theme.text, new View.OnClickListener() {
            public void onClick(View v) { toggleSound(); }
        });
        soundIcon = Ui.iconOf(snd);
        top.addView(snd);
        top.addView(Ui.iconBtn(this, IconView.NEWCHAT2, 48, 28, Theme.text, new View.OnClickListener() {
            public void onClick(View v) { newChat(); }
        }));
        main.addView(top, new LinearLayout.LayoutParams(-1, dp(58)));

        // messages + empty state
        FrameLayout mid = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(4), dp(18), dp(12));
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        mid.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.setPadding(dp(40), 0, dp(40), 0);
        IconView logo = new IconView(this, IconView.LOGO, Theme.accent);
        emptyBox.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));
        emptyItems.add(logo);
        TextView hi = Ui.label(this, T.s("hi"), 25, Theme.text, true);
        hi.setGravity(Gravity.CENTER);
        hi.setLineSpacing(0, 1.18f);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = dp(26);
        emptyBox.addView(hi, hl);
        emptyItems.add(hi);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        ep.bottomMargin = dp(10);
        mid.addView(emptyBox, ep);
        main.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        // input area
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(12), dp(4), dp(12), dp(10));

        inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.VERTICAL);
        inputCard.setBackground(Theme.rect(this, Theme.surface, 30, Theme.border, 1));
        inputCard.setPadding(dp(20), dp(8), dp(14), dp(14));

        previewScroll = new HorizontalScrollView(this);
        previewScroll.setHorizontalScrollBarEnabled(false);
        previewRow = new LinearLayout(this);
        previewScroll.addView(previewRow);
        previewScroll.setVisibility(View.GONE);
        LinearLayout.LayoutParams pvl = new LinearLayout.LayoutParams(-1, -2);
        pvl.topMargin = dp(6);
        inputCard.addView(previewScroll, pvl);

        input = new EditText(this);
        input.setHint(T.s("hint"));
        input.setHintTextColor(Theme.text3);
        input.setTextColor(Theme.text);
        input.setTypeface(Fonts.m);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f * Theme.font);
        input.setBackground(null);
        input.setMinHeight(dp(56));
        input.setMaxLines(6);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setPadding(0, dp(8), 0, dp(8));
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean f) { if (f && panelOpen) togglePanel(false); }
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { updateSend(); }
        });
        inputCard.addView(input, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        pillThink = new Pill(IconView.ATOM, T.s("deepthink"));
        pillThink.set(think);
        pillThink.box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { think = !think; pillThink.set(think); haptic(v); }
        });
        Fx.press(pillThink.box);
        row.addView(pillThink.box, new LinearLayout.LayoutParams(-2, dp(42)));
        pillSearch = new Pill(IconView.GLOBE, T.s("search"));
        pillSearch.set(search);
        pillSearch.box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { search = !search; pillSearch.set(search); haptic(v); }
        });
        Fx.press(pillSearch.box);
        LinearLayout.LayoutParams ps = new LinearLayout.LayoutParams(-2, dp(42));
        ps.setMarginStart(dp(10));
        row.addView(pillSearch.box, ps);
        row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));

        plusBtn = ringBtn(IconView.PLUS, 42, new View.OnClickListener() {
            public void onClick(View v) { togglePanel(!panelOpen); }
        });
        plusIcon = (IconView) plusBtn.getChildAt(0);
        row.addView(plusBtn);
        sendBtn = ringBtn(IconView.VOICE, 42, new View.OnClickListener() {
            public void onClick(View v) { onSendOrVoice(); }
        });
        sendIcon = (IconView) sendBtn.getChildAt(0);
        LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(dp(42), dp(42));
        sbl.setMarginStart(dp(10));
        row.addView(sendBtn, sbl);
        inputCard.addView(row, new LinearLayout.LayoutParams(-1, -2));
        area.addView(inputCard, new LinearLayout.LayoutParams(-1, -2));

        buildAttachPanel();
        area.addView(attachPanel, new LinearLayout.LayoutParams(-1, -2));
        bottomBar = area;
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
            box.setPadding(dp(16), 0, dp(18), 0);
            ic = new IconView(MainActivity.this, icon, Theme.text);
            box.addView(ic, new LinearLayout.LayoutParams(dp(21), dp(21)));
            tx = Ui.label(MainActivity.this, label, 17, Theme.text, true);
            tx.setSingleLine(true);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            l.setMarginStart(dp(8));
            box.addView(tx, l);
        }

        void set(boolean v) {
            on = v;
            int c = v ? 0xFF4D8DFF : Theme.text;
            box.setBackground(Theme.ripple(Theme.rect(MainActivity.this, v ? 0x2E4D6BFE : 0x00000000, 21,
                    v ? 0x664D6BFE : Theme.alpha(Theme.text, 130), 1.3f)));
            ic.setColor(c);
            tx.setTextColor(c);
        }
    }

    // ------------------------------------------------------------------ attach panel (gallery + camera/album/file)
    private void buildAttachPanel() {
        attachPanel = new LinearLayout(this);
        attachPanel.setOrientation(LinearLayout.VERTICAL);
        attachPanel.setVisibility(View.GONE);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        galleryRow = new LinearLayout(this);
        galleryRow.setPadding(0, dp(6), 0, dp(6));
        hs.addView(galleryRow);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, dp(138));
        gl.topMargin = dp(14);
        attachPanel.addView(hs, gl);

        LinearLayout btns = new LinearLayout(this);
        int[] icons = {IconView.CAMERA, IconView.IMAGE, IconView.CLIP};
        String[] labels = {T.s("camera"), T.s("album"), T.s("file")};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Theme.ripple(Theme.rect(this, Theme.surface2, 28)));
            b.addView(new IconView(this, icons[i], Theme.text), new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView t = Ui.label(this, labels[i], 22, Theme.text, true);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.topMargin = dp(10);
            b.addView(t, tl);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { attachAction(idx); }
            });
            Fx.press(b);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, dp(118), 1);
            bl.setMargins(dp(5), 0, dp(5), 0);
            btns.addView(b, bl);
        }
        LinearLayout.LayoutParams btl = new LinearLayout.LayoutParams(-1, -2);
        btl.topMargin = dp(8);
        attachPanel.addView(btns, btl);
    }

    private void togglePanel(boolean open) {
        panelOpen = open;
        plusIcon.setType(open ? IconView.CLOSE : IconView.PLUS);
        if (open) {
            hideKeyboard();
            input.clearFocus();
            attachPanel.setVisibility(View.VISIBLE);
            Fx.enter(attachPanel, 0);
            loadGallery();
        } else {
            attachPanel.setVisibility(View.GONE);
        }
    }

    private void attachAction(int idx) {
        if (idx == 0) {
            Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            try { startActivityForResult(it, REQ_CAMERA); } catch (Exception e) { Toast.makeText(this, "—", Toast.LENGTH_SHORT).show(); }
        } else if (idx == 1) {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("image/*");
            it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try { startActivityForResult(it, REQ_ALBUM); } catch (Exception e) { Toast.makeText(this, "—", Toast.LENGTH_SHORT).show(); }
        } else {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("*/*");
            it.addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(it, REQ_FILE); } catch (Exception e) { Toast.makeText(this, "—", Toast.LENGTH_SHORT).show(); }
        }
    }

    private boolean hasGalleryPerm() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadGallery() {
        galleryRow.removeAllViews();
        if (!hasGalleryPerm()) {
            String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
            requestPermissions(new String[]{p}, REQ_PERM);
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                final List<Object[]> items = new ArrayList<Object[]>();
                try {
                    Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Images.Media._ID}, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
                    if (c != null) {
                        while (c.moveToNext() && items.size() < 14) {
                            long id = c.getLong(0);
                            Uri u = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                            Bitmap bm = decode(u, 260);
                            if (bm != null) items.add(new Object[]{u, bm});
                        }
                        c.close();
                    }
                } catch (Exception ignored) { }
                ui.post(new Runnable() {
                    public void run() {
                        for (Object[] it : items) galleryRow.addView(thumb((Uri) it[0], (Bitmap) it[1]));
                    }
                });
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_PERM) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) loadGallery();
            else Toast.makeText(this, T.s("perm_needed"), Toast.LENGTH_SHORT).show();
        }
    }

    private View thumb(final Uri uri, final Bitmap bm) {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(bm);
        iv.setClipToOutline(true);
        iv.setBackground(Theme.rect(this, Theme.surface2, 22));
        iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
            public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(22)); }
        });
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        final View ring = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x33000000);
        g.setStroke(dp(2.2f), 0xFFFFFFFF);
        ring.setBackground(g);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.START);
        rl.setMargins(dp(10), dp(10), dp(10), dp(10));
        f.addView(ring, rl);
        f.setTag(Boolean.FALSE);
        f.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean on = !Boolean.TRUE.equals(v.getTag());
                v.setTag(on);
                GradientDrawable gg = new GradientDrawable();
                gg.setShape(GradientDrawable.OVAL);
                gg.setColor(on ? Theme.accent : 0x33000000);
                gg.setStroke(dp(2.2f), 0xFFFFFFFF);
                ring.setBackground(gg);
                Fx.pop(ring);
                if (on) addAttachment(uri, bm); else removeAttachment(uri);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(124), -1);
        lp.setMarginEnd(dp(10));
        f.setLayoutParams(lp);
        return f;
    }

    private Bitmap decode(Uri uri, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth / sample > max * 2 || o.outHeight / sample > max * 2) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private String toB64(Bitmap b) {
        int max = 1024;
        float k = Math.min(1f, max / (float) Math.max(b.getWidth(), b.getHeight()));
        Bitmap s = k < 1f ? Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true) : b;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        s.compress(Bitmap.CompressFormat.JPEG, 82, bo);
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    private void addAttachment(final Uri uri, final Bitmap thumbBm) {
        final Att a = new Att();
        a.uri = uri;
        a.thumb = thumbBm;
        atts.add(a);
        refreshPreview();
        new Thread(new Runnable() {
            public void run() {
                Bitmap full = uri != null ? decode(uri, 1024) : thumbBm;
                a.b64 = toB64(full == null ? thumbBm : full);
            }
        }).start();
    }

    private void removeAttachment(Uri uri) {
        for (int i = 0; i < atts.size(); i++) if (atts.get(i).uri != null && atts.get(i).uri.equals(uri)) { atts.remove(i); break; }
        refreshPreview();
    }

    private void refreshPreview() {
        previewRow.removeAllViews();
        previewScroll.setVisibility(atts.isEmpty() ? View.GONE : View.VISIBLE);
        for (final Att a : atts) {
            FrameLayout f = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(a.thumb);
            iv.setBackground(Theme.rect(this, Theme.surface2, 14));
            iv.setClipToOutline(true);
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14)); }
            });
            f.addView(iv, new FrameLayout.LayoutParams(dp(60), dp(60)));
            TextView x = Ui.label(this, "✕", 12, 0xFFFFFFFF, true);
            x.setGravity(Gravity.CENTER);
            x.setBackground(Theme.circle(0xCC000000));
            x.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { atts.remove(a); refreshPreview(); }
            });
            f.addView(x, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP | Gravity.END));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(8));
            previewRow.addView(f, lp);
        }
        updateSend();
    }

    // ------------------------------------------------------------------ drawer (search + history, as in the reference)
    private void buildDrawer() {
        int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
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
        drawer.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.addView(drawer, new FrameLayout.LayoutParams(w, -1, Gravity.START));
        drawer.setTranslationX(closedX(w));

        // search pill
        LinearLayout sb = new LinearLayout(this);
        sb.setGravity(Gravity.CENTER_VERTICAL);
        sb.setBackground(Theme.rect(this, Theme.field, 28));
        sb.setPadding(dp(18), 0, dp(18), 0);
        sb.addView(new IconView(this, IconView.SEARCH, Theme.text2), new LinearLayout.LayoutParams(dp(24), dp(24)));
        searchBox = new EditText(this);
        searchBox.setHint(T.s("search_chats"));
        searchBox.setHintTextColor(Theme.text2);
        searchBox.setTextColor(Theme.text);
        searchBox.setTypeface(Fonts.m);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f * Theme.font);
        searchBox.setBackground(null);
        searchBox.setSingleLine(true);
        searchBox.setPadding(dp(12), 0, dp(4), 0);
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { refreshHistory(); }
        });
        sb.addView(searchBox, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(-1, dp(56));
        sbl.topMargin = dp(4);
        drawer.addView(sb, sbl);

        ScrollView hs = new ScrollView(this);
        hs.setVerticalScrollBarEnabled(false);
        histBox = new LinearLayout(this);
        histBox.setOrientation(LinearLayout.VERTICAL);
        hs.addView(histBox, new FrameLayout.LayoutParams(-1, -2));
        drawer.addView(hs, new LinearLayout.LayoutParams(-1, 0, 1));

        // profile row (avatar, name, more)
        LinearLayout prof = new LinearLayout(this);
        prof.setGravity(Gravity.CENTER_VERTICAL);
        prof.setPadding(dp(4), dp(10), dp(4), dp(10));
        prof.addView(new IconView(this, IconView.AVATAR, 0xFFFFFFFF), new LinearLayout.LayoutParams(dp(46), dp(46)));
        profileName = Ui.label(this, userName(), 18, Theme.text, true);
        profileName.setSingleLine(true);
        LinearLayout.LayoutParams pn = new LinearLayout.LayoutParams(0, -2, 1);
        pn.setMarginStart(dp(14));
        prof.addView(profileName, pn);
        FrameLayout more = new FrameLayout(this);
        more.addView(new IconView(this, IconView.MORE, Theme.text2), new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        more.setBackground(Theme.ripple(Theme.circle(0x00000000)));
        prof.addView(more, new LinearLayout.LayoutParams(dp(46), dp(46)));
        prof.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeDrawer();
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        Fx.press(prof);
        delCount = Ui.label(this, "", 17, Theme.danger, true);
        delCount.setGravity(Gravity.CENTER);
        delCount.setVisibility(View.GONE);
        delCount.setBackground(Theme.rippleRect(this, Theme.field, 22));
        delCount.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { deleteSelected(); }
        });
        drawer.addView(prof, new LinearLayout.LayoutParams(-1, -2));
        drawer.addView(delCount, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    private float closedX(int w) { return Theme.rtl ? w : -w; }

    private void openDrawer() {
        refreshHistory();
        for (int i = 0; i < histBox.getChildCount() && i < 14; i++) Fx.enter(histBox.getChildAt(i), 40 + i * 28L);
        drawerOpen = true;
        scrim.setVisibility(View.VISIBLE);
        drawer.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(220).start();
        drawer.animate().translationX(0).setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        main.animate().translationX((Theme.rtl ? -1 : 1) * dp(26)).setDuration(260).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        if (selectMode) exitSelect();
        scrim.animate().alpha(0f).setDuration(200).start();
        main.animate().translationX(0).setDuration(220).start();
        drawer.animate().translationX(closedX(drawer.getWidth())).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(new Runnable() {
                    public void run() {
                        if (!drawerOpen) { drawer.setVisibility(View.GONE); scrim.setVisibility(View.GONE); }
                    }
                }).start();
    }

    private void refreshHistory() {
        histBox.removeAllViews();
        String q = searchBox == null ? "" : searchBox.getText().toString().trim().toLowerCase();
        List<Store.Conv> all = new ArrayList<Store.Conv>();
        for (Store.Conv c : Store.convs) {
            if (c.msgs.isEmpty()) continue;
            if (!q.isEmpty()) {
                boolean hit = c.title.toLowerCase().contains(q);
                for (int i = 0; i < c.msgs.size() && !hit; i++) hit = c.msgs.get(i).content.toLowerCase().contains(q);
                if (!hit) continue;
            }
            all.add(c);
        }
        Collections.sort(all, new Comparator<Store.Conv>() {
            public int compare(Store.Conv a, Store.Conv b) {
                if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                return a.ts == b.ts ? 0 : (a.ts < b.ts ? 1 : -1);
            }
        });
        if (all.isEmpty()) {
            TextView e = Ui.label(this, T.s("no_history"), 15, Theme.text3, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(40), 0, 0);
            histBox.addView(e, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        long now = System.currentTimeMillis();
        String lastGroup = "";
        boolean firstHeader = true;
        for (final Store.Conv c : all) {
            long age = now - c.ts;
            String g = c.pinned ? T.s("pinned") : age < 86400000L ? T.s("today") : age < 7 * 86400000L ? T.s("week") : age < 30 * 86400000L ? T.s("month") : T.s("earlier");
            if (!g.equals(lastGroup)) {
                lastGroup = g;
                LinearLayout gh = new LinearLayout(this);
                gh.setGravity(Gravity.CENTER_VERTICAL);
                gh.setPadding(dp(8), dp(18), dp(8), dp(4));
                TextView gt = Ui.label(this, g, 14, Theme.text2, true);
                gh.addView(gt, new LinearLayout.LayoutParams(0, -2, 1));
                if (firstHeader) {
                    firstHeader = false;
                    FrameLayout ms = Ui.iconBtn(this, IconView.MULTI, 36, 22, Theme.text2, new View.OnClickListener() {
                        public void onClick(View v) { enterSelect(null); }
                    });
                    gh.addView(ms);
                } else {
                    gh.addView(new View(this), new LinearLayout.LayoutParams(dp(36), dp(1)));
                }
                histBox.addView(gh, new LinearLayout.LayoutParams(-1, -2));
            }
            histBox.addView(convRow(c), new LinearLayout.LayoutParams(-1, dp(56)));
        }
    }

    private View convRow(final Store.Conv c) {
        final boolean sel = selected.contains(c);
        LinearLayout it = new LinearLayout(this);
        it.setGravity(Gravity.CENTER_VERTICAL);
        it.setPadding(dp(8), 0, dp(8), 0);
        it.setBackground(Theme.rippleRect(this, sel ? Theme.surface2 : (c == cur && !selectMode ? Theme.alpha(Theme.surface2, 120) : 0x00000000), 16));
        if (selectMode) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(sel ? Theme.accent : 0x00000000);
            g.setStroke(dp(2), sel ? Theme.accent : Theme.text3);
            View ck = new View(this);
            ck.setBackground(g);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(24), dp(24));
            cl.setMarginEnd(dp(12));
            it.addView(ck, cl);
        }
        TextView t = Ui.label(this, c.title.isEmpty() ? T.s("new_chat") : c.title, 17, Theme.text, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        it.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (c.pinned) it.addView(new IconView(this, IconView.PIN, Theme.text3), new LinearLayout.LayoutParams(dp(18), dp(18)));
        it.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectMode) { if (selected.contains(c)) selected.remove(c); else selected.add(c); updateSelectBar(); refreshHistory(); }
                else openConv(c);
            }
        });
        it.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { if (!selectMode) convMenu(v, c); return true; }
        });
        return it;
    }

    /** Context menu like the reference: rename / pin / multi-select / delete. */
    private void convMenu(final View anchor, final Store.Conv c) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Theme.rect(this, Theme.surface2, 26));
        box.setLayoutDirection(Theme.rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        final PopupWindow pw = new PopupWindow(box, (int) (getResources().getDisplayMetrics().widthPixels * 0.62f), -2, true);
        pw.setElevation(dp(14));
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        String[] labels = {T.s("rename"), c.pinned ? T.s("unpin") : T.s("pin"), T.s("multi"), T.s("delete")};
        int[] icons = {IconView.EDIT, IconView.PIN, IconView.MULTI, IconView.TRASH};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(22), 0, dp(22), 0);
            r.setBackground(Theme.rippleRect(this, 0x00000000, 24));
            int col = i == 3 ? 0xFFFF5A5F : Theme.text;
            TextView t = Ui.label(this, labels[i], 18, col, true);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(new IconView(this, icons[i], col), new LinearLayout.LayoutParams(dp(24), dp(24)));
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    pw.dismiss();
                    if (idx == 0) {
                        Ui.edit(MainActivity.this, T.s("rename"), c.title, "", false, false, new Ui.Str() {
                            public void on(String s) { if (!s.isEmpty()) { c.title = s; Store.save(); refreshHistory(); if (c == cur) Fx.typeInto(title, s, 34); } }
                        });
                    } else if (idx == 1) {
                        c.pinned = !c.pinned;
                        Store.save();
                        refreshHistory();
                    } else if (idx == 2) {
                        enterSelect(c);
                    } else {
                        Store.convs.remove(c);
                        Store.save();
                        if (c == cur) newChat(); else refreshHistory();
                    }
                }
            });
            box.addView(r, new LinearLayout.LayoutParams(-1, dp(62)));
            if (i < 3) {
                View d = new View(this);
                d.setBackgroundColor(Theme.alpha(0xFFFFFFFF, 28));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, 1);
                dl.setMargins(dp(8), 0, dp(8), 0);
                box.addView(d, dl);
            }
        }
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int x = Theme.rtl ? getResources().getDisplayMetrics().widthPixels - pw.getWidth() - dp(24) : dp(24);
        pw.showAtLocation(root, Gravity.TOP | Gravity.START, x, loc[1] + anchor.getHeight() + dp(2));
        box.setAlpha(0f);
        box.setScaleY(0.85f);
        box.animate().alpha(1f).scaleY(1f).setDuration(160).start();
    }

    private void enterSelect(Store.Conv first) {
        selectMode = true;
        selected.clear();
        if (first != null) selected.add(first);
        updateSelectBar();
        refreshHistory();
    }

    private void exitSelect() {
        selectMode = false;
        selected.clear();
        updateSelectBar();
        refreshHistory();
    }

    private void updateSelectBar() {
        delCount.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        delCount.setText(T.s("delete") + " (" + selected.size() + ")");
    }

    private void deleteSelected() {
        if (selected.isEmpty()) { exitSelect(); return; }
        boolean curGone = selected.contains(cur);
        Store.convs.removeAll(selected);
        Store.save();
        exitSelect();
        if (curGone) newChat();
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
    }

    private void showConv() {
        list.removeAllViews();
        title.setText(cur.title);
        int lastAi = -1;
        for (int i = 0; i < cur.msgs.size(); i++) if (cur.msgs.get(i).role.equals("assistant")) lastAi = i;
        for (int i = 0; i < cur.msgs.size(); i++) {
            Store.Msg m = cur.msgs.get(i);
            if (m.role.equals("user")) addUserRow(m);
            else { AiRow r = new AiRow(m, true, i == lastAi); list.addView(r.root); }
        }
        emptyBox.setVisibility(cur.msgs.isEmpty() ? View.VISIBLE : View.GONE);
        if (cur.msgs.isEmpty()) for (int i = 0; i < emptyItems.size(); i++) Fx.enter(emptyItems.get(i), 80L + i * 90L);
        scrollToEnd(false);
    }

    private void addUserRow(final Store.Msg m) {
        emptyBox.setVisibility(View.GONE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.END);
        if (m.imgCount > 0) {
            TextView ic = Ui.label(this, "🖼 " + m.imgCount + " " + T.s("attached"), 13, Theme.text2, true);
            ic.setPadding(dp(8), 0, dp(8), dp(4));
            wrap.addView(ic);
        }
        if (!m.content.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(m.content);
            t.setTextColor(Theme.text);
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f * Theme.font);
            t.setLineSpacing(0, 1.2f);
            t.setTypeface(Fonts.m);
            t.setTextIsSelectable(true);
            t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            t.setPadding(dp(18), dp(13), dp(18), dp(13));
            t.setBackground(Theme.rect(this, Theme.userBubble, 24));
            t.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.8f));
            wrap.addView(t, new LinearLayout.LayoutParams(-2, -2));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(4);
        wrap.setAlpha(0f);
        wrap.setTranslationY(dp(10));
        list.addView(wrap, lp);
        wrap.animate().alpha(1f).translationY(0).setDuration(380).setInterpolator(new android.view.animation.OvershootInterpolator(1.1f)).start();
    }

    // ------------------------------------------------------------------ sending / voice
    private void updateSend() {
        boolean has = input != null && (input.getText().toString().trim().length() > 0 || !atts.isEmpty());
        if (sendBtn == null) return;
        if (streaming) {
            sendBtn.setBackground(Theme.ripple(Theme.circle(Theme.accent)));
            sendIcon.setType(IconView.STOP);
            sendIcon.setColor(0xFFFFFFFF);
        } else if (has) {
            sendBtn.setBackground(Theme.ripple(Theme.circle(Theme.accent)));
            sendIcon.setType(IconView.SEND);
            sendIcon.setColor(0xFFFFFFFF);
        } else {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0x00000000);
            g.setStroke(dp(1.6f), Theme.text);
            sendBtn.setBackground(Theme.ripple(g));
            sendIcon.setType(IconView.VOICE);
            sendIcon.setColor(Theme.text);
        }
    }

    private void haptic(View v) {
        if (Store.bool("haptic", true)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void onSendOrVoice() {
        boolean has = input.getText().toString().trim().length() > 0 || !atts.isEmpty();
        if (streaming || has) { send(); return; }
        Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en".equals(Store.get("voice_lang", "ar")) ? "en-US" : "ar-SA");
        try { startActivityForResult(it, REQ_VOICE); }
        catch (Exception e) { Toast.makeText(this, T.s("voice_fail"), Toast.LENGTH_SHORT).show(); }
    }

    private void send() {
        if (streaming) { stopGen(); return; }
        String q = input.getText().toString().trim();
        if (q.isEmpty() && atts.isEmpty()) return;
        haptic(sendBtn);
        if (panelOpen) togglePanel(false);
        input.setText("");
        Store.Msg u = new Store.Msg();
        u.role = "user";
        u.content = q;
        for (Att a : atts) if (a.b64 != null) u.images.add(a.b64);
        u.imgCount = atts.size();
        atts.clear();
        refreshPreview();
        cur.msgs.add(u);
        if (cur.title.isEmpty()) {
            String t = (q.isEmpty() ? "🖼" : q).replace('\n', ' ');
            cur.title = t.length() > 40 ? t.substring(0, 40) : t;
            Fx.typeInto(title, cur.title, 34);
        }
        cur.ts = System.currentTimeMillis();
        if (!Store.convs.contains(cur)) Store.convs.add(0, cur);
        Store.put("cur", cur.id);
        addUserRow(u);
        startAnswer();
    }

    private void toggleSound() {
        boolean on = !Store.bool("sound", false);
        Store.putBool("sound", on);
        soundIcon.setType(on ? IconView.SOUND_ON : IconView.SOUND_OFF);
        if (on) ensureTts(); else if (tts != null) tts.stop();
        Toast.makeText(this, on ? T.s("s_sound") + " ✓" : T.s("s_sound") + " ✕", Toast.LENGTH_SHORT).show();
    }

    private void ensureTts() {
        if (tts != null) return;
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) tts.setLanguage(Theme.rtl ? new Locale("ar") : Locale.US);
            }
        });
    }

    private void speak(String text) {
        if (!Store.bool("sound", false)) return;
        ensureTts();
        if (!ttsReady) return;
        String clean = text.replace("```", " ").replace("*", "").replace("#", "");
        if (clean.length() > 3500) clean = clean.substring(0, 3500);
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "karrar");
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
        speak(ai.content);
    }

    private void stopGen() {
        if (handle != null) handle.cancel();
        if (tts != null) tts.stop();
        if (liveRow != null) {
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

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        try {
            if (req == REQ_FILE && data.getData() != null) {
                InputStream in = getContentResolver().openInputStream(data.getData());
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n, total = 0;
                while ((n = in.read(buf)) > 0 && total < 40000) { bo.write(buf, 0, n); total += n; }
                in.close();
                input.append("\n```\n" + new String(bo.toByteArray(), "UTF-8") + "\n```\n");
            } else if (req == REQ_ALBUM) {
                List<Uri> uris = new ArrayList<Uri>();
                if (data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
                } else if (data.getData() != null) uris.add(data.getData());
                for (Uri u : uris) { Bitmap t = decode(u, 200); if (t != null) addAttachment(u, t); }
            } else if (req == REQ_CAMERA && data.getExtras() != null) {
                Bitmap bm = (Bitmap) data.getExtras().get("data");
                if (bm != null) addAttachment(null, bm);
            } else if (req == REQ_VOICE) {
                ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) input.append(r.get(0));
            }
        } catch (Exception e) {
            Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareText(String s) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, s);
        startActivity(Intent.createChooser(i, T.s("share")));
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
        IconView thinkIcon, thinkChev, like, dislike, logoV;
        Fx.Dots dots;
        boolean thinkShown, actionsShown;
        android.animation.ObjectAnimator breathe;
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
            logoV = new IconView(MainActivity.this, IconView.LOGO, Theme.accent);
            who.addView(logoV, new LinearLayout.LayoutParams(dp(26), dp(26)));
            root.addView(who, new LinearLayout.LayoutParams(-2, -2));

            // sources chip
            srcBox = new LinearLayout(MainActivity.this);
            srcBox.setOrientation(LinearLayout.VERTICAL);
            srcBox.setVisibility(View.GONE);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.topMargin = dp(8);
            root.addView(srcBox, sl);

            Fx.Shimmer sh = new Fx.Shimmer(MainActivity.this, Theme.text2, Theme.text);
            sh.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f * Theme.font);
            sh.setTypeface(Fonts.b);
            status = sh;
            dots = new Fx.Dots(MainActivity.this);
            dots.setVisibility(View.GONE);
            root.addView(dots, new LinearLayout.LayoutParams(-2, -2));
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
            Fx.Shimmer tsh = new Fx.Shimmer(MainActivity.this, Theme.text2, Theme.accent);
            tsh.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f * Theme.font);
            tsh.setTypeface(Fonts.b);
            tsh.setActive(false);
            thinkTitle = tsh;
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
            thinkBodyWrap.setVisibility(View.GONE);
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
                dots.setVisibility(View.VISIBLE);
                breathe = Fx.breathe(logoV, 1.2f);
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
            if (s == null) { status.setVisibility(View.GONE); return; }
            dots.setVisibility(View.GONE);
            status.setVisibility(View.VISIBLE);
            status.setText(s);
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

        void applyThink() {
            if (thinkShown == thinkOpen) return;
            thinkShown = thinkOpen;
            Fx.expand(thinkBodyWrap, thinkOpen);
        }

        void refreshThink() {
            boolean has = !m.reasoning.isEmpty();
            thinkBox.setVisibility(has ? View.VISIBLE : View.GONE);
            if (!has) return;
            thinkBody.setText(m.reasoning);
            applyThink();
            thinkChev.animate().rotation(thinkOpen ? 180 : 0).setDuration(220).start();
            thinkTitle.setText(thinkingNow ? T.s("thinking") : String.format(T.s("thought_for"), Math.max(1, m.thinkSecs)));
            ((Fx.Shimmer) thinkTitle).setActive(thinkingNow);
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
            if (!m.content.isEmpty() || !m.reasoning.isEmpty() || done) dots.setVisibility(View.GONE);
            refreshThink();
            String txt = m.content;
            if (!done && !txt.isEmpty()) txt += " ▍";
            Md.render(MainActivity.this, content, txt, done);
            if (done && !actionsShown) {
                actionsShown = true;
                actions.setVisibility(View.VISIBLE);
                actions.setAlpha(0f);
                actions.animate().alpha(1f).setDuration(350).start();
                if (breathe != null) { breathe.cancel(); breathe = null; logoV.setScaleX(1f); logoV.setScaleY(1f); }
            } else if (!done) {
                actions.setVisibility(View.GONE);
            }
            demoNote.setVisibility(done && m.demo ? View.VISIBLE : View.GONE);
            regenBtn.setVisibility(done && isLast ? View.VISIBLE : View.GONE);
            paintVote();
        }
    }
}

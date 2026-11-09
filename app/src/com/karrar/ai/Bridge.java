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
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Native services exposed to the HTML/JS UI as window.Android. */
public class Bridge {
    static final int REQ_ALBUM = 1, REQ_CAMERA = 2, REQ_FILE = 3, REQ_VOICE = 4, REQ_PERM = 5;

    private final Activity act;
    private final WebView wv;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final HashMap<Integer, Api.Handle> handles = new HashMap<Integer, Api.Handle>();
    private int idAlbum, idCamera, idFile, idVoice, idGallery;
    private TextToSpeech tts;
    private boolean ttsReady;

    public Bridge(Activity a, WebView w) {
        act = a;
        wv = w;
    }

    // ------------------------------------------------------------------ helpers
    private void js(final String script) {
        ui.post(new Runnable() { public void run() { wv.evaluateJavascript(script, null); } });
    }

    private void cb(int id, String json) {
        js("window.__cb(" + id + "," + JSONObject.quote(json) + ")");
    }

    private Bitmap decode(Uri uri, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = act.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth / sample > max * 2 || o.outHeight / sample > max * 2) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            in = act.getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private String b64(Bitmap b, int maxDim, int q) {
        float k = Math.min(1f, maxDim / (float) Math.max(b.getWidth(), b.getHeight()));
        Bitmap s = k < 1f ? Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true) : b;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        s.compress(Bitmap.CompressFormat.JPEG, q, bo);
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    private String thumb(Bitmap b) { return "data:image/jpeg;base64," + b64(b, 300, 78); }

    // ------------------------------------------------------------------ prefs
    @JavascriptInterface
    public String getPref(String k, String d) { return Store.get(k, d); }

    @JavascriptInterface
    public void setPref(String k, String v) {
        Store.put(k, v);
        if ("lang".equals(k)) Theme.rtl = "ar".equals(v);
    }

    @JavascriptInterface
    public boolean hasKey() { return Api.hasKey(); }

    @JavascriptInterface
    public String defaults() {
        try {
            JSONObject o = new JSONObject();
            o.put("lang", "ar").put("theme", "dark").put("base_url", "https://api.deepseek.com")
                    .put("model", "deepseek-chat").put("think_model", "deepseek-reasoner").put("temp", "0.7")
                    .put("stream", "1").put("haptic", "1").put("save_hist", "1").put("font_i", "2").put("voice_lang", "ar");
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ------------------------------------------------------------------ AI
    @JavascriptInterface
    public void chat(final int id, String msgsJson, boolean think, String ctx) {
        try {
            JSONArray a = new JSONArray(msgsJson);
            List<Store.Msg> list = new ArrayList<Store.Msg>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Store.Msg m = new Store.Msg();
                m.role = o.optString("role", "user");
                m.content = o.optString("content", "");
                JSONArray im = o.optJSONArray("images");
                if (im != null) for (int j = 0; j < im.length(); j++) m.images.add(im.getString(j));
                list.add(m);
            }
            Api.Handle h = Api.chat(list, think, ctx, new Api.Cb() {
                public void onThink(String d) { ev(id, "think", "d", d); }
                public void onText(String d) { ev(id, "text", "d", d); }
                public void onDone() { ev(id, "done", null, null); }
                public void onError(String e) { ev(id, "err", "m", e); }
            }, ui);
            handles.put(id, h);
        } catch (Exception e) {
            ev(id, "err", "m", String.valueOf(e.getMessage()));
        }
    }

    private void ev(int id, String t, String key, String val) {
        try {
            JSONObject o = new JSONObject().put("t", t);
            if (key != null) o.put(key, val);
            cb(id, o.toString());
        } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public void cancel(int id) {
        Api.Handle h = handles.remove(id);
        if (h != null) h.cancel();
    }

    @JavascriptInterface
    public void search(final int id, final String q) {
        new Thread(new Runnable() {
            public void run() { cb(id, Search.run(q).toString()); }
        }).start();
    }

    @JavascriptInterface
    public void test(final int id) {
        new Thread(new Runnable() {
            public void run() {
                String r = Api.test();
                cb(id, r == null ? "null" : JSONObject.quote(r));
            }
        }).start();
    }

    // ------------------------------------------------------------------ accounts
    private String res(String err) {
        try { return err == null ? new JSONObject().put("ok", 1).toString() : new JSONObject().put("err", err).toString(); }
        catch (Exception e) { return "{}"; }
    }

    @JavascriptInterface
    public String login(String email, String pass) { return res(Accounts.login(email, pass)); }

    @JavascriptInterface
    public String signup(String n, String e, String p, String p2) { return res(Accounts.signUp(n, e, p, p2)); }

    @JavascriptInterface
    public void google(final int id) {
        ui.post(new Runnable() {
            public void run() {
                GoogleAuth.start(act, new GoogleAuth.Cb() {
                    public void done(String err, String name, String email) {
                        try {
                            JSONObject o = new JSONObject();
                            if (err != null) o.put("err", err); else o.put("name", name == null ? "" : name).put("email", email);
                            cb(id, o.toString());
                        } catch (Exception ignored) { }
                    }
                });
            }
        });
    }

    @JavascriptInterface public void guest() { Accounts.guest(); }
    @JavascriptInterface public void logout() { Accounts.logout(); }
    @JavascriptInterface public void deleteAccount(String email) { Accounts.delete(email); }

    // ------------------------------------------------------------------ media / files
    @JavascriptInterface
    public void pickImages(int id) {
        idAlbum = id;
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("image/*");
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        start(it, REQ_ALBUM, id);
    }

    @JavascriptInterface
    public void camera(int id) {
        idCamera = id;
        start(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQ_CAMERA, id);
    }

    @JavascriptInterface
    public void pickFile(int id) {
        idFile = id;
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("*/*");
        it.addCategory(Intent.CATEGORY_OPENABLE);
        start(it, REQ_FILE, id);
    }

    private void start(final Intent it, final int req, final int id) {
        ui.post(new Runnable() {
            public void run() {
                try { act.startActivityForResult(it, req); }
                catch (Exception e) { cb(id, req == REQ_FILE ? "{}" : "[]"); }
            }
        });
    }

    private boolean hasGalleryPerm() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        return act.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    @JavascriptInterface
    public void gallery(final int id) {
        idGallery = id;
        if (!hasGalleryPerm()) {
            final String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
            ui.post(new Runnable() { public void run() { act.requestPermissions(new String[]{p}, REQ_PERM); } });
            return;
        }
        loadGallery(id);
    }

    private void loadGallery(final int id) {
        new Thread(new Runnable() {
            public void run() {
                JSONArray items = new JSONArray();
                try {
                    Cursor c = act.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Images.Media._ID}, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
                    if (c != null) {
                        while (c.moveToNext() && items.length() < 14) {
                            Uri u = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(c.getLong(0)));
                            Bitmap bm = decode(u, 260);
                            if (bm != null) items.put(new JSONObject().put("uri", u.toString()).put("th", thumb(bm)));
                        }
                        c.close();
                    }
                    cb(id, new JSONObject().put("perm", true).put("items", items).toString());
                } catch (Exception e) {
                    cb(id, "{\"perm\":true,\"items\":[]}");
                }
            }
        }).start();
    }

    @JavascriptInterface
    public void imageData(final int id, final String uri) {
        new Thread(new Runnable() {
            public void run() {
                Bitmap b = decode(Uri.parse(uri), 1024);
                cb(id, "{\"b64\":\"" + (b == null ? "" : b64(b, 1024, 82)) + "\"}");
            }
        }).start();
    }

    @JavascriptInterface
    public void voice(int id) {
        idVoice = id;
        Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en".equals(Store.get("voice_lang", "ar")) ? "en-US" : "ar-SA");
        start(it, REQ_VOICE, id);
    }

    /** Called by the activity. */
    public void onResult(int req, int res, final Intent data) {
        final int ok = Activity.RESULT_OK;
        try {
            if (req == REQ_ALBUM) {
                final int id = idAlbum;
                if (res != ok || data == null) { cb(id, "[]"); return; }
                final List<Uri> uris = new ArrayList<Uri>();
                if (data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
                } else if (data.getData() != null) uris.add(data.getData());
                new Thread(new Runnable() {
                    public void run() {
                        JSONArray a = new JSONArray();
                        for (Uri u : uris) {
                            Bitmap full = decode(u, 1024);
                            if (full == null) continue;
                            try { a.put(new JSONObject().put("th", thumb(full)).put("b64", b64(full, 1024, 82))); } catch (Exception ignored) { }
                        }
                        cb(id, a.toString());
                    }
                }).start();
            } else if (req == REQ_CAMERA) {
                int id = idCamera;
                if (res != ok || data == null || data.getExtras() == null) { cb(id, "[]"); return; }
                Bitmap bm = (Bitmap) data.getExtras().get("data");
                JSONArray a = new JSONArray();
                if (bm != null) a.put(new JSONObject().put("th", thumb(bm)).put("b64", b64(bm, 1024, 85)));
                cb(id, a.toString());
            } else if (req == REQ_FILE) {
                final int id = idFile;
                if (res != ok || data == null || data.getData() == null) { cb(id, "{}"); return; }
                final Uri u = data.getData();
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            String name = "file";
                            Cursor c = act.getContentResolver().query(u, null, null, null, null);
                            if (c != null) {
                                if (c.moveToFirst()) {
                                    int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                                    if (ix >= 0) name = c.getString(ix);
                                }
                                c.close();
                            }
                            InputStream in = act.getContentResolver().openInputStream(u);
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            byte[] buf = new byte[4096];
                            int n, total = 0;
                            while ((n = in.read(buf)) > 0 && total < 40000) { bo.write(buf, 0, n); total += n; }
                            in.close();
                            cb(id, new JSONObject().put("name", name).put("text", new String(bo.toByteArray(), "UTF-8")).toString());
                        } catch (Exception e) {
                            cb(id, "{}");
                        }
                    }
                }).start();
            } else if (req == REQ_VOICE) {
                int id = idVoice;
                String t = "";
                if (res == ok && data != null) {
                    ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (r != null && !r.isEmpty()) t = r.get(0);
                }
                cb(id, JSONObject.quote(t));
            }
        } catch (Exception ignored) { }
    }

    public void onPermission(int req, int[] res) {
        if (req != REQ_PERM) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) loadGallery(idGallery);
        else cb(idGallery, "{\"perm\":false,\"items\":[]}");
    }

    // ------------------------------------------------------------------ speech, clipboard, misc
    @JavascriptInterface
    public void speak(final String text) {
        ui.post(new Runnable() {
            public void run() {
                if (tts == null) {
                    tts = new TextToSpeech(act, new TextToSpeech.OnInitListener() {
                        public void onInit(int status) {
                            ttsReady = status == TextToSpeech.SUCCESS;
                            if (ttsReady) { tts.setLanguage(Theme.rtl ? new Locale("ar") : Locale.US); doSpeak(text); }
                        }
                    });
                } else if (ttsReady) doSpeak(text);
            }
        });
    }

    private void doSpeak(String t) {
        String clean = t.replace("```", " ").replace("*", "").replace("#", "");
        if (clean.length() > 3500) clean = clean.substring(0, 3500);
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "karrar");
    }

    @JavascriptInterface
    public void stopSpeak() {
        ui.post(new Runnable() { public void run() { if (tts != null) tts.stop(); } });
    }

    public void destroy() { if (tts != null) { tts.stop(); tts.shutdown(); } }

    @JavascriptInterface
    public void copy(final String t) {
        ui.post(new Runnable() {
            public void run() {
                ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("karrar", t));
            }
        });
    }

    @JavascriptInterface
    public void share(final String t) {
        ui.post(new Runnable() {
            public void run() {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, t);
                act.startActivity(Intent.createChooser(i, "Share"));
            }
        });
    }

    @JavascriptInterface
    public void open(final String url) {
        ui.post(new Runnable() {
            public void run() {
                try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
            }
        });
    }

    @JavascriptInterface
    public void toast(final String m) {
        ui.post(new Runnable() { public void run() { Toast.makeText(act, m, Toast.LENGTH_SHORT).show(); } });
    }

    @JavascriptInterface
    public void haptic() {
        ui.post(new Runnable() { public void run() { wv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); } });
    }

    @JavascriptInterface
    public void setBars(final String bg, final boolean darkIcons) {
        ui.post(new Runnable() {
            public void run() {
                try {
                    int c = android.graphics.Color.parseColor(bg);
                    act.getWindow().setStatusBarColor(c);
                    act.getWindow().setNavigationBarColor(c);
                    View d = act.getWindow().getDecorView();
                    int f = d.getSystemUiVisibility();
                    if (darkIcons) {
                        f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    } else {
                        f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        if (Build.VERSION.SDK_INT >= 26) f &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                    d.setSystemUiVisibility(f);
                    d.setBackgroundColor(c);
                } catch (Exception ignored) { }
            }
        });
    }

    @JavascriptInterface public void exitApp() { act.finish(); }
    @JavascriptInterface public String deviceModel() { return Build.MODEL; }
}

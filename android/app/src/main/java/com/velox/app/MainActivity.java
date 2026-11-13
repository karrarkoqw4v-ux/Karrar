package com.velox.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * تطبيق Velox: يعرض ملفات الموقع المضمّنة (assets/www) داخل WebView على عنوان آمن https://velox.app
 * ويتصل بخادم Velox الذي يحدده المستخدم من داخل التطبيق.
 */
public class MainActivity extends Activity {
    private static final String HOST = "velox.app";
    private static final int BG = 0xFF070B18;
    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_REQ = 4711;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);

        web = new WebView(this);
        web.setBackgroundColor(BG);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString() + " VeloxApp/1.0");

        web.addJavascriptInterface(new Bridge(), "VeloxApp");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "اختر ملف الربط"), FILE_REQ);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                try {
                    InputStream in = getAssets().open("www" + path);
                    return new WebResourceResponse(mime(path), null, in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                            new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        if (state == null) {
            web.loadUrl("https://" + HOST + "/index.html");
        } else {
            web.restoreState(state);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_REQ && filePathCallback != null) {
            Uri[] result = null;
            if (res == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    private static String mime(String p) {
        String x = p.toLowerCase();
        if (x.endsWith(".html")) return "text/html";
        if (x.endsWith(".css")) return "text/css";
        if (x.endsWith(".js")) return "application/javascript";
        if (x.endsWith(".json") || x.endsWith(".webmanifest")) return "application/json";
        if (x.endsWith(".svg")) return "image/svg+xml";
        if (x.endsWith(".png")) return "image/png";
        if (x.endsWith(".webp")) return "image/webp";
        if (x.endsWith(".gif")) return "image/gif";
        if (x.endsWith(".jpg") || x.endsWith(".jpeg")) return "image/jpeg";
        if (x.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** يُستدعى من الصفحة لحفظ الملفات المحمّلة (ZIP ونسخ احتياطية) في مجلد التنزيلات. */
    private class Bridge {
        @JavascriptInterface
        public void save(String name, String mimeType, String base64) {
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                    v.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(data);
                    os.close();
                    toast("تم الحفظ في مجلد التنزيلات: " + safe);
                } else {
                    File dir = getExternalFilesDir(null);
                    File f = new File(dir, safe);
                    FileOutputStream os = new FileOutputStream(f);
                    os.write(data);
                    os.close();
                    toast("تم الحفظ: " + f.getAbsolutePath());
                }
            } catch (Exception e) {
                toast("تعذّر حفظ الملف");
            }
        }
    }
}

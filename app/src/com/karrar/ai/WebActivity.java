package com.karrar.ai;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.InputStream;

/** Hosts the HTML/CSS/JS interface (assets/www) in a WebView served from a virtual https origin. */
public class WebActivity extends Activity {
    private static final String HOST = "appassets.local";
    private WebView wv;
    private Bridge bridge;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Store.init(this);
        getWindow().getDecorView().setBackgroundColor(0xFF0F0F10);
        getWindow().setStatusBarColor(0xFF0F0F10);
        getWindow().setNavigationBarColor(0xFF0F0F10);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0F0F10);
        root.setFitsSystemWindows(true);
        wv = new WebView(this);
        wv.setBackgroundColor(0xFF0F0F10);
        wv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wv.setLongClickable(false);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);               // the app controls font size itself
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        bridge = new Bridge(this, wv);
        wv.addJavascriptInterface(bridge, "Android");
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) {
                    String path = u.getPath();
                    if (path == null || path.equals("/")) path = "/www/index.html";
                    try {
                        InputStream in = getAssets().open(path.substring(1));
                        return new WebResourceResponse(mime(path), "UTF-8", in);
                    } catch (Exception e) {
                        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found", null, null);
                    }
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (HOST.equals(r.getUrl().getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception ignored) { }
                return true;
            }
        });
        root.addView(wv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        wv.loadUrl("https://" + HOST + "/www/index.html");
    }

    private static String mime(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    @Override
    public void onBackPressed() {
        wv.evaluateJavascript("window.__back&&window.__back()", new ValueCallback<String>() {
            public void onReceiveValue(String v) {
                if (!"true".equals(v)) finish();
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        bridge.onResult(req, res, data);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        bridge.onPermission(req, res);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bridge.destroy();
        wv.destroy();
    }
}

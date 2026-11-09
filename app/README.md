# Karrar AI — Android chat app (HTML / CSS / JS UI in a WebView)

* `assets/www/` — the whole interface: 14 CSS files (tokens, animations, chat, search, settings…), 16 JS files,
  bundled fonts (Noto Sans Arabic + Inter), Lucide icons and 3.7k Twemoji SVGs (no system emoji).
* `src/com/karrar/ai/` — native shell: `WebActivity` (WebView), `Bridge` (JS <-> Android: AI streaming, web search,
  Google sign-in, gallery/camera/files, voice, TTS), `Api`, `Search`, `GoogleAuth`, `Accounts`.
* Open `assets/www/index.html` in a desktop browser to develop the UI (`js/mock.js` fakes the native side).

Build (needs `aapt`, `zipalign`, `apksigner`, a JDK and `tools/jars/{android-all,dx}.jar`): `./tools/build.sh`

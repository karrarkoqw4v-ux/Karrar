#!/usr/bin/env bash
# Builds Karrar-AI.apk without the Android SDK (aapt + javac + dx + apksigner).
# Needs: aapt, zipalign, apksigner (apt), JDK, and the two jars in $TOOLS (android-all.jar, dx.jar).
set -euo pipefail
APP="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${TOOLS:-$APP/tools/jars}"
OUT="$APP/build"
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex"
# EMBED_KEY=1 builds a PRIVATE copy with config/default_provider.json bundled (never commit that APK)
EXTRA=()
FINAL="$APP/Karrar-AI.apk"
if [ "${EMBED_KEY:-0}" = "1" ]; then
  [ -f "$APP/config/default_provider.json" ] || { echo "config/default_provider.json missing"; exit 1; }
  mkdir -p "$OUT/extra" && cp "$APP/config/default_provider.json" "$OUT/extra/"
  EXTRA=(-A "$OUT/extra")
  FINAL="$OUT/Bawsala-private.apk"
fi

aapt package -f -M "$APP/AndroidManifest.xml" -S "$APP/res" -A "$APP/assets" "${EXTRA[@]}" -I "$TOOLS/android-all.jar" \
     --min-sdk-version 24 --target-sdk-version 34 -F "$OUT/unsigned.apk"
javac --release 8 -Xlint:-options -cp "$TOOLS/android-all.jar" -d "$OUT/classes" $(find "$APP/src" -name '*.java') 2>&1
java -cp "$TOOLS/dx.jar" com.android.dx.command.Main --dex --min-sdk-version=24 --output="$OUT/dex/classes.dex" "$OUT/classes"
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KS="$APP/tools/release.keystore"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass karrar123 -keypass karrar123 -alias karrar \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Karrar AI" >/dev/null 2>&1
apksigner sign --ks "$KS" --ks-pass pass:karrar123 --key-pass pass:karrar123 --v1-signing-enabled true --v2-signing-enabled true \
  --out "$FINAL" "$OUT/aligned.apk"
apksigner verify -v "$FINAL" | head -5
ls -la "$FINAL"

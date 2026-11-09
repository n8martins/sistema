#!/usr/bin/env bash
# Builds tv/dist/sistema-home.apk without Android Studio or Gradle.
#
# Needs: JDK 17+, aapt2, dx (dalvik-exchange), zipalign, apksigner, and an API 34 android.jar.
#   Ubuntu/Debian:  sudo apt install aapt apksigner zipalign dalvik-exchange
#   android.jar:    export ANDROID_JAR=/path/to/platforms/android-34/android.jar
#
# Android Studio users can ignore this and run ./gradlew assembleDebug in tv/launcher.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TV="$(dirname "$HERE")"
SRC="$TV/launcher/app/src/main"
OUT="$TV/dist"
PKG="com.sistema.home"
VERSION_CODE="${VERSION_CODE:-2}"
VERSION_NAME="${VERSION_NAME:-1.1}"
ANDROID_JAR="${ANDROID_JAR:-${ANDROID_HOME:-$HOME/Android/Sdk}/platforms/android-34/android.jar}"
KEYSTORE="${KEYSTORE:-$HOME/.android/sistema-home.jks}"
KS_PASS="${KS_PASS:-sistema-home}"
DX="$(command -v dalvik-exchange || command -v dx || true)"

for tool in aapt2 javac zipalign apksigner zip; do
  command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }
done
[ -n "$DX" ] || { echo "Missing tool: dx (apt install dalvik-exchange)" >&2; exit 1; }
[ -f "$ANDROID_JAR" ] || { echo "android.jar not found. Set ANDROID_JAR." >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes" "$WORK/gen" "$OUT"

# Gradle reads the package from build.gradle; aapt2 needs it in the manifest.
sed "s|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$PKG\">|" \
  "$SRC/AndroidManifest.xml" > "$WORK/AndroidManifest.xml"

echo "> resources"
aapt2 compile --dir "$SRC/res" -o "$WORK/res.zip"
aapt2 link -I "$ANDROID_JAR" \
  --manifest "$WORK/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  -A "$SRC/assets" -0 ttf \
  --java "$WORK/gen" \
  -o "$WORK/base.apk" "$WORK/res.zip"

echo "> java"
javac -nowarn -Xlint:none -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -d "$WORK/classes" \
  $(find "$SRC/java" "$WORK/gen" -name '*.java') 2>&1 | grep -vE "^(Picked up|warning: \[options\]|[0-9]+ warnings?$)" || true
[ -n "$(find "$WORK/classes" -name '*.class' -print -quit)" ] || { echo "javac failed" >&2; exit 1; }

echo "> dex"
"$DX" --dex --min-sdk-version=26 --output="$WORK/classes.dex" "$WORK/classes"

echo "> package"
cp "$WORK/base.apk" "$WORK/unsigned.apk"
(cd "$WORK" && zip -q -j unsigned.apk classes.dex)
zipalign -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias sistema -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Sistema Home" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias sistema \
  --out "$OUT/sistema-home.apk" "$WORK/aligned.apk" 2>&1 | grep -v "^Picked up" || true
rm -f "$OUT/sistema-home.apk.idsig"
apksigner verify "$OUT/sistema-home.apk" 2>&1 | grep -v "^Picked up" || true
echo "Built $OUT/sistema-home.apk ($(du -h "$OUT/sistema-home.apk" | cut -f1))"

#!/bin/bash
# Build Zen Clicker APK with the bundled SDK + JDK (no gradle).
set -e
cd "$(dirname "$0")"

export PATH="$PWD/tools/jdk17/bin:$PATH"
SDK=tools/android-sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
SRC=app/src/main
OUT=build
PKG=com/zen/autoclicker

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/aligned" "$OUT/gen"

echo "[1/6] Compile resources (aapt2)"
"$BT/aapt2.exe" compile --dir "$SRC/res" -o "$OUT/res.zip"

echo "[2/6] Link resources + manifest"
"$BT/aapt2.exe" link -o "$OUT/base.apk" -I "$PLATFORM" --manifest "$SRC/AndroidManifest.xml" \
  --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 2 --version-name 1.1 \
  "$OUT/res.zip"

echo "[3/6] Compile Java"
tools/jdk17/bin/javac.exe -source 11 -target 11 -nowarn \
  -classpath "$PLATFORM" \
  -d "$OUT/classes" \
  $(find "$OUT/gen" -name "*.java") \
  $(find "$SRC/java" -name "*.java")

echo "[4/6] Dex (d8)"
find "$OUT/classes" -name "*.class" > "$OUT/classes.txt"
tools/jdk17/bin/java.exe -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --release --lib "$PLATFORM" --min-api 26 \
  --output "$OUT/dex" \
  @"$OUT/classes.txt"

echo "[5/6] Package + align"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
tools/jdk17/bin/jar.exe -uf "$OUT/unsigned.apk" -C "$OUT/dex" classes.dex
"$BT/zipalign.exe" -f 4 "$OUT/unsigned.apk" "$OUT/aligned/unsigned.apk"

echo "[6/6] Sign"
KEYSTORE=debug.keystore
if [ ! -f "$KEYSTORE" ]; then
  tools/jdk17/bin/keytool.exe -genkeypair -keystore "$KEYSTORE" \
    -storepass android -keypass android -alias zen \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Zen Clicker, O=Zen" >/dev/null 2>&1
fi

tools/jdk17/bin/java.exe -jar "$BT/lib/apksigner.jar" sign --ks "$KEYSTORE" --ks-pass pass:android \
  --key-pass pass:android --v4-signing-enabled false --out "ZenClicker.apk" "$OUT/aligned/unsigned.apk"

echo ""
echo "Built: ZenClicker.apk (Ready to install)"

#!/usr/bin/env bash
# Builds build/simplehome.apk with plain SDK tools (no Gradle).
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-34/android.jar
rm -rf build && mkdir -p build/res build/gen build/classes build/dex
"$BT/aapt2" compile --dir res -o build/res/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -o build/unsigned.apk \
  --java build/gen build/res/res.zip
javac -source 17 -target 17 -cp "$JAR" -d build/classes \
  $(find src build/gen -name '*.java') 2>&1 | grep -v 'source value 8\|target value 8\|bootstrap\|^warning: \[options\]\|^[0-9] warning' || true
"$BT/d8" --release --min-api 30 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
cp build/unsigned.apk build/withdex.apk
(cd build/dex && zip -q ../withdex.apk classes.dex)
"$BT/zipalign" -f 4 build/withdex.apk build/aligned.apk
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
  --key-pass pass:android --out build/simplehome.apk build/aligned.apk
echo "built build/simplehome.apk"

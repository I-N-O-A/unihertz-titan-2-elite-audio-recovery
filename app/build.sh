#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${1:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ]; then
  echo "Usage: $0 /path/to/android-sdk" >&2
  exit 2
fi

BT="$SDK/build-tools/35.0.1"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
OUT="$ROOT/out"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$ANDROID_JAR"; do
  [ -e "$f" ] || { echo "Missing: $f" >&2; exit 3; }
done

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir "$ROOT/res" -o "$OUT/res.zip"
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  "$OUT/res.zip" \
  --java "$OUT/gen" \
  --min-sdk-version 31 \
  --target-sdk-version 35 \
  --version-code 4 \
  --version-name 1.3

find "$ROOT/src" "$OUT/gen" -name '*.java' -print0 | \
  xargs -0 javac -source 11 -target 11 -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$OUT/classes"

"$BT/d8" --lib "$ANDROID_JAR" --min-api 31 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -u "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -P 16 4 "$OUT/unsigned.apk" "$OUT/TitanAudioRepair-1.3-aligned-unsigned.apk"

if [ -n "${KEYSTORE:-}" ]; then
  : "${KEY_ALIAS:?Set KEY_ALIAS}"
  : "${KEYSTORE_PASS:?Set KEYSTORE_PASS}"
  : "${KEY_PASS:=$KEYSTORE_PASS}"
  "$BT/apksigner" sign \
    --v1-signing-enabled false \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:KEYSTORE_PASS \
    --key-pass env:KEY_PASS \
    --out "$OUT/TitanAudioRepair-1.3-signed.apk" \
    "$OUT/TitanAudioRepair-1.3-aligned-unsigned.apk"
  "$BT/apksigner" verify --verbose --print-certs "$OUT/TitanAudioRepair-1.3-signed.apk"
else
  echo "Built unsigned APK: $OUT/TitanAudioRepair-1.3-aligned-unsigned.apk"
  echo "To sign, set KEYSTORE, KEY_ALIAS, KEYSTORE_PASS and optional KEY_PASS."
fi

#!/data/data/com.termux/files/usr/bin/bash
# ╔══════════════════════════════════════════════╗
# ║  OBLITERATE BT — APK Build v5 (final)       ║
# ║  aapt2 + javac + dx → working signed APK    ║
# ╚══════════════════════════════════════════════╝

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
BUILD_DIR="$PROJECT_DIR/build"
GEN_DIR="$BUILD_DIR/gen"
CLASSES_DIR="$BUILD_DIR/classes"
DEX_DIR="$BUILD_DIR/dex"
OUTPUT_DIR="$BUILD_DIR/outputs"
RES_DIR="$PROJECT_DIR/app/src/main/res"
SRC_DIR="$PROJECT_DIR/app/src/main/java"
MANIFEST="$PROJECT_DIR/app/src/main/AndroidManifest.xml"
ANDROID_JAR="$HOME/android-sdk/platforms/android-28/android.jar"
KEYSTORE="$PROJECT_DIR/debug.keystore"

COMPILED_RES="$BUILD_DIR/compiled-res.zip"
RES_APK="$BUILD_DIR/resources.ap_"
UNSIGNED="$BUILD_DIR/obliterate-bt-unsigned.apk"
ALIGNED="$BUILD_DIR/obliterate-bt-aligned.apk"
SIGNED="$OUTPUT_DIR/obliterate-bt.apk"

AAPT2="aapt2"
JAVAC="javac"
DX="dx"
APKSIGNER="apksigner"
ZIPALIGN="zipalign"

for tool in $AAPT2 $JAVAC $DX $APKSIGNER $ZIPALIGN; do
    command -v "$tool" >/dev/null 2>&1 || { echo "Missing: $tool"; exit 1; }
done

[ ! -f "$ANDROID_JAR" ] && { echo "Missing: $ANDROID_JAR"; exit 1; }

set -euo pipefail

echo "╔══════════════════════════════════════╗"
echo "║  OBLITERATE BT — BUILD v5           ║"
echo "╚══════════════════════════════════════╝"
echo ""

# Clean
echo "[1/6] Cleaning..."
rm -rf "$BUILD_DIR"
mkdir -p "$GEN_DIR" "$CLASSES_DIR" "$DEX_DIR" "$OUTPUT_DIR"

# aapt2 compile + link
echo "[2/6] aapt2 compile + link..."
$AAPT2 compile --dir "$RES_DIR" -o "$COMPILED_RES" 2>&1

$AAPT2 link \
    -o "$RES_APK" \
    --manifest "$MANIFEST" \
    -I "$ANDROID_JAR" \
    --java "$GEN_DIR" \
    --min-sdk-version 21 \
    --target-sdk-version 28 \
    --version-code 1 \
    --version-name "2.0" \
    "$COMPILED_RES" 2>&1

echo "  ✓ R.java + resources.ap_ generated"

# javac
echo "[3/6] javac..."
JAVA_FILES=$(find "$SRC_DIR" -name "*.java" | tr '\n' ' ')
$JAVAC \
    -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$CLASSES_DIR" \
    -cp "$GEN_DIR" \
    $JAVA_FILES 2>&1

CLASS_COUNT=$(find "$CLASSES_DIR" -name "*.class" | wc -l)
echo "  ✓ $CLASS_COUNT classes compiled"

# dx (NOT d8 — d8 3.3.20 is broken for inner classes)
echo "[4/6] dx..."
$DX --dex --output="$DEX_DIR/classes.dex" "$CLASSES_DIR" 2>&1
echo "  ✓ classes.dex: $(du -h "$DEX_DIR/classes.dex" | cut -f1)"

# Pack APK
echo "[5/6] Pack + align + sign..."
cp "$RES_APK" "$UNSIGNED"
(cd "$DEX_DIR" && jar uf "$UNSIGNED" classes.dex)

$ZIPALIGN -f 4 "$UNSIGNED" "$ALIGNED" 2>&1

# Generate keystore if missing
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias debug -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=OBLITERATE" 2>/dev/null
fi

$APKSIGNER sign \
    --ks "$KEYSTORE" --ks-key-alias debug \
    --ks-pass pass:android --key-pass pass:android \
    --out "$SIGNED" "$ALIGNED" 2>&1

echo ""

# Verify
echo "[6/6] Verify..."
$APKSIGNER verify --verbose "$SIGNED" 2>&1 | grep "Verified"

SIZE=$(du -h "$SIGNED" | cut -f1)
echo ""
echo "╔══════════════════════════════════════╗"
echo "║  ✅ BUILD SUCCESSFUL                 ║"
echo "╠══════════════════════════════════════╣"
echo "║  APK:  obliterate-bt.apk            ║"
echo "║  Size: $SIZE                          ║"
echo "║  Path:  $SIGNED                       ║"
echo "║                                      ║"
echo "║  Copy to device:                     ║"
echo "║  cp $SIGNED ~/storage/downloads/    ║"
echo "╚══════════════════════════════════════╝"

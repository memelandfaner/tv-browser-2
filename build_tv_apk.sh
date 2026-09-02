#!/usr/bin/env bash
# ==============================================================================
# TV BROWSER 2 — Gradle + Media3 (AndroidX) build
# Signed APK lands at TV-Browser-2.apk and Release/Artifacts/tv-browser-2-release.apk
# ==============================================================================
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="/home/janez/Namizje/Neimenovana mapa/streamN-TV2/android_tv/.tools"
# Keep SDK inside the project. Quote every use — sdkmanager cannot live under a spaced path,
# so we unpack official platform/build-tools zips instead of running sdkmanager.
SDK_DIR="${ANDROID_SDK_ROOT:-$DIR/.android-sdk}"
RELEASE_DIR="$DIR/Release/Artifacts"
WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
PLATFORM_ZIP_URL="https://dl.google.com/android/repository/platform-34-ext7_r03.zip"
BUILD_TOOLS_ZIP_URL="https://dl.google.com/android/repository/build-tools_r34-linux.zip"
WRAPPER_JAR_URL="https://raw.githubusercontent.com/gradle/gradle/v7.6.4/gradle/wrapper/gradle-wrapper.jar"

echo "=========================================================="
echo "GRADIM TV BROWSER 2 (Media3 / Gradle)"
echo "=========================================================="

mkdir -p "$RELEASE_DIR" "$DIR/gradle/wrapper"

if [[ ! -f "$WRAPPER_JAR" ]]; then
    echo "⬇️  gradle-wrapper.jar..."
    curl -fsSL "$WRAPPER_JAR_URL" -o "$WRAPPER_JAR"
fi
chmod +x "$DIR/gradlew"

ensure_sdk() {
    if [[ -f "$SDK_DIR/platforms/android-34/android.jar" && -x "$SDK_DIR/build-tools/34.0.0/aapt2" ]]; then
        return 0
    fi
    echo "⬇️  Android SDK platform 34 + build-tools 34.0.0 (zip, no sdkmanager)..."
    mkdir -p "$SDK_DIR/tmp" "$SDK_DIR/platforms" "$SDK_DIR/build-tools"
    if [[ ! -f "$SDK_DIR/tmp/platform-34.zip" ]]; then
        curl -fL "$PLATFORM_ZIP_URL" -o "$SDK_DIR/tmp/platform-34.zip"
    fi
    if [[ ! -f "$SDK_DIR/tmp/build-tools-34.zip" ]]; then
        curl -fL "$BUILD_TOOLS_ZIP_URL" -o "$SDK_DIR/tmp/build-tools-34.zip"
    fi
    local pdir bdir
    pdir="$SDK_DIR/tmp/platform-unpack"
    bdir="$SDK_DIR/tmp/build-tools-unpack"
    rm -rf "$pdir" "$bdir"
    mkdir -p "$pdir" "$bdir"
    unzip -q "$SDK_DIR/tmp/platform-34.zip" -d "$pdir"
    unzip -q "$SDK_DIR/tmp/build-tools-34.zip" -d "$bdir"
    rm -rf "$SDK_DIR/platforms/android-34"
    if [[ -d "$pdir/android-34" ]]; then
        mv "$pdir/android-34" "$SDK_DIR/platforms/android-34"
    else
        local found
        found="$(find "$pdir" -name android.jar | head -n 1)"
        if [[ -z "$found" ]]; then
            echo "platform zip nima android.jar" >&2
            find "$pdir" | head
            exit 1
        fi
        mkdir -p "$SDK_DIR/platforms/android-34"
        cp -a "$(dirname "$found")/." "$SDK_DIR/platforms/android-34/"
    fi
    if [[ ! -f "$SDK_DIR/platforms/android-34/source.properties" ]]; then
        cat > "$SDK_DIR/platforms/android-34/source.properties" <<'PROP'
Pkg.Desc=Android SDK Platform 34
Pkg.UserSrc=false
Platform.Version=14
Pkg.Revision=3
AndroidVersion.ApiLevel=34
AndroidVersion.IsBaseSdk=true
PROP
    fi
    rm -rf "$SDK_DIR/build-tools/34.0.0"
    if [[ -d "$bdir/android-14" ]]; then
        mv "$bdir/android-14" "$SDK_DIR/build-tools/34.0.0"
    elif [[ -d "$bdir/build-tools/34.0.0" ]]; then
        mv "$bdir/build-tools/34.0.0" "$SDK_DIR/build-tools/34.0.0"
    else
        local aapt
        aapt="$(find "$bdir" -name aapt2 | head -n 1)"
        if [[ -z "$aapt" ]]; then
            echo "build-tools zip nima aapt2" >&2
            find "$bdir" | head
            exit 1
        fi
        mkdir -p "$SDK_DIR/build-tools/34.0.0"
        cp -a "$(dirname "$aapt")/." "$SDK_DIR/build-tools/34.0.0/"
    fi
    chmod +x "$SDK_DIR/build-tools/34.0.0/aapt2" "$SDK_DIR/build-tools/34.0.0/d8" "$SDK_DIR/build-tools/34.0.0/zipalign" 2>/dev/null || true
    if [[ ! -f "$SDK_DIR/build-tools/34.0.0/source.properties" ]]; then
        cat > "$SDK_DIR/build-tools/34.0.0/source.properties" <<'PROP'
Pkg.Desc=Android SDK Build-Tools 34
Pkg.Revision=34.0.0
PROP
    fi
    mkdir -p "$SDK_DIR/licenses"
    echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$SDK_DIR/licenses/android-sdk-license"
    echo "84831b9409646161cbf74d34dc79c6c8ff54aa51" > "$SDK_DIR/licenses/android-sdk-preview-license"
}

ensure_sdk

install_xplore_auth() {
    local dest="$DIR/assets/xplore_auth.js"
    local src="$DIR/xplore_auth.local.js"
    mkdir -p "$DIR/assets"
    if [[ -f "$src" ]]; then
        cp "$src" "$dest"
        echo "Xplore: lokalni samodejni vstop je v TEM APK (ne nalagaj tega APK na GitHub)."
    else
        printf '%s\n' 'window._safeerXploreAuth = null;' > "$dest"
        echo "Xplore: javna prijava, brez samodejnega vstopa."
    fi
}
restore_xplore_auth() {
    printf '%s\n' 'window._safeerXploreAuth = null;' > "$DIR/assets/xplore_auth.js"
}
trap restore_xplore_auth EXIT
install_xplore_auth

# Relative path so spaces in the workspace directory do not break Gradle.
cat > "$DIR/local.properties" <<'EOF'
sdk.dir=.android-sdk
EOF

export ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_HOME="$SDK_DIR"

echo "☕ Gradle assembleRelease (Media3)..."
cd "$DIR"
./gradlew --no-daemon assembleRelease

UNSIGNED="$(find "$DIR/build/outputs/apk" -name '*.apk' | head -n 1)"
if [[ -z "$UNSIGNED" || ! -f "$UNSIGNED" ]]; then
    echo "Gradle ni naredil APK." >&2
    exit 1
fi

echo "✍️  Podpisujem z uber-apk-signer (ista debug identiteta kot prej)..."
rm -rf "$DIR/build/signed"
mkdir -p "$DIR/build/signed"
java -jar "$TOOLS_DIR/uber-apk-signer.jar" \
    --apks "$UNSIGNED" \
    --out "$DIR/build/signed" \
    --allowResign

SIGNED="$(find "$DIR/build/signed" -name '*.apk' | head -n 1)"
if [[ -z "$SIGNED" ]]; then
    echo "Podpisani APK manjka." >&2
    exit 1
fi

FINAL_APK="$RELEASE_DIR/tv-browser-2-release.apk"
cp "$SIGNED" "$FINAL_APK"
cp "$FINAL_APK" "$DIR/TV-Browser-2.apk"

echo "🔎 Preverjam, da je Media3 v dex..."
VERIFY_DIR="$DIR/build/dexcheck"
rm -rf "$VERIFY_DIR"
mkdir -p "$VERIFY_DIR"
unzip -qo "$DIR/TV-Browser-2.apk" "*.dex" -d "$VERIFY_DIR"
if ! grep -a -q "androidx/media3/exoplayer/ExoPlayer" "$VERIFY_DIR"/classes*.dex; then
    echo "NAPAKA: androidx.media3.exoplayer.ExoPlayer ni v APK dex. SurfaceView ne more zaživeti." >&2
    echo "Ne debugiraj Widevine, dokler ta test ne gre skozi." >&2
    ls -la "$VERIFY_DIR"
    exit 1
fi
if ! grep -a -q "androidx/media3/exoplayer/dash/DashMediaSource" "$VERIFY_DIR"/classes*.dex; then
    echo "NAPAKA: media3-exoplayer-dash ni v APK dex." >&2
    exit 1
fi
echo "OK: Media3 ExoPlayer + DashMediaSource sta v dex."

echo ""
echo "=========================================================="
echo "ZGRAJEN SIGNED TV BROWSER 2 APK: $DIR/TV-Browser-2.apk"
echo "=========================================================="
ls -lh "$DIR/TV-Browser-2.apk"

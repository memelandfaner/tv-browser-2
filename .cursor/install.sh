#!/usr/bin/env bash
# ==============================================================================
# Cloud Agent install: provision the TV Browser 2 / Safeer Browser build
# toolchain (Android SDK aapt2 + android.jar, D8/R8, Kotlin compiler,
# uber-apk-signer) into $HOME/android-build-tools so that ./build_tv_apk.sh and
# ./build_mobile_apk.sh work out of the box.
#
# Idempotent and non-interactive: safe to run repeatedly. Existing, already
# provisioned components are skipped.
# ==============================================================================
set -euo pipefail

# --- Pinned versions ---------------------------------------------------------
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
BUILD_TOOLS_VERSION="34.0.0"
PLATFORM_VERSION="android-34"
KOTLIN_VERSION="1.9.24"
UBER_APK_SIGNER_VERSION="1.3.0"

# --- Locations ---------------------------------------------------------------
SDK_ROOT="$HOME/android-sdk"
TOOLS_DIR="$HOME/android-build-tools"
DL_DIR="$HOME/.cache/tv-browser-toolchain"
mkdir -p "$DL_DIR" "$TOOLS_DIR"

log() { echo "==> $*"; }

fetch() {
    # fetch <url> <dest>
    local url="$1" dest="$2"
    if [ -s "$dest" ]; then
        log "cached: $(basename "$dest")"
        return 0
    fi
    log "download: $url"
    curl -fL --retry 4 --retry-delay 4 -o "$dest.part" "$url"
    mv "$dest.part" "$dest"
}

# --- 1. Android SDK command-line tools --------------------------------------
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
    log "Installing Android command-line tools"
    fetch "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}" "$DL_DIR/cmdline-tools.zip"
    rm -rf "$DL_DIR/cmdline-tools-extract"
    unzip -q "$DL_DIR/cmdline-tools.zip" -d "$DL_DIR/cmdline-tools-extract"
    mkdir -p "$SDK_ROOT/cmdline-tools"
    rm -rf "$SDK_ROOT/cmdline-tools/latest"
    mv "$DL_DIR/cmdline-tools-extract/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

# --- 2. SDK packages (build-tools, platform, platform-tools) ----------------
if [ ! -x "$SDK_ROOT/build-tools/${BUILD_TOOLS_VERSION}/aapt2" ] \
    || [ ! -f "$SDK_ROOT/platforms/${PLATFORM_VERSION}/android.jar" ]; then
    log "Installing SDK packages (build-tools ${BUILD_TOOLS_VERSION}, ${PLATFORM_VERSION}, platform-tools)"
    yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 || true
    "$SDKMANAGER" --sdk_root="$SDK_ROOT" \
        "platform-tools" \
        "platforms;${PLATFORM_VERSION}" \
        "build-tools;${BUILD_TOOLS_VERSION}" >/dev/null
fi

# --- 3. Kotlin compiler ------------------------------------------------------
if [ ! -x "$TOOLS_DIR/kotlinc/bin/kotlinc" ]; then
    log "Installing Kotlin compiler ${KOTLIN_VERSION}"
    fetch "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip" \
        "$DL_DIR/kotlin-compiler.zip"
    rm -rf "$DL_DIR/kotlinc" "$TOOLS_DIR/kotlinc"
    unzip -q "$DL_DIR/kotlin-compiler.zip" -d "$DL_DIR"
    mv "$DL_DIR/kotlinc" "$TOOLS_DIR/kotlinc"
    chmod +x "$TOOLS_DIR/kotlinc/bin/"*
fi

# --- 4. uber-apk-signer ------------------------------------------------------
fetch "https://github.com/patrickfav/uber-apk-signer/releases/download/v${UBER_APK_SIGNER_VERSION}/uber-apk-signer-${UBER_APK_SIGNER_VERSION}.jar" \
    "$TOOLS_DIR/uber-apk-signer.jar"

# --- 5. Assemble the layout the build scripts expect ------------------------
log "Assembling toolchain layout in $TOOLS_DIR"
cp -f "$SDK_ROOT/build-tools/${BUILD_TOOLS_VERSION}/aapt2" "$TOOLS_DIR/aapt2"
chmod +x "$TOOLS_DIR/aapt2"
cp -f "$SDK_ROOT/platforms/${PLATFORM_VERSION}/android.jar" "$TOOLS_DIR/android.jar"
# d8.jar contains com.android.tools.r8.D8 (the class the build scripts invoke).
cp -f "$SDK_ROOT/build-tools/${BUILD_TOOLS_VERSION}/lib/d8.jar" "$TOOLS_DIR/r8.jar"

# --- 6. Verify ---------------------------------------------------------------
log "Toolchain ready:"
"$TOOLS_DIR/aapt2" version
"$TOOLS_DIR/kotlinc/bin/kotlinc" -version 2>&1
echo "    android.jar        : $(du -h "$TOOLS_DIR/android.jar" | cut -f1)"
echo "    r8.jar (D8)        : $(du -h "$TOOLS_DIR/r8.jar" | cut -f1)"
echo "    uber-apk-signer.jar: $(du -h "$TOOLS_DIR/uber-apk-signer.jar" | cut -f1)"
echo "    adb                : $SDK_ROOT/platform-tools/adb"
log "Done. Build with ./build_tv_apk.sh or ./build_mobile_apk.sh"

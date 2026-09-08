#!/usr/bin/env bash
# Production APK and Android App Bundle; never embeds private login assets.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export RELEASE_KEYSTORE="${RELEASE_KEYSTORE:-$DIR/../safeer-browser/keystore/safeer-release.jks}"
export RELEASE_KEY_ALIAS="${RELEASE_KEY_ALIAS:-safeer-browser}"
PASS_FILE="${RELEASE_PASS_FILE:-$DIR/../safeer-browser/keystore/.release_pass}"
if [[ -z "${RELEASE_KEY_PASS:-}" && -f "$PASS_FILE" ]]; then
    RELEASE_KEY_PASS="$(cat "$PASS_FILE")"
fi
export RELEASE_KEY_PASS
if [[ ! -f "$RELEASE_KEYSTORE" || -z "${RELEASE_KEY_PASS:-}" ]]; then
    echo 'Set RELEASE_KEYSTORE and RELEASE_KEY_PASS for your production signing key.' >&2
    exit 1
fi
export ANDROID_USER_HOME="$DIR/.android"
cd "$DIR"
python3 tests/check_public_package.py
./gradlew --no-daemon assembleRelease bundleRelease
mkdir -p Release/Store
cp build/outputs/apk/release/*-release.apk Release/Store/Safeer-TV-2.1.82.apk
cp build/outputs/bundle/release/*-release.aab Release/Store/Safeer-TV-2.1.82.aab
python3 tests/check_public_package.py Release/Store/Safeer-TV-2.1.82.apk Release/Store/Safeer-TV-2.1.82.aab
(cd Release/Store && sha256sum Safeer-TV-2.1.82.apk Safeer-TV-2.1.82.aab > SHA256SUMS)
echo 'Production packages: Release/Store/'

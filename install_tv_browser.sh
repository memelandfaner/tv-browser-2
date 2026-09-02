#!/usr/bin/env bash
# 1-klik prenos in namestitev TV Browser 2 (Android / Android TV prek ADB)
set -euo pipefail

APK_URL="https://github.com/memelandfaner/tv-browser-2/raw/main/TV-Browser-2.apk"
TEMP_APK="/tmp/TV-Browser-2.apk"
TV="${1:-192.168.0.77:5555}"

echo "=========================================================="
echo "TV Browser 2 — prenos in namestitev"
echo "=========================================================="

echo "Prenašam APK..."
curl -fL --retry 3 -o "$TEMP_APK" "$APK_URL"

if [[ ! -s "$TEMP_APK" ]]; then
    echo "Prenos APK ni uspel."
    exit 1
fi

echo "APK: $(du -h "$TEMP_APK" | cut -f1)"

if ! command -v adb >/dev/null 2>&1; then
    echo "ADB ni nameščen. APK je v $TEMP_APK — namesti ga ročno na TV."
    exit 0
fi

adb connect "$TV" >/dev/null 2>&1 || true
DEVICE="$(adb devices | awk '/\tdevice$/{print $1; exit}')"
if [[ -z "$DEVICE" ]]; then
    echo "Ni ADB naprave. APK je v $TEMP_APK"
    echo "Primer: adb connect 192.168.0.77:5555 && adb install -r $TEMP_APK"
    exit 0
fi

echo "Nameščam na $DEVICE ..."
adb -s "$DEVICE" install -r "$TEMP_APK"
echo "Končano."

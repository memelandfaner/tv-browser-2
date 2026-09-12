#!/usr/bin/env bash
# ==============================================================================
# 📲 INSTALL SCRIPT: SAFEER MOBILE BROWSER (SAMSUNG GALAXY & ANDROID)
# ==============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="$DIR/Safeer-Browser.apk"

if [ ! -f "$APK" ]; then
    echo "⚠️ APK paket ne obstaja. Najprej gradim..."
    bash "$DIR/build_mobile_apk.sh"
fi

echo "=========================================================="
echo "📲 NAMEŠČAM SAFEER BROWSER NA ANDROID NAPRAVO"
echo "=========================================================="

DEVICE=$(adb devices | grep -v "List" | grep "device$" | head -n 1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo "⚠️ Nobena ADB naprava ni zaznana. Povežite telefon preko USB / Brezžičnega ADB."
    echo "Namestitveni paket se nahaja tukaj: $APK"
    exit 0
fi

echo "📱 Zaznana naprava: $DEVICE"
echo "📲 Nameščam APK..."
adb -s "$DEVICE" install -r "$APK"

echo "🚀 Zaganjam Safeer Browser..."
adb -s "$DEVICE" shell am start -n "si.safeer.tv/.MainActivity"

echo "✅ Uspešno nameščeno in zagnano!"

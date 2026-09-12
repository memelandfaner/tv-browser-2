#!/usr/bin/env bash
# ==============================================================================
# 🛡️ 1-KLIK NAMESTITEV: SAFEER MOBILE BROWSER (SAMSUNG GALAXY & ANDROID)
# ==============================================================================
set -e

APK_URL="https://raw.githubusercontent.com/memelandfaner/-safeer-browser/main/Safeer-Browser.apk"
TEMP_APK="/tmp/Safeer-Browser.apk"

echo "=========================================================="
echo "🛡️ SAFEER BROWSER: 1-KLIK NAMESTITEV"
echo "=========================================================="

echo "📥 Prenašam najnovejšo različico Safeer Browser APK..."
curl -L -s -o "$TEMP_APK" "$APK_URL"

if [ ! -s "$TEMP_APK" ]; then
    echo "❌ Napaka pri prenosu APK paketa."
    exit 1
fi

echo "✅ APK uspešno prenesen ($(du -h "$TEMP_APK" | cut -f1))"

# Preveri prisotnost ADB
if command -v adb >/dev/null 2>&1; then
    DEVICE=$(adb devices | grep -v "List" | grep "device$" | head -n 1 | awk '{print $1}')
    if [ -n "$DEVICE" ]; then
        echo "📱 Zaznana ADB naprava: $DEVICE"
        echo "📲 Nameščam Safeer Browser..."
        adb -s "$DEVICE" install -r "$TEMP_APK"
        echo "🚀 Zaganjam Safeer Browser..."
        adb -s "$DEVICE" shell am start -n "si.safeer.tv/.MainActivity"
        echo "=========================================================="
        echo "🎉 SAFEER BROWSER JE USPEŠNO NAMEŠČEN IN ZAGNAN!"
        echo "=========================================================="
        exit 0
    fi
fi

# Če ni zaznane ADB naprave
cp "$TEMP_APK" "./Safeer-Browser.apk"
echo "ℹ️ APK paket je shranjen v: $(pwd)/Safeer-Browser.apk"
echo "👉 Lahko ga ročno pošljete ali namestite na svoj telefon."

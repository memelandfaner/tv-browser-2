#!/usr/bin/env bash
# Preizkus bralca JSON in usmerjevalnika Safeer Huba v navadnem JVM (Androidovi razredi so v tests/stubs).
#   KOTLINC=/pot/do/kotlinc tests/run_usmerjevalnik_tests.sh
set -euo pipefail
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$TEST_DIR")"
SRC="$PROJECT_DIR/src/main/kotlin"
TOOLS_DIR="${SAFEER_TOOLS_DIR:-$HOME/Namizje/Neimenovana mapa/streamN-TV2/android_tv/.tools}"
KOTLINC="${KOTLINC:-$TOOLS_DIR/kotlinc/bin/kotlinc}"
command -v "$KOTLINC" >/dev/null 2>&1 || KOTLINC="kotlinc"

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

"$KOTLINC" -J-Xmx2g \
    "$TEST_DIR/stubs/Log.kt" \
    "$SRC/si/safeer/tv/cast/HubStreznik.kt" \
    "$SRC/si/safeer/tv/cast/JsonLahki.kt" \
    "$SRC/si/safeer/tv/cast/HubUsmerjevalnik.kt" \
    "$TEST_DIR/UsmerjevalnikTest.kt" \
    -include-runtime -d "$OUT/usmerjevalnik.jar"

java -cp "$OUT/usmerjevalnik.jar" si.safeer.tv.cast.UsmerjevalnikTestKt

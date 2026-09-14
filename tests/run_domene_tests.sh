#!/usr/bin/env bash
# Preizkus strnjenega seznama domen v navadnem JVM.
#   KOTLINC=/pot/do/kotlinc tests/run_domene_tests.sh
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
    "$SRC/si/safeer/tv/DomainSuffixTrie.kt" \
    "$TEST_DIR/DomenTest.kt" \
    -include-runtime -d "$OUT/domene.jar"

java -Xmx1g -cp "$OUT/domene.jar" si.safeer.tv.DomenTestKt

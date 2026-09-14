#!/usr/bin/env bash
# Preizkus blokiranja oglasov v navadnem JVM (Androidovi razredi so v tests/stubs).
#   KOTLINC=/pot/do/kotlinc tests/run_adblock_tests.sh
set -euo pipefail
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$TEST_DIR")"
SRC="$PROJECT_DIR/src/main/kotlin"
BC="$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar"
TOOLS_DIR="${SAFEER_TOOLS_DIR:-$HOME/Namizje/Neimenovana mapa/streamN-TV2/android_tv/.tools}"
KOTLINC="${KOTLINC:-$TOOLS_DIR/kotlinc/bin/kotlinc}"
command -v "$KOTLINC" >/dev/null 2>&1 || KOTLINC="kotlinc"

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

"$KOTLINC" -J-Xmx3g -cp "$BC" "$TEST_DIR"/stubs/*.kt \
    "$SRC/si/safeer/tv/DomainSuffixTrie.kt" \
    "$SRC/si/safeer/tv/ThreatBlockEngine.kt" \
    "$SRC/si/safeer/tv/AdBlockEngine.kt" \
    "$SRC/si/safeer/tv/SignedThreatIntel.kt" \
    "$SRC/com/safeer/threatfeed/FilterListEngine.kt" \
    "$SRC/com/safeer/threatfeed/SignedThreatFeed.kt" \
    "$SRC/com/safeer/threatfeed/BankGuard.kt" \
    "$SRC/com/safeer/threatfeed/BankGuardData.kt" \
    "$SRC/com/safeer/threatfeed/ThreatListAgent.kt" \
    "$TEST_DIR/AdBlockTest.kt" -include-runtime -d "$OUT/adblock.jar"

java -cp "$OUT/adblock.jar:$BC" si.safeer.tv.AdBlockTestKt

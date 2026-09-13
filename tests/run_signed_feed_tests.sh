#!/usr/bin/env bash
# JVM tests for the signed Safeer Threat Intelligence client (JDK 17+).
#   KOTLINC=/path/to/kotlinc tests/run_signed_feed_tests.sh
set -euo pipefail
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$TEST_DIR")"
KOTLINC="${KOTLINC:-kotlinc}"
BC_JAR="$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
(cd "$PROJECT_DIR/libs" && sha256sum -c SHA256SUMS)
"$KOTLINC" -cp "$BC_JAR" "$PROJECT_DIR/src/main/kotlin/com/safeer/threatfeed/SignedThreatFeed.kt" "$TEST_DIR/SignedThreatFeedTest.kt" \
    -include-runtime -d "$OUT/signed-feed.jar"
java -cp "$OUT/signed-feed.jar:$BC_JAR" com.safeer.threatfeed.SignedThreatFeedTestKt "$TEST_DIR/signed-feed-conformance"

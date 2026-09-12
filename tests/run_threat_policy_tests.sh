#!/usr/bin/env bash
# JVM checks for ThreatBlockEngine and the signed feed glue (Android classes stubbed in tests/stubs).
#   KOTLINC=/path/to/kotlinc tests/run_threat_policy_tests.sh
set -euo pipefail
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$TEST_DIR")"
SRC="$PROJECT_DIR/src/main/kotlin"
KOTLINC="${KOTLINC:-kotlinc}"
BC_JAR="$PROJECT_DIR/libs/bcprov-ed25519-1.78.1.jar"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
"$KOTLINC" -cp "$BC_JAR" "$TEST_DIR"/stubs/*.kt "$SRC/com/example/safeerbrowser/DomainSuffixTrie.kt" \
    "$SRC/com/example/safeerbrowser/ThreatBlockEngine.kt" "$SRC/com/example/safeerbrowser/SignedThreatIntel.kt" \
    "$SRC/com/safeer/threatfeed/SignedThreatFeed.kt" "$SRC/com/safeer/threatfeed/BankGuard.kt" \
    "$SRC/com/safeer/threatfeed/BankGuardData.kt" "$SRC/com/safeer/threatfeed/ThreatListAgent.kt" \
    "$TEST_DIR/ThreatPolicyTest.kt" -include-runtime -d "$OUT/threat.jar"
java -cp "$OUT/threat.jar:$BC_JAR" com.example.safeerbrowser.ThreatPolicyTestKt
"$KOTLINC" "$SRC/com/safeer/threatfeed/BankGuard.kt" "$SRC/com/safeer/threatfeed/BankGuardData.kt" "$TEST_DIR/BankGuardTest.kt" \
    -include-runtime -d "$OUT/bank-guard.jar"
java -cp "$OUT/bank-guard.jar" com.safeer.threatfeed.BankGuardTestKt "$TEST_DIR/bank-guard-cases.json"
"$KOTLINC" "$SRC/com/safeer/threatfeed/ThreatListAgent.kt" "$TEST_DIR/ThreatListAgentTest.kt" -include-runtime -d "$OUT/list-agent.jar"
java -cp "$OUT/list-agent.jar" com.safeer.threatfeed.ThreatListAgentTestKt
"$KOTLINC" "$SRC/com/safeer/threatfeed/SponsorBlock.kt" "$SRC/com/safeer/threatfeed/BankGuard.kt" "$SRC/com/safeer/threatfeed/BankGuardData.kt" \
    "$TEST_DIR/SponsorBlockTest.kt" -include-runtime -d "$OUT/sponsorblock.jar"
java -cp "$OUT/sponsorblock.jar" com.safeer.threatfeed.SponsorBlockTestKt

#!/usr/bin/env bash
# Preizkus motorja seznamov brez Androida.
#
# FilterListEngine.kt je namenoma cist JVM (brez uvozov android.*), zato ga lahko
# prevedemo skupaj s preizkusom in pozenemo z navadno Javo. Tako pravila -- kaj se
# blokira in kaj se skrije -- preverimo v nekaj sekundah, brez naprave in brez SDK.
#
#   KOTLINC=/pot/do/kotlinc bash tools/preveri-motor.sh
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLINC="${KOTLINC:-kotlinc}"
IZHOD="$(mktemp -d)"
trap 'rm -rf "$IZHOD"' EXIT

command -v "$KOTLINC" >/dev/null 2>&1 || [ -x "$KOTLINC" ] || {
    echo "NAPAKA: kotlinc ni najden ($KOTLINC)"; exit 1; }

"$KOTLINC" -nowarn -include-runtime -d "$IZHOD/preizkus.jar" \
    "$DIR/src/main/kotlin/com/safeer/threatfeed/FilterListEngine.kt" \
    "$DIR/tests/kotlin/PreizkusMotorjaSeznamov.kt"

java -jar "$IZHOD/preizkus.jar"

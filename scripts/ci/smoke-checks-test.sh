#!/usr/bin/env bash
# Regression test for scripts/smoke-checks.sh (GAS-P3-009): require_output passes only when the command exits 0
# AND prints the fragment; contains matches without a pipeline. Each case runs in a subshell because fail exits.
set -euo pipefail
cd "$(dirname "$0")/../.."
. scripts/smoke-checks.sh

failures=0
expect() { # expect <pass|fail> <description> <command...>
  local want="$1" what="$2"; shift 2
  local got
  if ( "$@" ) >/dev/null 2>&1; then got=pass; else got=fail; fi
  if [ "$got" = "$want" ]; then echo "ok   - $what"; else echo "FAIL - $what (expected $want, got $got)"; failures=$((failures + 1)); fi
}

prints_and_succeeds() { echo "Ingestion volume: ready"; }
prints_and_fails() { echo "Ingestion volume: ready"; return 7; }
silent_success() { echo "nothing relevant"; }
big_output() { head -c 300000 /dev/zero | tr '\0' 'x'; echo "Ingestion"; head -c 300000 /dev/zero | tr '\0' 'y'; }

expect pass "expected text, exit 0"                  require_output "Ingestion" "status" prints_and_succeeds
expect fail "expected text, nonzero exit (GAS-P3-009)" require_output "Ingestion" "status" prints_and_fails
expect fail "exit 0 without the text"                require_output "Ingestion" "status" silent_success
expect pass "large output containing the text (no pipeline race)" require_output "Ingestion" "status" big_output
expect pass "contains: present"                      contains "a Smoke Test Archive page" "Smoke Test Archive"
expect fail "contains: absent"                       contains "another page" "Smoke Test Archive"

[ "$failures" = "0" ] || { echo "$failures smoke-check case(s) failed"; exit 1; }
echo "smoke-checks: all cases passed"

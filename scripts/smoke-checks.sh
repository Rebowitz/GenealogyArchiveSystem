# Shared checks for scripts/smoke-test.sh (sourced, not run). Kept separate so scripts/ci/smoke-checks-test.sh can
# exercise exactly the functions the smoke test uses.

fail() { echo "::error::smoke test: $*"; exit 1; }

# True if the text contains the fragment. Matching in the shell, not "producer | grep -q": under pipefail grep -q
# exits at the first match and the still-writing producer then fails the pipeline, a timing-dependent false failure.
contains() { case "$1" in *"$2"*) return 0;; *) return 1;; esac; }

# require_output <fragment> <message> <command...>
# Runs the command once, requires it to exit 0 AND its output to contain the fragment (GAS-P3-009: a producer that
# prints the expected text but fails must fail the check, as the original pipefail pipeline did).
require_output() {
  local fragment="$1" message="$2" out code
  shift 2
  out="$("$@")" && code=0 || code=$?
  [ "$code" = "0" ] || fail "$message (command exited $code)"
  contains "$out" "$fragment" || fail "$message"
}

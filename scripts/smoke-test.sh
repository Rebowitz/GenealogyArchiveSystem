#!/usr/bin/env bash
# Packaged-application smoke test (Linux): drives the built jar exactly as an administrator would -
# check-config, status, migrate (twice), serve with readiness polling, worker start/stop - using a
# Storage Volume root given as a native host path. Requires:
#   target/genealogy-archive.jar      (./mvnw package or verify)
#   GAS_SMOKE_DB_URL                  JDBC URL of an EMPTY database
#   GAS_TEST_DB_USER, GAS_TEST_DB_PASSWORD
set -euo pipefail
cd "$(dirname "$0")/.."

JAR="target/genealogy-archive.jar"
PORT="${GAS_SMOKE_PORT:-18080}"
[ -f "$JAR" ] || { echo "Build first: $JAR not found" >&2; exit 2; }
: "${GAS_SMOKE_DB_URL:?}" "${GAS_TEST_DB_USER:?}" "${GAS_TEST_DB_PASSWORD:?}"

WORK="$(mktemp -d)"
mkdir -p "$WORK/logs" "$WORK/volume-primary"
cat > "$WORK/installation.yml" <<EOF
database:
  url: '${GAS_SMOKE_DB_URL}'
  username: '${GAS_TEST_DB_USER}'
server:
  bindAddress: 127.0.0.1
  port: ${PORT}
logging:
  format: json
  directory: '${WORK}/logs'
storageRoots:
  '0192f0c4-1111-7000-8000-000000000001': '${WORK}/volume-primary'
EOF
export GAS_DB_PASSWORD="$GAS_TEST_DB_PASSWORD"
CONFIG="--config=$WORK/installation.yml"

fail() { echo "::error::smoke test: $*"; exit 1; }
expect_exit() {
  local want="$1"; shift
  set +e; java -jar "$JAR" "$@" "$CONFIG"; local got=$?; set -e
  [ "$got" = "$want" ] || fail "'$*' exited $got, expected $want"
  echo "ok: '$*' exited $got"
}

expect_exit 0 check-config
expect_exit 3 status          # not yet migrated: visibly not ready
expect_exit 0 migrate
expect_exit 0 migrate         # repeat is a no-op
expect_exit 0 status

java -jar "$JAR" serve "$CONFIG" &
WEB=$!
code=000
for _ in $(seq 1 90); do
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/actuator/health/readiness" || true)"
  [ "$code" = "200" ] && break
  kill -0 "$WEB" 2>/dev/null || fail "web process exited during startup"
  sleep 1
done
[ "$code" = "200" ] || fail "web application not ready (last HTTP status $code)"
curl -fsS "http://127.0.0.1:${PORT}/" | grep -q "Archive instance is ready" || fail "home page does not report ready"
kill -TERM "$WEB"
wait "$WEB" || true
echo "ok: web application started, ready, stopped"

java -jar "$JAR" worker "$CONFIG" &
WORKER=$!
sleep 10
kill -0 "$WORKER" 2>/dev/null || fail "worker exited unexpectedly"
kill -TERM "$WORKER"
wait "$WORKER" || true
echo "ok: worker started and stopped"

grep -q '"log.level"' "$WORK/logs/genealogy-archive-web.log" || fail "structured JSON web log not written"
grep -q 'Worker stopped' "$WORK/logs/genealogy-archive-worker.log" || fail "worker did not log clean shutdown"
if grep -rq -- "$GAS_TEST_DB_PASSWORD" "$WORK/logs"; then fail "database password found in logs"; fi
echo "ok: structured logs written, no secrets in logs"
echo "SMOKE TEST PASSED"

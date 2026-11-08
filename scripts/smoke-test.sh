#!/usr/bin/env bash
# Packaged-application smoke test (Linux): drives the built jar exactly as an administrator would -
# check-config, status, migrate (twice), serve with readiness polling, worker start/stop - using a
# Storage Volume root given as a native host path. Phase 2: registers and initializes the volume,
# uploads a file through the browser upload API, lets the worker archive it, and downloads it again
# byte for byte. Requires:
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

WEB=""
WORKER=""
fail() { echo "::error::smoke test: $*"; exit 1; }
# A failed check must not leave the web or worker process holding the step's output open (it would hang the job).
stop_background() {
  for p in $WEB $WORKER; do kill -TERM "$p" 2>/dev/null || true; done
}
trap stop_background EXIT
# True if the text contains the fragment. Matching in the shell, not "producer | grep -q": under pipefail grep -q
# exits at the first match and the still-writing producer then fails the pipeline, a timing-dependent false failure.
contains() { case "$1" in *"$2"*) return 0;; *) return 1;; esac; }
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
expect_exit 3 status          # Phase 1: migrated but the archive is not initialized yet
expect_exit 0 init-archive --name="Smoke Test Archive"
expect_exit 5 init-archive --name="Second Archive"   # an Archive ID is created only once
ADMIN_PW="smoke-$(openssl rand -hex 12)"
export GAS_NEW_ADMIN_PASSWORD="$ADMIN_PW"
expect_exit 0 create-admin --username=smoke-admin
unset GAS_NEW_ADMIN_PASSWORD
expect_exit 0 status

# ---- Phase 2: Storage Volume registration and initialization (P2-C, P2-D) ----------------------
VOLUME_ID="$(java -jar "$JAR" register-volume --name=Primary --purpose=primary "$CONFIG" | sed -n 's/^ *Volume ID *: *//p')"
[ -n "$VOLUME_ID" ] || fail "register-volume printed no Volume ID"
mkdir -p "$WORK/staging" "$WORK/cache"
chmod 0700 "$WORK/staging"
cat > "$WORK/installation.yml" <<YAML
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
  '${VOLUME_ID}': '${WORK}/volume-primary'
files:
  stagingDirectory: '${WORK}/staging'
  cacheDirectory: '${WORK}/cache'
YAML
expect_exit 0 check-config
expect_exit 0 init-volume --volume-id="$VOLUME_ID"
expect_exit 5 init-volume --volume-id="$VOLUME_ID"     # a marker is never written twice
[ -f "$WORK/volume-primary/.genealogy-archive-volume" ] || fail "volume marker not written"
expect_exit 0 set-ingestion-volume --volume-id="$VOLUME_ID"
contains "$(java -jar "$JAR" status "$CONFIG")" "Ingestion" || fail "status does not report the ingestion volume"

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
home_page="$(curl -fsS "http://127.0.0.1:${PORT}/")" || fail "home page not served"
contains "$home_page" "Archive instance is ready" || fail "home page does not report ready"
# Phase 1: sign in through the real form (CSRF token from the page) and read an archive page.
COOKIES="$WORK/cookies.txt"
login_page="$(curl -fsS -c "$COOKIES" -b "$COOKIES" "http://127.0.0.1:${PORT}/login")"
token="$(printf '%s' "$login_page" | sed -n 's/.*name="_csrf" value="\([^"]*\)".*/\1/p' | head -1)"
[ -n "$token" ] || fail "no CSRF token on the sign-in page"
location="$(curl -s -o /dev/null -w '%{redirect_url}' -c "$COOKIES" -b "$COOKIES" \
  --data-urlencode "_csrf=$token" --data-urlencode "username=smoke-admin" --data-urlencode "password=$ADMIN_PW" \
  "http://127.0.0.1:${PORT}/login")"
case "$location" in *error*|"") fail "sign-in failed (redirect: $location)";; esac
archive_page="$(curl -fsS -b "$COOKIES" "http://127.0.0.1:${PORT}/archive")" || fail "signed-in archive page not served"
contains "$archive_page" "Smoke Test Archive" || fail "signed-in archive page not shown"
[ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/archive")" = "302" ] || fail "archive page reachable without sign-in"
echo "ok: local sign-in works and protects archive pages"

# ---- Phase 2: browser upload -> worker -> verified, protected original -> download --------------
java -jar "$JAR" worker "$CONFIG" &
WORKER=$!
head -c 3145731 /dev/urandom > "$WORK/scan.bin"
upload_page="$(curl -fsS -b "$COOKIES" -c "$COOKIES" "http://127.0.0.1:${PORT}/files/upload")"
contains "$upload_page" "Uploads are stored on volume" || fail "upload page does not report readiness"
token="$(printf '%s' "$upload_page" | sed -n 's/.*name="_csrf" value="\([^"]*\)".*/\1/p' | head -1)"
batch_location="$(curl -s -o /dev/null -w '%{redirect_url}' -b "$COOKIES" -c "$COOKIES" \
  --data-urlencode "_csrf=$token" --data-urlencode "note=smoke test" "http://127.0.0.1:${PORT}/files/batches")"
BATCH="$(printf '%s' "$batch_location" | sed -n 's|.*/files/batches/\([0-9a-f-]\{36\}\).*|\1|p')"
[ -n "$BATCH" ] || fail "no upload batch created (redirect: $batch_location)"
put="$(curl -sS -b "$COOKIES" -X PUT -H "X-CSRF-TOKEN: $token" -H "Content-Type: application/octet-stream" \
  -H "X-File-Name: smoke%20scan%201.tif" -H "X-File-Last-Modified: 1577934245000" \
  --data-binary @"$WORK/scan.bin" "http://127.0.0.1:${PORT}/files/batches/${BATCH}/items/smoke-1")"
OP="$(printf '%s' "$put" | sed -n 's/.*"operationId":"\([0-9a-f-]*\)".*/\1/p')"
[ -n "$OP" ] || fail "upload not accepted: $put"
FILE_ID=""
st=""
for _ in $(seq 1 60); do
  st="$(curl -fsS -b "$COOKIES" "http://127.0.0.1:${PORT}/files/operations/${OP}/status")"
  if contains "$st" '"archived":true'; then
    FILE_ID="$(printf '%s' "$st" | sed -n 's/.*"fileId":"\([0-9a-f-]*\)".*/\1/p')"
    break
  fi
  sleep 1
done
[ -n "$FILE_ID" ] || fail "upload was not archived by the worker (last status: $st)"
curl -fsS -b "$COOKIES" -o "$WORK/downloaded.bin" "http://127.0.0.1:${PORT}/files/${FILE_ID}/download"
cmp -s "$WORK/scan.bin" "$WORK/downloaded.bin" || fail "downloaded original differs from the uploaded bytes"
stored="$(find "$WORK/volume-primary/originals" -type f | head -1)"
{ [ -n "$stored" ] && [ ! -w "$stored" ]; } || fail "stored original is not read-only"
case "$stored" in *smoke*) fail "stored filename derived from the uploaded name";; esac
for _ in $(seq 1 20); do [ -z "$(ls -A "$WORK/staging")" ] && break; sleep 1; done
[ -z "$(ls -A "$WORK/staging")" ] || fail "staging not cleaned after archiving"
kill -TERM "$WORKER"
wait "$WORKER" || true
echo "ok: browser upload archived by the worker, protected, downloaded byte for byte"
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

grep -q '"ecs":{"version"' "$WORK/logs/genealogy-archive-web.log" || fail "structured JSON web log not written"
grep -q 'Worker stopped' "$WORK/logs/genealogy-archive-worker.log" || fail "worker did not log clean shutdown"
if grep -rq -- "$GAS_TEST_DB_PASSWORD" "$WORK/logs"; then fail "database password found in logs"; fi
if grep -rq -- "$ADMIN_PW" "$WORK/logs"; then fail "administrator password found in logs"; fi
echo "ok: structured logs written, no secrets in logs"
echo "SMOKE TEST PASSED"

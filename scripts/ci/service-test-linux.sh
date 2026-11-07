#!/usr/bin/env bash
# CI only (run as root with the CI environment preserved: sudo -E): installs the real systemd units
# with deploy/linux/install.sh, runs the web application and worker under systemd as the dedicated
# service account, checks readiness, stops them and verifies a clean (graceful) stop. Phase 2: registers
# and initializes a Storage Volume, then with both services running proves from inside the sandboxed
# web service that it cannot write archival originals, uploads a file through the browser API, waits
# for the worker to archive it and downloads it byte for byte. Uninstalls.
set -euo pipefail
cd "$(dirname "$0")/../.."

: "${JAVA_HOME:?}" "${GAS_PG_BIN:?}" "${GAS_PG_PORT:?}" "${GAS_TEST_DB_USER:?}" "${GAS_TEST_DB_PASSWORD:?}"
JAVA="$JAVA_HOME/bin/java"
ETC=/etc/genealogy-archive
PORT=18081
fail() { echo "::error::systemd service test: $*"; journalctl -u genealogy-archive-web -u genealogy-archive-worker --no-pager -n 80 || true; exit 1; }

bash deploy/linux/install.sh target/genealogy-archive.jar "$JAVA"
sudo -u genealogy-archive "$JAVA" -version >/dev/null 2>&1 || fail "service account cannot execute $JAVA"

PGPASSWORD="$GAS_TEST_DB_PASSWORD" "$GAS_PG_BIN/psql" -h localhost -p "$GAS_PG_PORT" -U "$GAS_TEST_DB_USER" -d postgres \
  -v ON_ERROR_STOP=1 -c "CREATE DATABASE gas_service ENCODING 'UTF8' TEMPLATE template0"

printf '%s\n' "$GAS_TEST_DB_PASSWORD" > "$ETC/db-password"
chown root:genealogy-archive "$ETC/db-password"
chmod 0640 "$ETC/db-password"
VOLUME=/srv/genealogy-archive-ci/primary
install -d -m 0750 -o genealogy-archive -g genealogy-archive "$VOLUME"
cat > "$ETC/installation.yml" <<EOF
database:
  url: 'jdbc:postgresql://localhost:${GAS_PG_PORT}/gas_service'
  username: '${GAS_TEST_DB_USER}'
  passwordFile: '${ETC}/db-password'
server:
  bindAddress: 127.0.0.1
  port: ${PORT}
logging:
  format: json
  directory: '/var/log/genealogy-archive'
storageRoots:
  '0192f0c4-1111-7000-8000-000000000001': '${VOLUME}'
EOF
chown root:genealogy-archive "$ETC/installation.yml"
chmod 0640 "$ETC/installation.yml"

sudo -u genealogy-archive "$JAVA" -jar /opt/genealogy-archive/genealogy-archive.jar check-config --config="$ETC/installation.yml" \
  || fail "check-config as service account"
sudo -u genealogy-archive "$JAVA" -jar /opt/genealogy-archive/genealogy-archive.jar migrate --config="$ETC/installation.yml" \
  || fail "migrate as service account"
# Phase 1: the archive is created explicitly (never as a migration side effect); the worker and
# readiness require it. The administrator password is passed through the environment, never argv.
sudo -u genealogy-archive "$JAVA" -jar /opt/genealogy-archive/genealogy-archive.jar init-archive --name="Service Test Archive" \
  --config="$ETC/installation.yml" || fail "init-archive as service account"
GAS_NEW_ADMIN_PASSWORD="svc-$(openssl rand -hex 12)"
export GAS_NEW_ADMIN_PASSWORD
sudo --preserve-env=GAS_NEW_ADMIN_PASSWORD -u genealogy-archive "$JAVA" -jar /opt/genealogy-archive/genealogy-archive.jar \
  create-admin --username=service-admin --config="$ETC/installation.yml" || fail "create-admin as service account"
SVC_ADMIN_PW="$GAS_NEW_ADMIN_PASSWORD"
unset GAS_NEW_ADMIN_PASSWORD

# Phase 2: register and initialize the volume as the service account (P2-C, P2-D).
VOLUME_ID="$(sudo -u genealogy-archive "$JAVA" -jar /opt/genealogy-archive/genealogy-archive.jar register-volume \
  --name=Primary --purpose=primary --config="$ETC/installation.yml" | sed -n 's/^ *Volume ID *: *//p')"
[ -n "$VOLUME_ID" ] || fail "register-volume printed no Volume ID"
sed -i "s|'0192f0c4-1111-7000-8000-000000000001'|'${VOLUME_ID}'|" "$ETC/installation.yml"
cat >> "$ETC/installation.yml" <<YAML
files:
  stagingDirectory: '/var/lib/genealogy-archive/staging'
  cacheDirectory: '/var/lib/genealogy-archive/cache'
YAML
for cmd in "init-volume --volume-id=$VOLUME_ID" "set-ingestion-volume --volume-id=$VOLUME_ID"; do
  # shellcheck disable=SC2086
  sudo -u genealogy-archive "$JAVA" -jar /opt/genealogy-archive/genealogy-archive.jar $cmd --config="$ETC/installation.yml" \
    || fail "$cmd as service account"
done
[ "$(systemctl show -p User --value genealogy-archive-web)" = genealogy-archive ] || fail "web unit does not run as the service account"
[ "$(systemctl show -p ProtectSystem --value genealogy-archive-web)" = strict ] || fail "web unit is not ProtectSystem=strict"
[ "$(systemctl show -p ProtectSystem --value genealogy-archive-worker)" = full ] || fail "worker unit ProtectSystem changed"

systemctl start genealogy-archive-web
code=000
for _ in $(seq 1 90); do
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/actuator/health/readiness" || true)"
  [ "$code" = 200 ] && break
  systemctl is-failed --quiet genealogy-archive-web && fail "web service failed during startup"
  sleep 1
done
[ "$code" = 200 ] || fail "web service not ready (HTTP $code)"
systemctl stop genealogy-archive-web
[ "$(systemctl show -p Result --value genealogy-archive-web)" = success ] || fail "web service did not stop cleanly"
grep -q 'graceful shutdown' /var/log/genealogy-archive/genealogy-archive-web.log || fail "web service stop was not graceful"
echo "ok: systemd web service started, ready, stopped gracefully"

systemctl start genealogy-archive-worker
sleep 10
systemctl is-active --quiet genealogy-archive-worker || fail "worker service not active"
systemctl stop genealogy-archive-worker
[ "$(systemctl show -p Result --value genealogy-archive-worker)" = success ] || fail "worker service did not stop cleanly"
grep -q 'Worker stopped' /var/log/genealogy-archive/genealogy-archive-worker.log || fail "worker stop was not graceful"
grep -q 'started for archive' /var/log/genealogy-archive/genealogy-archive-worker.log || fail "worker did not start for the archive"
if grep -rq -- "$SVC_ADMIN_PW" /var/log/genealogy-archive; then fail "administrator password found in service logs"; fi
echo "ok: systemd worker service started and stopped gracefully"

# ---- Phase 2 under the real units --------------------------------------------------------------
systemctl start genealogy-archive-worker genealogy-archive-web
code=000
for _ in $(seq 1 90); do
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/actuator/health/readiness" || true)"
  [ "$code" = 200 ] && break
  sleep 1
done
[ "$code" = 200 ] || fail "web service not ready for Phase 2 checks (HTTP $code)"
JAR_COOKIES="$(mktemp)"
base="http://127.0.0.1:${PORT}"
tok() { sed -n 's/.*name="_csrf" value="\([^"]*\)".*/\1/p' | head -1; }
t="$(curl -fsS -c "$JAR_COOKIES" -b "$JAR_COOKIES" "$base/login" | tok)"
loc="$(curl -s -o /dev/null -w '%{redirect_url}' -c "$JAR_COOKIES" -b "$JAR_COOKIES" --data-urlencode "_csrf=$t" \
  --data-urlencode "username=service-admin" --data-urlencode "password=$SVC_ADMIN_PW" "$base/login")"
case "$loc" in *error*|"") fail "sign-in failed";; esac
ops="$(curl -fsS -b "$JAR_COOKIES" "$base/operations")"
printf '%s' "$ops" | grep -q 'Web service write access to archival originals: denied' \
  || { printf '%s' "$ops" | grep -o '<li>[^<]*</li>' | head -5; fail "web service could write (or could not confirm) archival originals"; }
echo "ok: sandboxed web service is denied write access to the archival volume (requirement 5)"
page="$(curl -fsS -b "$JAR_COOKIES" -c "$JAR_COOKIES" "$base/files/upload")"
t="$(printf '%s' "$page" | tok)"
loc="$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR_COOKIES" -c "$JAR_COOKIES" --data-urlencode "_csrf=$t" "$base/files/batches")"
BATCH="$(printf '%s' "$loc" | sed -n 's|.*/files/batches/\([0-9a-f-]\{36\}\).*|\1|p')"
[ -n "$BATCH" ] || fail "no upload batch (redirect: $loc)"
head -c 2097155 /dev/urandom > /tmp/gas-svc-scan.bin
put="$(curl -sS -b "$JAR_COOKIES" -X PUT -H "X-CSRF-TOKEN: $t" -H "Content-Type: application/octet-stream" \
  -H "X-File-Name: service%20scan.tif" --data-binary @/tmp/gas-svc-scan.bin "$base/files/batches/$BATCH/items/svc-1")"
OP="$(printf '%s' "$put" | sed -n 's/.*"operationId":"\([0-9a-f-]*\)".*/\1/p')"
[ -n "$OP" ] || fail "upload not accepted: $put"
FILE_ID=""
for _ in $(seq 1 60); do
  st="$(curl -fsS -b "$JAR_COOKIES" "$base/files/operations/$OP/status")"
  if printf '%s' "$st" | grep -q '"archived":true'; then
    FILE_ID="$(printf '%s' "$st" | sed -n 's/.*"fileId":"\([0-9a-f-]*\)".*/\1/p')"; break
  fi
  sleep 1
done
[ -n "$FILE_ID" ] || fail "upload not archived by the worker service (last status: $st)"
curl -fsS -b "$JAR_COOKIES" -o /tmp/gas-svc-down.bin "$base/files/$FILE_ID/download"
cmp -s /tmp/gas-svc-scan.bin /tmp/gas-svc-down.bin || fail "downloaded original differs"
stored="$(find "$VOLUME/originals" -type f | head -1)"
[ "$(stat -c '%U %a' "$stored")" = "genealogy-archive 440" ] || fail "stored original is not owner-read-only ($(stat -c '%U %a' "$stored"))"
echo "ok: browser upload archived by the worker service, protected and downloaded byte for byte"
systemctl stop genealogy-archive-web genealogy-archive-worker
rm -f /tmp/gas-svc-scan.bin /tmp/gas-svc-down.bin "$JAR_COOKIES"

bash deploy/linux/uninstall.sh
echo "SYSTEMD SERVICE TEST PASSED"

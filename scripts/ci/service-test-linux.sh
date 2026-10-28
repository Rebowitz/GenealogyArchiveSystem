#!/usr/bin/env bash
# CI only (run as root with the CI environment preserved: sudo -E): installs the real systemd units
# with deploy/linux/install.sh, runs the web application and worker under systemd as the dedicated
# service account, checks readiness, stops them and verifies a clean (graceful) stop. Uninstalls.
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

bash deploy/linux/uninstall.sh
echo "SYSTEMD SERVICE TEST PASSED"

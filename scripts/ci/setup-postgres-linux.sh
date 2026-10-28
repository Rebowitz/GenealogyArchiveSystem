#!/usr/bin/env bash
# CI only: installs the requested PostgreSQL major version from the PostgreSQL Global Development
# Group (PGDG) apt repository and starts a private, password-protected cluster for the test run.
# Exports GAS_TEST_DB_URL / GAS_TEST_DB_USER / GAS_TEST_DB_PASSWORD and GAS_SMOKE_DB_URL to later
# steps. The password is random per run and masked in the log.
set -euo pipefail

VERSION="${1:?usage: setup-postgres-linux.sh <major-version>}"
PORT=5499
WORK="${RUNNER_TEMP:-/tmp}/gas-postgres"
mkdir -p "$WORK"

sudo apt-get update -q
sudo apt-get install -y -q postgresql-common
sudo /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y
sudo apt-get install -y -q "postgresql-${VERSION}" "postgresql-client-${VERSION}"

BIN="/usr/lib/postgresql/${VERSION}/bin"
PASSWORD="$(openssl rand -hex 24)"
echo "::add-mask::${PASSWORD}"
printf '%s' "$PASSWORD" > "$WORK/pwfile"
chmod 600 "$WORK/pwfile"

"$BIN/initdb" -D "$WORK/data" -U gas_ci --auth=scram-sha-256 --pwfile="$WORK/pwfile" -E UTF8 --locale=C.UTF-8 >/dev/null
rm -f "$WORK/pwfile"
"$BIN/pg_ctl" -D "$WORK/data" -o "-p ${PORT} -k ${WORK} -c listen_addresses=localhost" -l "$WORK/postgres.log" -w start

export PGPASSWORD="$PASSWORD"
"$BIN/psql" -h localhost -p "$PORT" -U gas_ci -d postgres -v ON_ERROR_STOP=1 -tAc "SELECT version()"
"$BIN/psql" -h localhost -p "$PORT" -U gas_ci -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE gas_smoke ENCODING 'UTF8' TEMPLATE template0"

ACTUAL="$("$BIN/psql" -h localhost -p "$PORT" -U gas_ci -d postgres -tAc "SHOW server_version_num")"
if [ "$((ACTUAL / 10000))" != "$VERSION" ]; then
  echo "::error::Expected PostgreSQL ${VERSION}, server reports ${ACTUAL}"
  exit 1
fi

{
  echo "GAS_TEST_DB_URL=jdbc:postgresql://localhost:${PORT}/postgres"
  echo "GAS_TEST_DB_USER=gas_ci"
  echo "GAS_TEST_DB_PASSWORD=${PASSWORD}"
  echo "GAS_SMOKE_DB_URL=jdbc:postgresql://localhost:${PORT}/gas_smoke"
  echo "GAS_PG_BIN=${BIN}"
  echo "GAS_PG_PORT=${PORT}"
} >> "$GITHUB_ENV"
echo "PostgreSQL ${VERSION} ready on port ${PORT}"

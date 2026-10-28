#!/usr/bin/env bash
# Developer convenience (Linux): creates the local development role and database.
# Run as a PostgreSQL superuser, for example:  sudo -u postgres scripts/setup-dev-db.sh
# You will be prompted for the new role's password; nothing is written to disk.
set -euo pipefail
ROLE="${GAS_DEV_ROLE:-genealogy_archive}"
DB="${GAS_DEV_DB:-genealogy_archive}"
read -r -s -p "Password for new PostgreSQL role '$ROLE': " PW; echo
psql -v ON_ERROR_STOP=1 -v role="$ROLE" -v db="$DB" -v pw="$PW" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'pw')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role') \gexec
SELECT format('CREATE DATABASE %I OWNER %I ENCODING ''UTF8'' TEMPLATE template0', :'db', :'role')
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db') \gexec
SQL
echo "Role '$ROLE' and database '$DB' are ready. For integration tests also grant CREATEDB:"
echo "  psql -c 'ALTER ROLE $ROLE CREATEDB'"

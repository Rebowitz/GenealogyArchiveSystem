#!/usr/bin/env bash
# Runs the Fast Development Suite (unit tests) and the Phase-Gate Suite (PostgreSQL integration
# tests). Integration tests need a PostgreSQL server you can create throwaway databases on:
#   export GAS_TEST_DB_URL=jdbc:postgresql://localhost:5432/postgres
#   export GAS_TEST_DB_USER=<role with CREATEDB>
#   export GAS_TEST_DB_PASSWORD=<password>
# Unit tests only (no database):  ./mvnw test
set -euo pipefail
cd "$(dirname "$0")/.."

missing=0
for v in GAS_TEST_DB_URL GAS_TEST_DB_USER GAS_TEST_DB_PASSWORD; do
  if [ -z "${!v:-}" ]; then
    echo "Missing environment variable $v (see README 'Running the tests')." >&2
    missing=1
  fi
done
[ "$missing" -eq 0 ] || exit 2

exec ./mvnw -B -ntp clean verify "$@"

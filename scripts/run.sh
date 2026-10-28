#!/usr/bin/env bash
# Builds (if needed) and runs a Genealogy Archive System command against an installation file.
#   scripts/run.sh check-config --config=./installation.yml
#   scripts/run.sh migrate      --config=./installation.yml
#   scripts/run.sh serve        --config=./installation.yml
#   scripts/run.sh worker       --config=./installation.yml
# The database password comes from GAS_DB_PASSWORD (or database.passwordFile).
set -euo pipefail
cd "$(dirname "$0")/.."
JAR=target/genealogy-archive.jar
if [ ! -f "$JAR" ] || [ -n "$(find src pom.xml -newer "$JAR" -print -quit)" ]; then
  ./mvnw -B -ntp -q package -DskipTests
fi
exec java -jar "$JAR" "$@"

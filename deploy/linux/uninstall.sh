#!/usr/bin/env bash
# Stops and removes the Genealogy Archive System systemd services. Run as root.
# Leaves /etc/genealogy-archive (configuration), logs, the database and all Storage Volumes untouched.
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "Run as root (sudo)." >&2; exit 1; }
for unit in web worker; do
  systemctl disable --now "genealogy-archive-${unit}" 2>/dev/null || true
  rm -f "/etc/systemd/system/genealogy-archive-${unit}.service"
done
systemctl daemon-reload
echo "Services removed. Configuration, logs, database and Storage Volumes were not touched."

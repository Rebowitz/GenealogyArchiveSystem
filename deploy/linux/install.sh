#!/usr/bin/env bash
# Installs or updates the Genealogy Archive System as systemd services on Linux. Run as root.
#
#   sudo deploy/linux/install.sh target/genealogy-archive.jar [/path/to/java]
#
# Creates the 'genealogy-archive' system account, /opt/genealogy-archive (application),
# /etc/genealogy-archive (installation configuration, root:genealogy-archive 0750) and
# /var/log/genealogy-archive. It never overwrites an existing installation.yml and never stores a
# password. Services are installed but not enabled or started: configure, migrate, then enable.
set -euo pipefail

JAR="${1:?usage: install.sh <genealogy-archive.jar> [java-executable]}"
JAVA="${2:-$(command -v java || true)}"
HERE="$(cd "$(dirname "$0")" && pwd)"
APP_USER=genealogy-archive
APP_DIR=/opt/genealogy-archive
ETC_DIR=/etc/genealogy-archive
LOG_DIR=/var/log/genealogy-archive

[ "$(id -u)" = 0 ] || { echo "Run as root (sudo)." >&2; exit 1; }
[ -f "$JAR" ] || { echo "Jar not found: $JAR" >&2; exit 1; }
[ -n "$JAVA" ] && [ -x "$JAVA" ] || { echo "Java 21+ not found; pass its path as the second argument." >&2; exit 1; }

if ! id -u "$APP_USER" >/dev/null 2>&1; then
  useradd --system --home-dir /var/lib/genealogy-archive --create-home --shell /usr/sbin/nologin "$APP_USER"
fi

install -d -m 0755 "$APP_DIR"
install -m 0644 "$JAR" "$APP_DIR/genealogy-archive.jar"
install -d -m 0750 -o root -g "$APP_USER" "$ETC_DIR"
install -d -m 0750 -o "$APP_USER" -g "$APP_USER" "$LOG_DIR"

if [ ! -f "$ETC_DIR/installation.yml" ]; then
  install -m 0640 -o root -g "$APP_USER" "$HERE/../../config/installation.example.yml" "$ETC_DIR/installation.yml"
  echo "Created $ETC_DIR/installation.yml from the example - edit it before starting services."
fi

for unit in web worker; do
  sed -e "s|@JAVA@|${JAVA}|g" -e "s|@APP_DIR@|${APP_DIR}|g" \
    "$HERE/genealogy-archive-${unit}.service" > "/etc/systemd/system/genealogy-archive-${unit}.service"
  chmod 0644 "/etc/systemd/system/genealogy-archive-${unit}.service"
done
systemctl daemon-reload
systemd-analyze verify /etc/systemd/system/genealogy-archive-web.service /etc/systemd/system/genealogy-archive-worker.service

cat <<EOF

Installed. Next steps:
  1. Edit $ETC_DIR/installation.yml (database, storageRoots, logging.directory: $LOG_DIR).
  2. Put the database password in $ETC_DIR/db-password (one line) and protect it:
       chown root:$APP_USER $ETC_DIR/db-password && chmod 0640 $ETC_DIR/db-password
     and set database.passwordFile: $ETC_DIR/db-password
  3. Validate and migrate as the service account:
       sudo -u $APP_USER $JAVA -jar $APP_DIR/genealogy-archive.jar check-config --config=$ETC_DIR/installation.yml
       sudo -u $APP_USER $JAVA -jar $APP_DIR/genealogy-archive.jar migrate --config=$ETC_DIR/installation.yml
  4. Enable and start:
       systemctl enable --now genealogy-archive-web genealogy-archive-worker
EOF

#!/usr/bin/env bash
# Install a release tarball on the production host.
#
# Nothing has to be pre-installed on the server: pipe this script over SSH so
# the version that runs always matches the checkout that built the tarball.
#
#   ssh user@host 'sudo bash -s -- /tmp/daily-english-<rev>.tar.gz' \
#     < deploy/remote-deploy.sh
#
# What it does: unpack, back up the live web root and binary, swap both in,
# restart the service, health-check it, and roll everything back if the check
# fails. The SQLite data directory is never touched.
#
# Requires: root (NOPASSWD sudo for the deploy user), tar, rsync, curl,
# systemctl. Override any path with an environment variable, e.g.
#   sudo WEB_ROOT=/var/www/other bash -s -- /tmp/release.tar.gz

set -Eeuo pipefail

TARBALL=${1:-}
WEB_ROOT=${WEB_ROOT:-/var/www/daily-english/web}
APP_DIR=${APP_DIR:-/opt/daily-english-go}
SERVICE=${SERVICE:-daily-english-go}
BACKUP_ROOT=${BACKUP_ROOT:-/var/backups/daily-english}
HEALTH_LOCAL=${HEALTH_LOCAL:-http://127.0.0.1:8788/api/health}
HEALTH_PUBLIC=${HEALTH_PUBLIC:-}
# The systemd unit on the server is hand-maintained (it usually points at an
# EnvironmentFile that this repository knows nothing about), so it is never
# replaced unless you explicitly ask for it.
UPDATE_UNIT=${UPDATE_UNIT:-0}
KEEP=${KEEP:-5}
# Rehearsal escape hatch. Set to 1 to run the script against sandbox paths on a
# workstation (WEB_ROOT=/tmp/... APP_DIR=/tmp/... plus a stub systemctl on
# PATH). Production runs must leave it unset: the root check is what stops a
# half-configured invocation from writing to the wrong place.
ALLOW_NON_ROOT=${ALLOW_NON_ROOT:-0}
BIN_NAME=daily-english-go

if [ -z "$TARBALL" ]; then
  echo "usage: remote-deploy.sh <release.tar.gz>" >&2
  exit 2
fi
if [ "$(id -u)" != 0 ] && [ "$ALLOW_NON_ROOT" != 1 ]; then
  echo "must run as root: ssh host 'sudo bash -s -- $TARBALL' < deploy/remote-deploy.sh" >&2
  echo "(set ALLOW_NON_ROOT=1 only to rehearse against sandbox paths)" >&2
  exit 2
fi
if [ ! -f "$TARBALL" ]; then
  echo "no such tarball: $TARBALL" >&2
  exit 2
fi
for tool in tar rsync curl systemctl mktemp date install; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "missing required tool: $tool" >&2
    exit 2
  }
done

health_ok() {
  url=$1
  tries=${2:-10}
  i=1
  while [ "$i" -le "$tries" ]; do
    if curl -fsSk --max-time 5 "$url" >/dev/null 2>&1; then
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  return 1
}

# Ownership only makes sense when we are root; a workstation rehearsal
# (ALLOW_NON_ROOT=1) would otherwise die on "invalid user 'root'".
install_file() {
  mode=$1
  src=$2
  dst=$3
  if [ "$(id -u)" = 0 ]; then
    install -m "$mode" -o root -g root "$src" "$dst"
  else
    install -m "$mode" "$src" "$dst"
  fi
}

stamp=$(date -u +%Y%m%d-%H%M%S)
work=$(mktemp -d "${TMPDIR:-/tmp}/daily-english-deploy.XXXXXX")
trap 'rm -rf "$work"' EXIT

# Anything that breaks after the first change is applied must undo that change,
# otherwise the host is left half-old-web/half-new-binary.
rolling_back=0
swapped_web=0
swapped_bin=0
backup=""
on_error() {
  line=$1
  if [ "$rolling_back" = 1 ]; then
    return 0
  fi
  if [ "$swapped_web" = 1 ] || [ "$swapped_bin" = 1 ]; then
    echo "== deploy broke at line $line, after changes were applied" >&2
    rollback
  fi
  echo "== deploy broke at line $line; nothing on the host was changed" >&2
  exit 1
}
trap 'on_error $LINENO' ERR

echo "== unpacking $TARBALL"
tar -xzf "$TARBALL" -C "$work"
revision=$(cat "$work/REVISION" 2>/dev/null || echo unknown)
echo "== revision $revision"

if [ ! -d "$work/web" ] && [ ! -f "$work/bin/$BIN_NAME" ]; then
  echo "tarball contains neither web/ nor bin/$BIN_NAME; nothing to deploy" >&2
  exit 2
fi

backup="$BACKUP_ROOT/$stamp-$revision"
mkdir -p "$backup"

rollback() {
  rolling_back=1
  echo "== rolling back to the previous revision" >&2
  if [ "$swapped_bin" = 1 ] && [ -f "$backup/$BIN_NAME" ]; then
    install_file 755 "$backup/$BIN_NAME" "$APP_DIR/$BIN_NAME"
    echo "   restored $APP_DIR/$BIN_NAME" >&2
  fi
  if [ "$swapped_web" = 1 ] && [ -d "$backup/web" ]; then
    rsync -a --delete "$backup/web/" "$WEB_ROOT/"
    echo "   restored $WEB_ROOT" >&2
  fi
  systemctl restart "$SERVICE" || true
  if health_ok "$HEALTH_LOCAL" 5; then
    echo "== rollback complete; the previous revision is serving again" >&2
  else
    echo "== ROLLBACK DID NOT RECOVER THE SERVICE; inspect it now:" >&2
    echo "   systemctl status $SERVICE && journalctl -u $SERVICE -n 100" >&2
  fi
  echo "== backup kept at $backup" >&2
  exit 1
}

# --- back up whatever is live right now -----------------------------------
if [ -d "$WEB_ROOT" ]; then
  echo "== backing up $WEB_ROOT"
  mkdir -p "$backup/web"
  rsync -a --delete "$WEB_ROOT/" "$backup/web/"
fi
if [ -f "$APP_DIR/$BIN_NAME" ]; then
  echo "== backing up $APP_DIR/$BIN_NAME"
  cp -a "$APP_DIR/$BIN_NAME" "$backup/$BIN_NAME"
fi
if [ -f "/etc/systemd/system/$SERVICE.service" ]; then
  cp -a "/etc/systemd/system/$SERVICE.service" "$backup/$SERVICE.service"
fi

# Keep only the newest $KEEP backups (newest first by mtime).
prune_index=0
while IFS= read -r old_backup; do
  [ -n "$old_backup" ] || continue
  prune_index=$((prune_index + 1))
  if [ "$prune_index" -gt "$KEEP" ]; then
    echo "== pruning old backup $old_backup"
    rm -rf "$old_backup"
  fi
done < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null |
  sort -rn | cut -d' ' -f2-)

# --- web bundle ------------------------------------------------------------
if [ -d "$work/web" ]; then
  echo "== updating $WEB_ROOT"
  install -d -m 755 "$WEB_ROOT"
  # Pass 1 adds the new hashed assets and index.html without removing anything,
  # so a browser mid-navigation can still fetch the chunk it asked for.
  rsync -a "$work/web/" "$WEB_ROOT/"
  # Pass 2 drops files the new bundle no longer ships.
  rsync -a --delete "$work/web/" "$WEB_ROOT/"
  swapped_web=1
fi

# --- binary ----------------------------------------------------------------
if [ -f "$work/bin/$BIN_NAME" ]; then
  echo "== updating $APP_DIR/$BIN_NAME"
  install -d -m 755 "$APP_DIR"
  install_file 755 "$work/bin/$BIN_NAME" "$APP_DIR/$BIN_NAME.new"
  mv -f "$APP_DIR/$BIN_NAME.new" "$APP_DIR/$BIN_NAME"
  swapped_bin=1
fi

# --- systemd unit (opt-in) -------------------------------------------------
if [ "$UPDATE_UNIT" = 1 ] && [ -f "$work/systemd/$SERVICE.service" ]; then
  if ! cmp -s "$work/systemd/$SERVICE.service" "/etc/systemd/system/$SERVICE.service" 2>/dev/null; then
    echo "== updating systemd unit (UPDATE_UNIT=1)"
    install_file 644 "$work/systemd/$SERVICE.service" "/etc/systemd/system/$SERVICE.service"
    systemctl daemon-reload
  fi
fi

# --- restart and verify ----------------------------------------------------
echo "== restarting $SERVICE"
systemctl restart "$SERVICE"

if ! health_ok "$HEALTH_LOCAL" 10; then
  echo "== local health check failed: $HEALTH_LOCAL" >&2
  systemctl status --no-pager -l "$SERVICE" 2>&1 | tail -25 >&2 || true
  if command -v journalctl >/dev/null 2>&1; then
    journalctl -u "$SERVICE" -n 40 --no-pager >&2 || true
  fi
  rollback
fi

if [ -n "$HEALTH_PUBLIC" ] && ! health_ok "$HEALTH_PUBLIC" 5; then
  echo "== public health check failed: $HEALTH_PUBLIC" >&2
  rollback
fi

echo "== deployed revision $revision"
echo "== backup kept at $backup"
echo "== health: $(curl -fsSk --max-time 5 "$HEALTH_LOCAL" || echo 'unavailable')"

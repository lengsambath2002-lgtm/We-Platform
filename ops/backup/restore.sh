#!/bin/sh
# Restores a backup made by backup.sh: checks the checksums, loads the database dump into $PGDATABASE (which must be
# empty, unless --force) and unpacks the files into $FILES_DIR. Stop the API first. See docs/ops/backups.md.
#
#   ops/backup/restore.sh backups/2026-11-02T020000Z [--force]
set -eu

SRC="${1:?usage: restore.sh <backup directory> [--force]}"
FORCE="${2:-}"
: "${PGDATABASE:?set PGDATABASE (and PGHOST, PGPORT, PGUSER, PGPASSWORD)}"
log() { echo "restore: $*"; }

[ -f "$SRC/database.dump" ] || { log "no database.dump in $SRC"; exit 1; }
log "checking checksums"
if command -v sha256sum > /dev/null; then SUM="sha256sum"; else SUM="shasum -a 256"; fi
(cd "$SRC" && $SUM -c SHA256SUMS) || { log "checksums don't match: the backup is damaged"; exit 1; }

TABLES="$(psql -Atc "select count(*) from information_schema.tables where table_schema = 'public'")"
if [ "$TABLES" != "0" ] && [ "$FORCE" != "--force" ]; then
  log "$PGDATABASE already has $TABLES tables. Restore into a new, empty database, or pass --force to replace it."
  exit 1
fi

log "restoring the database into $PGDATABASE"
pg_restore --no-owner --no-privileges --clean --if-exists --exit-on-error --dbname="$PGDATABASE" "$SRC/database.dump"

if [ -f "$SRC/files.tar.gz" ]; then
  : "${FILES_DIR:?set FILES_DIR to restore the uploaded files}"
  log "restoring files into $FILES_DIR"
  mkdir -p "$FILES_DIR"
  tar -C "$FILES_DIR" -xzf "$SRC/files.tar.gz"
fi
log "done. Start the API: Flyway applies any newer migrations."

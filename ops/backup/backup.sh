#!/bin/sh
# Backs up Business Platform: the PostgreSQL database (pg_dump custom format) and the uploaded files, into
# $BACKUP_DIR/<UTC time>/ with SHA-256 checksums; checks the dump can be read; deletes local backups older than
# $BACKUP_KEEP_DAYS; and, with $BACKUP_S3_URI set, copies the backup off-site (aws CLI, any S3-compatible store).
# See docs/ops/backups.md.
#
#   PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE   the database (libpq variables)
#   FILES_DIR          uploaded files (the API's PLATFORM_FILES_DIR); skipped when empty or missing
#   BACKUP_DIR         where backups go (default ./backups)
#   BACKUP_KEEP_DAYS   local retention in days (default 30)
#   BACKUP_S3_URI      e.g. s3://my-bucket/business-platform (optional; S3_ENDPOINT_URL for non-AWS stores)
set -eu

BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"
STAMP="$(date -u +%Y-%m-%dT%H%M%SZ)"
OUT="$BACKUP_DIR/$STAMP"
PARTIAL="$OUT.partial"

log() { echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) backup: $*"; }
fail() { log "FAILED: $*"; rm -rf "$PARTIAL"; exit 1; }

: "${PGDATABASE:?set PGDATABASE (and PGHOST, PGPORT, PGUSER, PGPASSWORD)}"
umask 077
mkdir -p "$PARTIAL"

log "dumping database $PGDATABASE on ${PGHOST:-localhost}:${PGPORT:-5432}"
pg_dump --format=custom --compress=6 --no-owner --no-privileges --file="$PARTIAL/database.dump" \
  || fail "pg_dump"
# A dump that pg_restore can't list is no backup.
pg_restore --list "$PARTIAL/database.dump" > /dev/null || fail "the dump can't be read"

if [ -n "${FILES_DIR:-}" ] && [ -d "$FILES_DIR" ]; then
  log "archiving files in $FILES_DIR"
  tar -C "$FILES_DIR" -czf "$PARTIAL/files.tar.gz" . || fail "tar"
else
  log "no files directory; database only"
fi

if command -v sha256sum > /dev/null; then SUM="sha256sum"; else SUM="shasum -a 256"; fi
(cd "$PARTIAL" && $SUM database.dump $([ -f files.tar.gz ] && echo files.tar.gz) > SHA256SUMS) || fail "checksums"
mv "$PARTIAL" "$OUT"
log "done: $OUT ($(du -sh "$OUT" | cut -f1))"

if [ -n "${BACKUP_S3_URI:-}" ]; then
  log "copying to $BACKUP_S3_URI/$STAMP"
  aws s3 cp --recursive ${S3_ENDPOINT_URL:+--endpoint-url "$S3_ENDPOINT_URL"} "$OUT" "$BACKUP_S3_URI/$STAMP" \
    || fail "off-site copy (the local backup is kept)"
fi

# Retention: only complete backups (named by time) older than KEEP_DAYS.
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '20*Z' -mtime "+$KEEP_DAYS" -print -exec rm -rf {} + \
  | sed 's/^/  removed old backup /'
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '*.partial' -mtime +1 -exec rm -rf {} +
log "kept $(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '20*Z' | wc -l | tr -d ' ') backups"

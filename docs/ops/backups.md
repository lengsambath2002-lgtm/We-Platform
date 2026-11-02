# Backups and restore

Everything a customer has is in two places: the PostgreSQL database and the uploaded files (`PLATFORM_FILES_DIR`,
the `files` volume). `ops/backup/backup.sh` saves both together; `ops/backup/restore.sh` puts them back.

## What runs

With `docker compose up`, the **backup** service runs `backup.sh` every day at 02:00 Phnom Penh time
(`BACKUP_SCHEDULE`, cron in UTC, default `0 19 * * *`). Each run writes `/backups/<UTC time>/` in the `backups`
volume:

| File | What |
| --- | --- |
| `database.dump` | `pg_dump` custom format (compressed), checked with `pg_restore --list` before it counts |
| `files.tar.gz` | every uploaded file |
| `SHA256SUMS` | checksums, checked again before a restore |

Backups older than `BACKUP_KEEP_DAYS` (30, as the Privacy Policy says) are deleted. A run that fails leaves no
half-written backup behind and exits non-zero, so the container log shows `FAILED`.

Run one by hand (before an upgrade, for example):

```sh
docker compose exec backup sh /ops/backup.sh
docker compose exec backup ls /backups
```

Without Docker, set the libpq variables and run the script anywhere `pg_dump` (same major version as the server or
newer) is installed:

```sh
PGHOST=localhost PGPORT=5432 PGUSER=bizplat PGPASSWORD=… PGDATABASE=business_platform \
FILES_DIR=/srv/business-platform/files BACKUP_DIR=/srv/backups ops/backup/backup.sh
```

## Off-site copies

A backup on the same server doesn't survive losing that server. Copy every backup to another place:

- set `BACKUP_S3_URI` (and `S3_ENDPOINT_URL` for an S3-compatible store such as Cloudflare R2 or Wasabi) where the
  `aws` CLI is installed, and the script copies each new backup there; the `postgres:16-alpine` image has no `aws`,
  so either run the script from a host that has it, or sync the `backups` volume from such a host
  (`aws s3 sync /var/lib/docker/volumes/business-platform_backups/_data s3://…`);
- keep the bucket private, turn on its versioning or object lock, and give it its own lifecycle rule (for example
  delete after 30 days, to match the Privacy Policy).

A managed database (AWS RDS, DigitalOcean, Google Cloud SQL) adds point-in-time recovery; keep this file backup as
well, because it also has the uploaded files and can be restored anywhere.

## Restore

1. Stop the API (`docker compose stop api web`) so nothing writes while you restore.
2. Restore into a new, empty database (the script refuses a database that has tables, unless `--force`):

   ```sh
   createdb -O bizplat business_platform_restored
   PGDATABASE=business_platform_restored FILES_DIR=/path/to/files ops/backup/restore.sh /backups/2026-11-02T190000Z
   ```

3. Point `DATABASE_URL` (and `PLATFORM_FILES_DIR`) at the restored copy and start the API: Flyway applies any newer
   migrations. Keep the old database until you have checked the restored one.

`PLATFORM_JWT_SECRET` must be the same as before: it also encrypts the two-step sign-in keys (without it, people
set two-step sign-in up again).

## Drill

Restore the latest backup into a scratch database once a month and compare a few counts (`app_user`, `organisation`,
`task`) with production. Last drill: 2026-11-02 on the development database (2,876 accounts, 1,068 organisations,
307 tasks, 32 migrations, all uploaded files): counts identical.

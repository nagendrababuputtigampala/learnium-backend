#!/usr/bin/env bash
# Back up ONE Learnium stack (tracker 56): database dump + uploaded files.
#
#   deploy/backup.sh prod     # or: dev
#
# The database is managed (Azure PG for prod, Neon for dev), so the dump is
# taken over the network with pg_dump from a throwaway postgres:16 container;
# the provider's own automated backups stay on as well — this is the second,
# portable copy. Uploaded files live in the stack's Docker volume and are
# tarred. Output goes to ~/learnium-backups/<stack>/ and files older than
# 7 days are pruned. Run nightly from cron on the VM, e.g.:
#   15 3 * * *  cd ~/learnium/learnium-backend && deploy/backup.sh prod >> ~/learnium-backups/backup.log 2>&1
set -euo pipefail

STACK="${1:?usage: backup.sh prod|dev}"
case "${STACK}" in prod|dev) ;; *) echo "stack must be prod or dev" >&2; exit 2;; esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_PATH="${ROOT}/${STACK}.env"
[ -f "${ENV_PATH}" ] || { echo "missing ${ENV_PATH}" >&2; exit 1; }

# Load the stack's own env file (it lives only on the VM, chmod 600).
set -a; . "${ENV_PATH}"; set +a

BACKUP_DIR="${HOME}/learnium-backups/${STACK}"
mkdir -p "${BACKUP_DIR}"
TS="$(date +%Y%m%d-%H%M%S)"

# Parse DB_URL (jdbc:postgresql://host[:port]/db?params) into pg parts.
rest="${DB_URL#jdbc:postgresql://}"
hostport="${rest%%/*}"
dbname="${rest#*/}"; dbname="${dbname%%\?*}"
host="${hostport%%:*}"
port="${hostport##*:}"; [ "${port}" = "${host}" ] && port=5432

echo "==> Dumping database ${dbname} on ${host} (${STACK})"
docker run --rm \
  -e PGPASSWORD="${DB_PASSWORD}" -e PGSSLMODE=require \
  -v "${BACKUP_DIR}:/backup" \
  postgres:16-alpine \
  pg_dump -h "${host}" -p "${port}" -U "${DB_USERNAME}" -d "${dbname}" \
    --no-owner -f "/backup/db-${TS}.sql"

echo "==> Archiving uploaded files volume learnium-${STACK}_appfiles"
docker run --rm \
  -v "learnium-${STACK}_appfiles:/data:ro" \
  -v "${BACKUP_DIR}:/backup" \
  alpine tar czf "/backup/files-${TS}.tar.gz" -C /data .

echo "==> Pruning backups older than 7 days"
find "${BACKUP_DIR}" -type f -mtime +7 -delete

echo "Backup complete for ${STACK} at ${TS}:"
ls -lh "${BACKUP_DIR}" | tail -n +2

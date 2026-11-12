#!/usr/bin/env bash
# Reset ONE Learnium stack back to the seeded demo state (tracker 53).
#
#   deploy/reset-demo.sh prod          # asks you to type the stack name
#   deploy/reset-demo.sh prod --yes    # no prompt (for scripted runs)
#
# What it does:
#   1. Refuses to run unless the stack's env uses the `demo` profile (only
#      demo-profile stacks are seeded, so resetting anything else would
#      wipe real data with nothing to re-seed).
#   2. Stops the backend, drops and recreates the `learnium` schema
#      (Flyway's history table lives in it, so migrations re-run cleanly),
#      and empties the uploaded-files volume.
#   3. Starts the backend: Flyway re-applies V1..Vn and the demo seeder
#      recreates the demo centre + logins.
#   4. Waits for the health endpoint and prints the demo logins.
#
# WARNING: this destroys all data in the target stack's database. Prod and
# dev have separate databases and volumes; the argument picks exactly one.
set -euo pipefail

STACK="${1:?usage: reset-demo.sh prod|dev [--yes]}"
case "${STACK}" in prod|dev) ;; *) echo "stack must be prod or dev" >&2; exit 2;; esac
ASSUME_YES="${2:-}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_PATH="${ROOT}/${STACK}.env"
[ -f "${ENV_PATH}" ] || { echo "missing ${ENV_PATH}" >&2; exit 1; }

set -a; . "${ENV_PATH}"; set +a

case ",${SPRING_PROFILES_ACTIVE}," in
  *,demo,*) ;;
  *) echo "Refusing to reset: stack ${STACK} does not run the demo profile." >&2; exit 1;;
esac

if [ "${ASSUME_YES}" != "--yes" ]; then
  echo "This will DELETE ALL DATA in the ${STACK} database and files volume."
  read -r -p "Type the stack name (${STACK}) to continue: " confirm
  [ "${confirm}" = "${STACK}" ] || { echo "Aborted."; exit 1; }
fi

COMPOSE=(docker compose -p "learnium-${STACK}" -f "${ROOT}/docker-compose.azure.yml" --env-file "${ENV_PATH}")

# Parse DB_URL (jdbc:postgresql://host[:port]/db?params) into pg parts.
rest="${DB_URL#jdbc:postgresql://}"
hostport="${rest%%/*}"
dbname="${rest#*/}"; dbname="${dbname%%\?*}"
host="${hostport%%:*}"
port="${hostport##*:}"; [ "${port}" = "${host}" ] && port=5432

echo "==> Stopping backend (${STACK})"
"${COMPOSE[@]}" stop backend

echo "==> Dropping and recreating schema learnium on ${host}"
docker run --rm \
  -e PGPASSWORD="${DB_PASSWORD}" -e PGSSLMODE=require \
  postgres:16-alpine \
  psql -h "${host}" -p "${port}" -U "${DB_USERNAME}" -d "${dbname}" \
    -v ON_ERROR_STOP=1 \
    -c "DROP SCHEMA IF EXISTS learnium CASCADE" \
    -c "CREATE SCHEMA learnium"

echo "==> Emptying uploaded files volume learnium-${STACK}_appfiles"
docker run --rm -v "learnium-${STACK}_appfiles:/data" alpine \
  sh -c "rm -rf /data/files && mkdir -p /data/files"

echo "==> Starting backend — Flyway migrates, demo seeder re-seeds"
"${COMPOSE[@]}" up -d backend

echo "==> Waiting for health endpoint on port ${FRONTEND_PORT:-80}"
for i in $(seq 1 30); do
  if curl -fsS "http://localhost:${FRONTEND_PORT:-80}/api/v1/system/ping" >/dev/null 2>&1; then
    echo "Stack ${STACK} is up. Demo logins:"
    echo "  Admin:   admin@demo.learnium.local / Demo@123"
    echo "  Teacher: patel@demo.learnium.local / Demo@123"
    echo "  Student: simran@dtc / Demo@123"
    exit 0
  fi
  sleep 2
done
echo "Backend did not answer within 60s — check: ${COMPOSE[*]} logs backend" >&2
exit 1

#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${STACK_ENV_FILE:-$ROOT_DIR/.env.docker}"
PASSPHRASE_FILE="${BACKUP_PASSPHRASE_FILE:-}"
MINIO_BUCKET="${MINIO_BUCKET_NAME:-familyagent}"
MINIO_MC_IMAGE="${DOCKER_MINIO_MC_IMAGE:-minio/mc:latest}"
BACKUP_DIR="${1:-}"
CONFIRMATION="${2:-}"
STACK_MODE="${STACK_MODE:-production}"
WORK_DIR="$(mktemp -d)"

cleanup() {
  rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT

if [[ -z "$BACKUP_DIR" || "$CONFIRMATION" != "--confirm" ]]; then
  echo "Usage: BACKUP_PASSPHRASE_FILE=/secure/passphrase $0 <backup-directory> --confirm"
  echo "This replaces PostgreSQL data and overwrites matching MinIO objects."
  exit 1
fi

for required in "$ENV_FILE" "$PASSPHRASE_FILE" \
  "$BACKUP_DIR/postgresql.dump.enc" "$BACKUP_DIR/minio.tar.gz.enc" "$BACKUP_DIR/SHA256SUMS"; do
  if [[ ! -f "$required" ]]; then
    echo "[ERROR] Missing required file: $required"
    exit 1
  fi
done

(
  cd "$BACKUP_DIR"
  sha256sum -c SHA256SUMS
)

openssl enc -d -aes-256-cbc -pbkdf2 \
  -pass "file:$PASSPHRASE_FILE" \
  -in "$BACKUP_DIR/postgresql.dump.enc" \
  -out "$WORK_DIR/postgresql.dump"
openssl enc -d -aes-256-cbc -pbkdf2 \
  -pass "file:$PASSPHRASE_FILE" \
  -in "$BACKUP_DIR/minio.tar.gz.enc" \
  -out "$WORK_DIR/minio.tar.gz"
tar -C "$WORK_DIR" -xzf "$WORK_DIR/minio.tar.gz"

case "$STACK_MODE" in
  local)
    COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/compose.yml" -f "$ROOT_DIR/deploy/compose/local.yml")
    APP_SERVICES=(frontend backend)
    ;;
  prod|production)
    COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/compose.yml" -f "$ROOT_DIR/deploy/compose/production.yml")
    APP_SERVICES=(cloudflared frontend backend)
    ;;
  *)
    echo "[ERROR] STACK_MODE must be local or production."
    exit 1
    ;;
esac

echo "[INFO] Stopping application services..."
STACK_ENV_FILE="$ENV_FILE" "${COMPOSE[@]}" stop "${APP_SERVICES[@]}"

echo "[INFO] Restoring PostgreSQL..."
STACK_ENV_FILE="$ENV_FILE" "${COMPOSE[@]}" exec -T postgres \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    --clean --if-exists --no-owner --no-privileges --exit-on-error --single-transaction' \
  < "$WORK_DIR/postgresql.dump"

MINIO_CONTAINER_ID="$(STACK_ENV_FILE="$ENV_FILE" "${COMPOSE[@]}" ps -q minio)"
if [[ -z "$MINIO_CONTAINER_ID" ]]; then
  echo "[ERROR] MinIO container is not running."
  exit 1
fi

echo "[INFO] Restoring MinIO objects..."
docker run --rm \
  --env-file "$ENV_FILE" \
  --network "container:$MINIO_CONTAINER_ID" \
  -e BACKUP_MINIO_BUCKET="$MINIO_BUCKET" \
  -v "$WORK_DIR/minio:/restore:ro" \
  --entrypoint /bin/sh \
  "$MINIO_MC_IMAGE" -c \
  'mc alias set familyagent http://127.0.0.1:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null &&
   mc mirror --overwrite "/restore/$BACKUP_MINIO_BUCKET" "familyagent/$BACKUP_MINIO_BUCKET"'

echo "[INFO] Starting application services..."
STACK_ENV_FILE="$ENV_FILE" "${COMPOSE[@]}" up -d "${APP_SERVICES[@]}"

echo "[OK] Restore completed. Verify login, memory queries, and object downloads before reopening traffic."

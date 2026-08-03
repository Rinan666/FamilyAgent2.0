#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${STACK_ENV_FILE:-$ROOT_DIR/.env.docker}"
BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/familyagent}"
OFFSITE_ROOT="${BACKUP_OFFSITE_ROOT:-}"
PASSPHRASE_FILE="${BACKUP_PASSPHRASE_FILE:-}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
MINIO_BUCKET="${MINIO_BUCKET_NAME:-familyagent}"
MINIO_MC_IMAGE="${DOCKER_MINIO_MC_IMAGE:-minio/mc:latest}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DESTINATION="$BACKUP_ROOT/$TIMESTAMP"
WORK_DIR="$(mktemp -d)"
STAGING_DIR="$BACKUP_ROOT/.incomplete-$TIMESTAMP-$$"

cleanup() {
  rm -rf -- "$WORK_DIR"
  if [[ -n "$STAGING_DIR" && -d "$STAGING_DIR" ]]; then
    rm -rf -- "$STAGING_DIR"
  fi
}
trap cleanup EXIT

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "[ERROR] Missing required file: $1"
    exit 1
  fi
}

if [[ "$BACKUP_ROOT" == "/" || ${#BACKUP_ROOT} -lt 8 ]]; then
  echo "[ERROR] BACKUP_ROOT is unsafe: $BACKUP_ROOT"
  exit 1
fi

if [[ ! "$RETENTION_DAYS" =~ ^[1-9][0-9]*$ ]]; then
  echo "[ERROR] BACKUP_RETENTION_DAYS must be a positive integer."
  exit 1
fi

require_file "$ENV_FILE"
require_file "$PASSPHRASE_FILE"
if [[ ! -s "$PASSPHRASE_FILE" ]]; then
  echo "[ERROR] Backup passphrase file is empty."
  exit 1
fi
if [[ -n "$OFFSITE_ROOT" && "$OFFSITE_ROOT" == "$BACKUP_ROOT"* ]]; then
  echo "[ERROR] BACKUP_OFFSITE_ROOT must not be inside BACKUP_ROOT."
  exit 1
fi
mkdir -p "$BACKUP_ROOT" "$STAGING_DIR" "$WORK_DIR/minio"

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/compose.yml")
DATABASE_NAME="$(STACK_ENV_FILE="$ENV_FILE" "${COMPOSE[@]}" exec -T postgres sh -c 'printf %s "$POSTGRES_DB"')"

echo "[INFO] Dumping PostgreSQL..."
STACK_ENV_FILE="$ENV_FILE" "${COMPOSE[@]}" exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges' \
  > "$WORK_DIR/postgresql.dump"

MINIO_CONTAINER_ID="$(STACK_ENV_FILE="$ENV_FILE" "${COMPOSE[@]}" ps -q minio)"
if [[ -z "$MINIO_CONTAINER_ID" ]]; then
  echo "[ERROR] MinIO container is not running."
  exit 1
fi

echo "[INFO] Mirroring MinIO bucket..."
docker run --rm \
  --env-file "$ENV_FILE" \
  --network "container:$MINIO_CONTAINER_ID" \
  -e BACKUP_MINIO_BUCKET="$MINIO_BUCKET" \
  -v "$WORK_DIR/minio:/backup" \
  --entrypoint /bin/sh \
  "$MINIO_MC_IMAGE" -c \
  'mc alias set familyagent http://127.0.0.1:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null &&
   mc mirror --overwrite "familyagent/$BACKUP_MINIO_BUCKET" "/backup/$BACKUP_MINIO_BUCKET"'

tar -C "$WORK_DIR" -czf "$WORK_DIR/minio.tar.gz" minio

for artifact in postgresql.dump minio.tar.gz; do
  openssl enc -aes-256-cbc -pbkdf2 -salt \
    -pass "file:$PASSPHRASE_FILE" \
    -in "$WORK_DIR/$artifact" \
    -out "$STAGING_DIR/$artifact.enc"
done

(
  cd "$STAGING_DIR"
  sha256sum postgresql.dump.enc minio.tar.gz.enc > SHA256SUMS
)

cat > "$STAGING_DIR/manifest.txt" <<EOF
created_at_utc=$TIMESTAMP
database=$DATABASE_NAME
minio_bucket=$MINIO_BUCKET
encryption=aes-256-cbc-pbkdf2
retention_days=$RETENTION_DAYS
EOF

mv "$STAGING_DIR" "$DESTINATION"
STAGING_DIR=""

if [[ -n "$OFFSITE_ROOT" ]]; then
  mkdir -p "$OFFSITE_ROOT"
  cp -a "$DESTINATION" "$OFFSITE_ROOT/"
  echo "[INFO] Copied encrypted backup to offsite path: $OFFSITE_ROOT/$TIMESTAMP"
fi

find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d \
  -mtime "+$RETENTION_DAYS" -exec rm -rf -- {} +

echo "[OK] Backup completed: $DESTINATION"

#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${STACK_ENV_FILE:-$ROOT_DIR/.env.docker}"
MAX_USES="${1:-1}"
VALID_DAYS="${2:-30}"
SOURCE="${3:-operations}"
DESCRIPTION="${4:-Issued by operations script}"

if [[ ! "$MAX_USES" =~ ^[1-9][0-9]*$ ]]; then
  echo "[ERROR] max uses must be a positive integer."
  exit 1
fi

if [[ ! "$VALID_DAYS" =~ ^[1-9][0-9]*$ ]]; then
  echo "[ERROR] valid days must be a positive integer."
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] Missing env file: $ENV_FILE"
  exit 1
fi

INVITE_CODE="FA-$(openssl rand -hex 16 | tr '[:lower:]' '[:upper:]')"
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/compose.yml")

STACK_ENV_FILE="$ENV_FILE" "${COMPOSE[@]}" exec -T \
  -e INVITE_CODE="$INVITE_CODE" \
  -e INVITE_SOURCE="$SOURCE" \
  -e INVITE_DESCRIPTION="$DESCRIPTION" \
  -e INVITE_MAX_USES="$MAX_USES" \
  -e INVITE_VALID_DAYS="$VALID_DAYS" \
  postgres sh -c 'psql -v ON_ERROR_STOP=1 \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" \
    -v invite_code="$INVITE_CODE" \
    -v invite_source="$INVITE_SOURCE" \
    -v invite_description="$INVITE_DESCRIPTION" \
    -v max_uses="$INVITE_MAX_USES" \
    -v valid_days="$INVITE_VALID_DAYS"' <<'SQL'
INSERT INTO invite_codes (
    code,
    source,
    description,
    max_uses,
    used_count,
    status,
    expires_at
)
VALUES (
    :'invite_code',
    :'invite_source',
    :'invite_description',
    :max_uses,
    0,
    'ACTIVE',
    NOW() + make_interval(days => :valid_days)
);
SQL

echo "Invite code: $INVITE_CODE"
echo "Expires in: $VALID_DAYS days"
echo "Maximum uses: $MAX_USES"

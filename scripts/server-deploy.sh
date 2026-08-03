#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

# --- Pre-flight checks ---
if [[ ! -f .env.docker ]]; then
  echo "[ERROR] Missing .env.docker"
  echo "       cp deploy/examples/stack.env.example .env.docker"
  echo "       Then edit .env.docker and set your API keys and secrets."
  exit 1
fi

# Check that at least AI_INTERNAL_SERVICE_TOKEN has been changed from the placeholder.
if grep -qE '^AI_INTERNAL_SERVICE_TOKEN\s*=\s*(change-me|familyagent-prod-internal-token|$)' .env.docker; then
  echo "[ERROR] AI_INTERNAL_SERVICE_TOKEN still has a placeholder value in .env.docker."
  echo "       Generate a strong random token and update it."
  exit 1
fi

# Verify Dockerfiles exist.
for f in ai-service/Dockerfile backend/Dockerfile frontend/Dockerfile; do
  if [[ ! -f "$f" ]]; then
    echo "[ERROR] Missing Dockerfile: $f — run this from the repo root."
    exit 1
  fi
done

echo "[OK] Pre-flight checks passed. Building and starting the stack..."

STACK_ENV_FILE="$ROOT_DIR/.env.docker" docker compose \
  --env-file .env.docker \
  -f compose.yml \
  -f deploy/compose/production.yml \
  up -d --build

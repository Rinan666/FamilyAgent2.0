#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

if [[ ! -f .env.docker ]]; then
  echo "[ERROR] Missing .env.docker"
  echo "cp .env.docker.example .env.docker"
  exit 1
fi

docker compose --env-file .env.docker -f docker-compose.stack.yml up -d --build

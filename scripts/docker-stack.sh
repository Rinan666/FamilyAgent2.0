#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STACK_MODE="${STACK_MODE:-local}"

case "$STACK_MODE" in
  local)
    ENV_FILE="${STACK_ENV_FILE:-$ROOT_DIR/.env}"
    COMPOSE_FILES=(-f "$ROOT_DIR/compose.yml" -f "$ROOT_DIR/deploy/compose/local.yml")
    ;;
  prod|production)
    ENV_FILE="${STACK_ENV_FILE:-$ROOT_DIR/.env.docker}"
    COMPOSE_FILES=(-f "$ROOT_DIR/compose.yml" -f "$ROOT_DIR/deploy/compose/production.yml")
    ;;
  *)
    echo "[ERROR] STACK_MODE must be local or production."
    exit 1
    ;;
esac

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] Missing env file: $ENV_FILE"
  exit 1
fi

docker_compose() {
  STACK_ENV_FILE="$ENV_FILE" docker compose --env-file "$ENV_FILE" "${COMPOSE_FILES[@]}" "$@"
}

case "${1:-help}" in
  up)
    docker_compose up -d --build
    ;;
  down)
    docker_compose down
    ;;
  restart)
    docker_compose up -d --build --remove-orphans
    ;;
  logs)
    shift || true
    docker_compose logs -f "$@"
    ;;
  status|ps)
    docker_compose ps
    ;;
  config)
    docker_compose config
    ;;
  pull)
    docker_compose pull
    ;;
  help|--help|-h)
    cat <<EOF
Usage: scripts/docker-stack.sh <command>

Environment:
  STACK_MODE=local       Use loopback-only host ports (default)
  STACK_MODE=production  Use the Cloudflare production override
  STACK_ENV_FILE=path    Override the environment file

Commands:
  up       Build and start the stack
  down     Stop the stack
  restart  Rebuild and restart the stack
  logs     Follow logs
  status   Show service status
  config   Render the resolved Compose configuration
  pull     Pull referenced images
EOF
    ;;
  *)
    echo "[ERROR] Unsupported command: $1"
    exit 1
    ;;
esac

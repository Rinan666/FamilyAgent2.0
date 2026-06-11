#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/docker-compose.stack.yml}"
ENV_FILE="${STACK_ENV_FILE:-$ROOT_DIR/.env.docker}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] Missing env file: $ENV_FILE"
  echo "        cp $ROOT_DIR/.env.docker.example $ENV_FILE"
  echo "        Then edit $ENV_FILE and set your API keys and secrets."
  exit 1
fi

docker_compose() {
  STACK_ENV_FILE="$ENV_FILE" docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
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
    echo "=== Resolved compose config ==="
    docker_compose config
    ;;
  pull)
    docker_compose pull
    ;;
  help|--help|-h)
    cat <<EOF
Usage: scripts/docker-stack.sh <command>

Commands:
  up       Build and start the full stack (7 services)
  down     Stop and remove the stack
  restart  Rebuild and restart (adds --remove-orphans)
  logs     Follow logs, e.g. ./scripts/docker-stack.sh logs backend
  status   Show service status
  config   Render the resolved compose config (useful for debugging env vars)
  pull     Pull referenced base images

First-time setup:
  cp .env.docker.example .env.docker
  # Edit .env.docker — replace API keys and secrets
  ./scripts/docker-stack.sh up
EOF
    ;;
  *)
    echo "[ERROR] Unsupported command: $1"
    exit 1
    ;;
esac

#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/docker-compose.stack.yml}"
ENV_FILE="${STACK_ENV_FILE:-$ROOT_DIR/.env.docker}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] Missing env file: $ENV_FILE"
  echo "        Copy $ROOT_DIR/.env.docker.example to $ENV_FILE and fill in real secrets first."
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
    docker_compose up -d --build
    ;;
  logs)
    shift || true
    docker_compose logs -f "$@"
    ;;
  ps)
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

Commands:
  up       Build and start the full stack
  down     Stop and remove the stack
  restart  Rebuild and restart the stack
  logs     Follow logs, optionally for selected services
  ps       Show service status
  config   Render the resolved compose config
  pull     Pull referenced base images
EOF
    ;;
  *)
    echo "[ERROR] Unsupported command: $1"
    exit 1
    ;;
esac

#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$ROOT_DIR/.service-pids.txt"
RUNTIME_PID_FILE="$ROOT_DIR/.codex-runtime-pids.txt"
INFRA_ENV_FILE="$ROOT_DIR/.env.infra.local"
AI_ENV_FILE="$ROOT_DIR/ai-service/.env"
FRONTEND_PORT=3000
BACKEND_PORT=8180
AI_SERVICE_PORT_DEFAULT=8090

print_header() {
  echo
  echo "============================================"
  echo "    FamilyAgent - Stop All Services (Linux)"
  echo "============================================"
  echo
}

load_env_file() {
  local env_file="$1"
  local line
  local key
  local value
  if [[ -f "$env_file" ]]; then
    while IFS= read -r line || [[ -n "$line" ]]; do
      line="${line%$'\r'}"
      line="${line#$'\ufeff'}"
      [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
      if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
        key="${BASH_REMATCH[1]}"
        value="${BASH_REMATCH[2]}"
        export "$key=$value"
      fi
    done <"$env_file"
  fi
}

pid_on_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -n 1 || true
    return
  fi
  if command -v ss >/dev/null 2>&1; then
    ss -ltnp "sport = :$port" 2>/dev/null | awk -F 'pid=' 'NR>1 && NF>1 {split($2,a,","); print a[1]; exit}' || true
    return
  fi
  echo ""
}

stop_pid() {
  local pid="$1"
  local label="$2"
  local retries=10
  local i

  if [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; then
    return
  fi

  kill "$pid" 2>/dev/null || true
  for ((i = 1; i <= retries; i++)); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "        Stopped $label (PID $pid)"
      return
    fi
    sleep 1
  done

  kill -9 "$pid" 2>/dev/null || true
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "        Force stopped $label (PID $pid)"
  fi
}

stop_pids_from_file() {
  local file_path="$1"
  local label_prefix="$2"
  local line
  local pid

  if [[ ! -f "$file_path" ]]; then
    return
  fi

  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    pid="${line##*=}"
    pid="${pid//[[:space:]]/}"
    [[ "$pid" =~ ^[0-9]+$ ]] || continue
    stop_pid "$pid" "$label_prefix"
  done <"$file_path"
}

stop_service_on_port() {
  local port="$1"
  local label="$2"
  local pid
  pid="$(pid_on_port "$port")"
  if [[ -n "${pid:-}" ]]; then
    stop_pid "$pid" "$label on port $port"
  fi
}

print_header

load_env_file "$AI_ENV_FILE"
AI_SERVICE_PORT="${AI_SERVICE_PORT:-$AI_SERVICE_PORT_DEFAULT}"

echo "Stopping app services..."
stop_pids_from_file "$PID_FILE" "service"
stop_pids_from_file "$RUNTIME_PID_FILE" "runtime"

stop_service_on_port "$FRONTEND_PORT" "frontend"
stop_service_on_port "$BACKEND_PORT" "backend"
stop_service_on_port "$AI_SERVICE_PORT" "ai-service"

pkill -f "cloudflared.*tunnel" 2>/dev/null || true

rm -f "$PID_FILE" "$RUNTIME_PID_FILE"

echo
echo "Stopping infrastructure..."
if command -v docker >/dev/null 2>&1; then
  if [[ -f "$INFRA_ENV_FILE" ]]; then
    docker compose --env-file "$INFRA_ENV_FILE" -f "$ROOT_DIR/docker-compose.yml" stop >/dev/null
  else
    docker compose -f "$ROOT_DIR/docker-compose.yml" stop >/dev/null
  fi
  echo "        Infrastructure stopped"
else
  echo "        [SKIP] docker not found"
fi

echo
echo "============================================"
echo "   All services stopped."
echo "============================================"

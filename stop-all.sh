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
    ss -ltnp "sport = :$port" 2>/dev/null | awk 'NR>1 {split($NF,a,"pid="); if(length(a)>1){split(a[2],b,","); print b[1]; exit}}' || true
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
    echo "        [SKIP] $label (PID $pid) is not running"
    return
  fi

  echo "        Stopping $label (PID $pid)..."
  kill "$pid" 2>/dev/null || true
  
  # Wait for graceful shutdown
  for ((i = 1; i <= retries; i++)); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "        [OK] Stopped $label (PID $pid)"
      return
    fi
    sleep 1
  done

  # Force kill if still running
  echo "        Force killing $label (PID $pid)..."
  kill -9 "$pid" 2>/dev/null || true
  sleep 1
  
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "        [OK] Force stopped $label (PID $pid)"
  else
    echo "        [WARNING] Failed to stop $label (PID $pid)"
  fi
}

stop_pids_from_file() {
  local file_path="$1"
  local label_prefix="$2"
  local line
  local pid
  local name

  if [[ ! -f "$file_path" ]]; then
    echo "        [SKIP] PID file not found: $file_path"
    return
  fi

  echo "        Processing $file_path..."
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    
    # Handle both formats: "name=pid" and plain "pid"
    if [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*=[0-9]+$ ]]; then
      name="${line%%=*}"
      pid="${line##*=}"
    elif [[ "$line" =~ ^[0-9]+$ ]]; then
      name="$label_prefix"
      pid="$line"
    else
      echo "        [SKIP] Invalid PID format: $line"
      continue
    fi
    
    pid="${pid//[[:space:]]/}"
    stop_pid "$pid" "${name:-$label_prefix}"
  done <"$file_path"
}

stop_service_on_port() {
  local port="$1"
  local label="$2"
  local pid
  pid="$(pid_on_port "$port")"
  if [[ -n "${pid:-}" ]]; then
    stop_pid "$pid" "$label on port $port"
  else
    echo "        [SKIP] No process found on port $port for $label"
  fi
}

print_header

load_env_file "$AI_ENV_FILE"
AI_SERVICE_PORT="${AI_SERVICE_PORT:-$AI_SERVICE_PORT_DEFAULT}"

echo "Stopping app services..."

# Stop services from PID files (in reverse order of startup)
if [[ -f "$RUNTIME_PID_FILE" ]]; then
  echo "Stopping runtime services..."
  stop_pids_from_file "$RUNTIME_PID_FILE" "runtime"
fi

if [[ -f "$PID_FILE" ]]; then
  echo "Stopping background services..."
  stop_pids_from_file "$PID_FILE" "service"
fi

# Stop tunnel if running
if [[ -x "$ROOT_DIR/tunnel.sh" ]]; then
  echo "Stopping Cloudflare Tunnel..."
  bash "$ROOT_DIR/tunnel.sh" down >/dev/null || true
fi

# Fallback: kill any remaining processes on known ports
echo "Checking for remaining processes on standard ports..."
stop_service_on_port "$FRONTEND_PORT" "frontend"
stop_service_on_port "$BACKEND_PORT" "backend"
stop_service_on_port "$AI_SERVICE_PORT" "ai-service"

# Clean up PID files
rm -f "$PID_FILE" "$RUNTIME_PID_FILE"

echo
echo "Stopping infrastructure..."
if command -v docker >/dev/null 2>&1; then
  if [[ -f "$INFRA_ENV_FILE" ]]; then
    echo "        Stopping Docker containers..."
    docker compose --env-file "$INFRA_ENV_FILE" -f "$ROOT_DIR/docker-compose.yml" down >/dev/null 2>&1 || \
    docker compose --env-file "$INFRA_ENV_FILE" -f "$ROOT_DIR/docker-compose.yml" stop >/dev/null 2>&1 || true
  else
    echo "        Stopping Docker containers (no env file)..."
    docker compose -f "$ROOT_DIR/docker-compose.yml" down >/dev/null 2>&1 || \
    docker compose -f "$ROOT_DIR/docker-compose.yml" stop >/dev/null 2>&1 || true
  fi
  echo "        [OK] Infrastructure stopped"
else
  echo "        [SKIP] docker not found"
fi

echo
echo "============================================"
echo "   All services stopped."
echo "============================================"

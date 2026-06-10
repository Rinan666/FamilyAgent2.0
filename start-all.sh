#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
PID_FILE="$ROOT_DIR/.service-pids.txt"
RUNTIME_PID_FILE="$ROOT_DIR/.codex-runtime-pids.txt"
INFRA_ENV_FILE="$ROOT_DIR/.env.infra.local"
INFRA_ENV_EXAMPLE_FILE="$ROOT_DIR/.env.infra.example"
TUNNEL_ENV_FILE="$ROOT_DIR/.env.tunnel.local"
TUNNEL_EXAMPLE_FILE="$ROOT_DIR/.env.tunnel.example"
AI_ENV_FILE="$ROOT_DIR/ai-service/.env"
FRONTEND_PORT=3000
BACKEND_PORT=8180
AI_SERVICE_PORT_DEFAULT=8090

mkdir -p "$LOG_DIR"
: > "$PID_FILE"
: > "$RUNTIME_PID_FILE"

print_header() {
  echo
  echo "============================================"
  echo "    FamilyAgent One-Click Start (Linux)"
  echo "============================================"
  echo
}

print_step() {
  echo
  echo "[$1/5] $2"
}

require_command() {
  local cmd="$1"
  local hint="$2"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERROR] Missing command: $cmd"
    echo "        $hint"
    exit 1
  fi
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

record_named_pid() {
  local name="$1"
  local pid="$2"
  local tmp_file
  tmp_file="$(mktemp)"
  if [[ -f "$RUNTIME_PID_FILE" ]]; then
    grep -v "^${name}=" "$RUNTIME_PID_FILE" >"$tmp_file" || true
  fi
  {
    cat "$tmp_file"
    echo "${name}=${pid}"
  } >"$RUNTIME_PID_FILE"
  rm -f "$tmp_file"
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

kill_port_if_needed() {
  local port="$1"
  local service_name="$2"
  local pid
  pid="$(pid_on_port "$port")"
  if [[ -n "${pid:-}" ]]; then
    kill "$pid" 2>/dev/null || sudo kill "$pid" 2>/dev/null || true
    sleep 2
    # Verify the process is actually stopped
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 "$pid" 2>/dev/null || sudo kill -9 "$pid" 2>/dev/null || true
      sleep 1
    fi
    echo "        [OK] Stopped old $service_name process on port $port (PID $pid)"
  fi
}

wait_for_postgres() {
  local retries=30
  local i
  for ((i = 1; i <= retries; i++)); do
    if docker exec fa-postgres pg_isready -U "${DB_USER:-fa_user}" -d "${DB_NAME:-familyagent}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

start_background_service() {
  local name="$1"
  local work_dir="$2"
  local log_prefix="$3"
  shift 3
  local out_log="$LOG_DIR/${log_prefix}.out.log"
  local err_log="$LOG_DIR/${log_prefix}.err.log"

  (
    cd "$work_dir"
    "$@"
  ) >"$out_log" 2>"$err_log" &

  local pid=$!
  # Save to both PID files with consistent format
  echo "$pid" >>"$PID_FILE"
  record_named_pid "$name" "$pid"
  echo "        $name started (PID $pid)"
  echo "        Logs: $out_log"
  
  # Wait a moment and verify the process is still running
  sleep 2
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "        [WARNING] $name (PID $pid) exited unexpectedly. Check logs: $err_log"
    return 1
  fi
  return 0
}

verify_service_on_port() {
  local port="$1"
  local service_name="$2"
  local max_wait=30
  local waited=0
  
  while [[ $waited -lt $max_wait ]]; do
    if pid_on_port "$port" >/dev/null 2>&1; then
      echo "        [OK] $service_name is listening on port $port"
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  
  echo "        [WARNING] $service_name may not be ready on port $port after ${max_wait}s"
  return 1
}

print_header

print_step 0 "Checking prerequisites..."
require_command docker "Install Docker Engine and Docker Compose plugin."
require_command java "Install Java 17 and ensure 'java' is on PATH."
require_command node "Install Node.js and ensure 'node' is on PATH."
require_command npm "Install npm and ensure 'npm' is on PATH."
require_command bash "bash is required to run this script."
if [[ ! -x "$ROOT_DIR/backend/mvnw" ]]; then
  chmod +x "$ROOT_DIR/backend/mvnw"
fi

if [[ ! -f "$INFRA_ENV_FILE" ]]; then
  echo "[ERROR] Missing infra config: $INFRA_ENV_FILE"
  if [[ -f "$INFRA_ENV_EXAMPLE_FILE" ]]; then
    echo "        Copy $INFRA_ENV_EXAMPLE_FILE to .env.infra.local and fill in local values."
  fi
  exit 1
fi

if [[ ! -x "$ROOT_DIR/ai-service/.venv/bin/python" ]]; then
  echo "[ERROR] Missing AI service virtual environment: $ROOT_DIR/ai-service/.venv/bin/python"
  echo "        Create it with:"
  echo "          cd ai-service"
  echo "          python3.12 -m venv .venv"
  echo "          ./.venv/bin/python -m pip install -r requirements.txt"
  exit 1
fi

if [[ ! -x "$ROOT_DIR/frontend/node_modules/.bin/next" ]]; then
  echo "[ERROR] Missing frontend dependencies: $ROOT_DIR/frontend/node_modules/.bin/next"
  echo "        Install them with:"
  echo "          cd frontend"
  echo "          npm install"
  exit 1
fi

echo "        All checks passed"

# Load environment variables early to get port configurations
load_env_file "$INFRA_ENV_FILE"
load_env_file "$AI_ENV_FILE"
load_env_file "$TUNNEL_ENV_FILE"
AI_SERVICE_PORT="${AI_SERVICE_PORT:-$AI_SERVICE_PORT_DEFAULT}"
START_TUNNEL_VALUE="${START_TUNNEL:-false}"
TUNNEL_PUBLIC_HOST_VALUE="${TUNNEL_PUBLIC_HOST:-}"

print_step 1 "Starting infrastructure..."
docker compose --env-file "$INFRA_ENV_FILE" -f "$ROOT_DIR/docker-compose.yml" up -d
echo "        Containers started, waiting for PostgreSQL..."
if ! wait_for_postgres; then
  echo "[ERROR] PostgreSQL did not become ready in time."
  exit 1
fi

echo "        Database ready (schema will be managed by backend Flyway)"

print_step 2 "Starting AI Service (port $AI_SERVICE_PORT)..."
kill_port_if_needed "$AI_SERVICE_PORT" "AI Service"
start_background_service \
  "ai-service" \
  "$ROOT_DIR/ai-service" \
  "ai-service" \
  env AI_SERVICE_PORT="$AI_SERVICE_PORT" "$ROOT_DIR/ai-service/.venv/bin/python" -m uvicorn app.main:app --host 0.0.0.0 --port "$AI_SERVICE_PORT"
verify_service_on_port "$AI_SERVICE_PORT" "AI Service" || true

print_step 3 "Starting Backend (port $BACKEND_PORT)..."
kill_port_if_needed "$BACKEND_PORT" "Backend"
start_background_service \
  "backend" \
  "$ROOT_DIR/backend" \
  "backend" \
  env SERVER_PORT="$BACKEND_PORT" "$ROOT_DIR/backend/mvnw" spring-boot:run -Dspring-boot.run.profiles=dev
verify_service_on_port "$BACKEND_PORT" "Backend" || true

print_step 4 "Starting Frontend (port $FRONTEND_PORT)..."
kill_port_if_needed "$FRONTEND_PORT" "Frontend"
echo "        Building frontend (this may take a while)..."
(
  cd "$ROOT_DIR/frontend"
  npm run build >"$LOG_DIR/frontend-build.log" 2>&1
) || {
  echo "        [WARNING] Frontend build failed. Check $LOG_DIR/frontend-build.log"
  echo "        Attempting to start anyway..."
}
start_background_service \
  "frontend" \
  "$ROOT_DIR/frontend" \
  "frontend" \
  npm run start -- --hostname 0.0.0.0 --port "$FRONTEND_PORT"
verify_service_on_port "$FRONTEND_PORT" "Frontend" || true

print_step 5 "Starting Cloudflare Tunnel..."
if [[ "${START_TUNNEL_VALUE,,}" == "true" ]]; then
  bash "$ROOT_DIR/tunnel.sh" up
else
  echo "        [SKIP] Tunnel disabled (set START_TUNNEL=true in .env.tunnel.local to auto-start)"
  if [[ ! -f "$TUNNEL_ENV_FILE" && -f "$TUNNEL_EXAMPLE_FILE" ]]; then
    echo "        Copy $TUNNEL_EXAMPLE_FILE to .env.tunnel.local to configure Tunnel defaults."
  fi
fi

echo
echo "============================================"
echo "   All services launching!"
echo
echo "   Frontend:  http://localhost:$FRONTEND_PORT"
if [[ -n "$TUNNEL_PUBLIC_HOST_VALUE" ]]; then
  echo "   Public:    https://$TUNNEL_PUBLIC_HOST_VALUE"
fi
echo "   Backend:   http://localhost:$BACKEND_PORT"
echo "   AI API:    http://localhost:$AI_SERVICE_PORT/docs"
echo "   MinIO:     http://localhost:9001"
echo "   RabbitMQ:  http://localhost:15672"
echo
echo "   PID file:  $PID_FILE"
echo "   Runtime PID file: $RUNTIME_PID_FILE"
echo "   Logs dir:  $LOG_DIR"
echo "============================================"

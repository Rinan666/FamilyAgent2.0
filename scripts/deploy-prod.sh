#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ROOT="${APP_ROOT:-$ROOT_DIR}"
BRANCH="${BRANCH:-main}"
PYTHON_BIN="${PYTHON_BIN:-python3.12}"

FRONTEND_HEALTH_URL="${FRONTEND_HEALTH_URL:-http://127.0.0.1:3000}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8180/actuator/health}"
AI_HEALTH_URL="${AI_HEALTH_URL:-http://127.0.0.1:8090/ai/health}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
AI_SERVICE_PORT="${AI_SERVICE_PORT:-8090}"
CLOUDFLARED_CONFIG_PATH="${CLOUDFLARED_CONFIG_PATH:-/etc/familyagent/cloudflared/config.yml}"

run_systemctl() {
  if [[ "${EUID}" -eq 0 ]]; then
    systemctl "$@"
  else
    sudo systemctl "$@"
  fi
}

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "[ERROR] Missing required command: $command_name"
    exit 1
  fi
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local retries="${3:-60}"
  local sleep_seconds="${4:-5}"
  local attempt

  for ((attempt = 1; attempt <= retries; attempt++)); do
    if curl --fail --silent --show-error "$url" >/dev/null; then
      echo "        [OK] $label is healthy: $url"
      return 0
    fi
    sleep "$sleep_seconds"
  done

  echo "[ERROR] Timed out waiting for $label health check: $url"
  return 1
}

print_unit_diagnostics() {
  local unit_name="$1"

  echo
  echo "[INFO] Diagnostics for $unit_name"
  run_systemctl status "$unit_name" --no-pager -l || true
  run_systemctl show "$unit_name" -p ExecMainStatus -p ExecMainCode -p Result -p NRestarts || true
  journalctl -u "$unit_name" -n 120 --no-pager || true
}

wait_for_systemd_active() {
  local unit_name="$1"
  local retries="${2:-24}"
  local sleep_seconds="${3:-5}"
  local attempt
  local state

  for ((attempt = 1; attempt <= retries; attempt++)); do
    state="$(run_systemctl show "$unit_name" -p ActiveState --value | tr -d '\r')"
    if [[ "$state" == "active" ]]; then
      return 0
    fi
    if [[ "$state" == "failed" ]]; then
      break
    fi
    sleep "$sleep_seconds"
  done

  return 1
}

ensure_port_free() {
  local port="$1"
  local label="$2"
  local listener

  listener="$(ss -ltnp "( sport = :$port )" 2>/dev/null | tail -n +2 || true)"
  if [[ -n "$listener" ]]; then
    echo "[ERROR] $label port $port is already in use. Stop the existing process before deploy."
    echo "$listener"
    return 1
  fi
}

build_frontend() {
  echo "        Building frontend..."
  if [[ -d "$APP_ROOT/frontend/.next" ]]; then
    rm -rf "$APP_ROOT/frontend/.next"
  fi

  if ! (
    cd "$APP_ROOT/frontend"
    npm run build
  ); then
    echo "[ERROR] Frontend build failed."
    return 1
  fi

  if [[ ! -f "$APP_ROOT/frontend/.next/BUILD_ID" ]]; then
    echo "[ERROR] Frontend build did not produce .next/BUILD_ID."
    return 1
  fi
}

restart_and_verify_service() {
  local unit_name="$1"
  local health_url="$2"
  local label="$3"
  local expect_active_only="${4:-false}"

  echo
  echo "        Restarting $label..."
  if ! run_systemctl restart "$unit_name"; then
    echo "[ERROR] Failed to restart $label."
    print_unit_diagnostics "$unit_name"
    return 1
  fi

  if ! wait_for_systemd_active "$unit_name"; then
    echo "[ERROR] $label did not reach active state."
    print_unit_diagnostics "$unit_name"
    return 1
  fi

  if [[ "$expect_active_only" == "true" ]]; then
    echo "        [OK] $label is active"
    return 0
  fi

  if ! wait_for_http "$health_url" "$label"; then
    print_unit_diagnostics "$unit_name"
    return 1
  fi
}

echo
echo "============================================"
echo "    FamilyAgent production deploy"
echo "============================================"
echo

require_command git
require_command curl
require_command npm
require_command ss
require_command "$PYTHON_BIN"
require_command cloudflared

cd "$APP_ROOT"

echo "[1/6] Updating git checkout..."
git fetch origin "$BRANCH"
git checkout "$BRANCH"
git pull --ff-only origin "$BRANCH"

echo
echo "[2/6] Refreshing systemd units..."
START_SERVICES=false APP_ROOT="$APP_ROOT" bash "$APP_ROOT/scripts/install-systemd-services.sh"

echo
echo "[3/6] Updating dependencies and validating config..."
(
  cd "$APP_ROOT/frontend"
  npm ci
)

if [[ ! -x "$APP_ROOT/ai-service/.venv/bin/python" ]]; then
  echo "        Creating AI virtual environment..."
  (
    cd "$APP_ROOT/ai-service"
    "$PYTHON_BIN" -m venv .venv
  )
fi

(
  cd "$APP_ROOT/ai-service"
  ./.venv/bin/python -m pip install -r requirements.txt
)

cloudflared tunnel --config "$CLOUDFLARED_CONFIG_PATH" ingress validate
ensure_port_free "$AI_SERVICE_PORT" "AI service"

echo
echo "[4/6] Building frontend release..."
build_frontend

echo
echo "[5/6] Restarting and verifying FamilyAgent services..."
restart_and_verify_service familyagent-infra.service "" "Infra" true
restart_and_verify_service familyagent-ai.service "$AI_HEALTH_URL" "AI service"
restart_and_verify_service familyagent-backend.service "$BACKEND_HEALTH_URL" "Backend"
restart_and_verify_service familyagent-frontend.service "$FRONTEND_HEALTH_URL" "Frontend"
restart_and_verify_service familyagent-tunnel.service "" "Cloudflare Tunnel" true

echo
echo "[6/6] Deployment complete."
echo "        Branch: $BRANCH"
echo "        Frontend: $FRONTEND_HEALTH_URL"
echo "        Backend:  $BACKEND_HEALTH_URL"
echo "        AI:       $AI_HEALTH_URL"

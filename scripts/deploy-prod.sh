#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ROOT="${APP_ROOT:-$ROOT_DIR}"
BRANCH="${BRANCH:-main}"
PYTHON_BIN="${PYTHON_BIN:-python3.12}"

FRONTEND_HEALTH_URL="${FRONTEND_HEALTH_URL:-http://127.0.0.1:3000}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8180/actuator/health}"
AI_HEALTH_URL="${AI_HEALTH_URL:-http://127.0.0.1:8090/ai/health}"

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

echo
echo "============================================"
echo "    FamilyAgent production deploy"
echo "============================================"
echo

require_command git
require_command curl
require_command npm
require_command "$PYTHON_BIN"

cd "$APP_ROOT"

echo "[1/6] Updating git checkout..."
git fetch origin "$BRANCH"
git checkout "$BRANCH"
git pull --ff-only origin "$BRANCH"

echo
echo "[2/6] Refreshing systemd units..."
START_SERVICES=false APP_ROOT="$APP_ROOT" bash "$APP_ROOT/scripts/install-systemd-services.sh"

echo
echo "[3/6] Updating dependencies..."
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

echo
echo "[4/6] Restarting FamilyAgent services..."
run_systemctl restart familyagent-infra.service
run_systemctl restart familyagent-ai.service
run_systemctl restart familyagent-backend.service
run_systemctl restart familyagent-frontend.service
run_systemctl restart familyagent-tunnel.service

echo
echo "[5/6] Waiting for health checks..."
wait_for_http "$AI_HEALTH_URL" "AI service"
wait_for_http "$BACKEND_HEALTH_URL" "Backend"
wait_for_http "$FRONTEND_HEALTH_URL" "Frontend"

echo
echo "[6/6] Deployment complete."
echo "        Branch: $BRANCH"
echo "        Frontend: $FRONTEND_HEALTH_URL"
echo "        Backend:  $BACKEND_HEALTH_URL"
echo "        AI:       $AI_HEALTH_URL"

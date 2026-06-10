#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ROOT="${APP_ROOT:-$ROOT_DIR}"
ETC_DIR="${ETC_DIR:-/etc/familyagent}"
SYSTEMD_DIR="${SYSTEMD_DIR:-/etc/systemd/system}"
DEPLOY_USER="${DEPLOY_USER:-${SUDO_USER:-$(id -un)}}"
DEPLOY_GROUP="${DEPLOY_GROUP:-$(id -gn "$DEPLOY_USER")}"
START_SERVICES="${START_SERVICES:-false}"

SUDO=""
if [[ "${EUID}" -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    SUDO="sudo"
  else
    echo "[ERROR] Please run as root or install sudo."
    exit 1
  fi
fi

render_template() {
  local template_path="$1"
  local target_path="$2"
  local tmp_file
  tmp_file="$(mktemp)"

  sed \
    -e "s|__APP_ROOT__|$APP_ROOT|g" \
    -e "s|__ETC_DIR__|$ETC_DIR|g" \
    -e "s|__DEPLOY_USER__|$DEPLOY_USER|g" \
    -e "s|__DEPLOY_GROUP__|$DEPLOY_GROUP|g" \
    "$template_path" >"$tmp_file"

  $SUDO install -m 0644 "$tmp_file" "$target_path"
  rm -f "$tmp_file"
}

install_example() {
  local source_path="$1"
  local target_path="$2"
  $SUDO install -d "$(dirname "$target_path")"
  $SUDO install -m 0644 "$source_path" "$target_path"
}

warn_if_missing() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "[WARN] Missing required file: $path"
  fi
}

$SUDO install -d "$SYSTEMD_DIR" "$ETC_DIR" "$ETC_DIR/cloudflared"

render_template \
  "$ROOT_DIR/deploy/systemd/familyagent-infra.service.template" \
  "$SYSTEMD_DIR/familyagent-infra.service"
render_template \
  "$ROOT_DIR/deploy/systemd/familyagent-ai.service.template" \
  "$SYSTEMD_DIR/familyagent-ai.service"
render_template \
  "$ROOT_DIR/deploy/systemd/familyagent-backend.service.template" \
  "$SYSTEMD_DIR/familyagent-backend.service"
render_template \
  "$ROOT_DIR/deploy/systemd/familyagent-frontend.service.template" \
  "$SYSTEMD_DIR/familyagent-frontend.service"
render_template \
  "$ROOT_DIR/deploy/systemd/familyagent-tunnel.service.template" \
  "$SYSTEMD_DIR/familyagent-tunnel.service"
$SUDO install -m 0644 \
  "$ROOT_DIR/deploy/systemd/familyagent.target" \
  "$SYSTEMD_DIR/familyagent.target"

install_example "$ROOT_DIR/deploy/env/infra.env.example" "$ETC_DIR/infra.env.example"
install_example "$ROOT_DIR/deploy/env/backend.env.example" "$ETC_DIR/backend.env.example"
install_example "$ROOT_DIR/deploy/env/ai-service.env.example" "$ETC_DIR/ai-service.env.example"
install_example "$ROOT_DIR/deploy/env/frontend.env.example" "$ETC_DIR/frontend.env.example"
install_example \
  "$ROOT_DIR/deploy/cloudflared/config.yml.example" \
  "$ETC_DIR/cloudflared/config.yml.example"

warn_if_missing "$ETC_DIR/infra.env"
warn_if_missing "$ETC_DIR/backend.env"
warn_if_missing "$ETC_DIR/ai-service.env"
warn_if_missing "$ETC_DIR/frontend.env"
warn_if_missing "$ETC_DIR/cloudflared/config.yml"

$SUDO systemctl daemon-reload
$SUDO systemctl enable \
  familyagent.target \
  familyagent-infra.service \
  familyagent-ai.service \
  familyagent-backend.service \
  familyagent-frontend.service \
  familyagent-tunnel.service

if [[ "${START_SERVICES,,}" == "true" ]]; then
  $SUDO systemctl start familyagent.target
fi

echo
echo "Installed FamilyAgent systemd units into $SYSTEMD_DIR"
echo "Production env examples copied into $ETC_DIR"
echo "Deploy user: $DEPLOY_USER"
echo "Deploy group: $DEPLOY_GROUP"

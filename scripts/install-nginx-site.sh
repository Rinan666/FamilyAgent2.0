#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_PATH="$ROOT_DIR/scripts/nginx/familyagent.conf.template"
SITE_NAME="${SITE_NAME:-familyagent}"
TARGET_PATH="/etc/nginx/sites-available/${SITE_NAME}"
ENABLED_PATH="/etc/nginx/sites-enabled/${SITE_NAME}"
SERVER_NAMES="${SERVER_NAMES:-familyagent.cn www.familyagent.cn app.familyagent.cn app.familyagentai.top}"
NEXT_PORT="${NEXT_PORT:-3000}"
ENABLE_TLS="${ENABLE_TLS:-0}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-}"

if [[ ! -f "$TEMPLATE_PATH" ]]; then
  echo "[ERROR] Missing template: $TEMPLATE_PATH"
  exit 1
fi

if ! command -v nginx >/dev/null 2>&1; then
  echo "[ERROR] nginx is not installed."
  echo "        Install it first, for example: apt-get update && apt-get install -y nginx"
  exit 1
fi

SUDO=""
if [[ "${EUID}" -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    SUDO="sudo"
  else
    echo "[ERROR] Please run as root or install sudo."
    exit 1
  fi
fi

TMP_FILE="$(mktemp)"
cleanup() {
  rm -f "$TMP_FILE"
}
trap cleanup EXIT

sed \
  -e "s/__SERVER_NAMES__/${SERVER_NAMES}/g" \
  -e "s/__NEXT_PORT__/${NEXT_PORT}/g" \
  "$TEMPLATE_PATH" >"$TMP_FILE"

$SUDO mkdir -p /etc/nginx/sites-available /etc/nginx/sites-enabled
$SUDO cp "$TMP_FILE" "$TARGET_PATH"

if [[ ! -L "$ENABLED_PATH" ]]; then
  $SUDO ln -s "$TARGET_PATH" "$ENABLED_PATH"
fi

if [[ -L /etc/nginx/sites-enabled/default ]]; then
  $SUDO rm -f /etc/nginx/sites-enabled/default
fi

$SUDO nginx -t
$SUDO systemctl reload nginx

if command -v ufw >/dev/null 2>&1; then
  UFW_STATUS="$(ufw status 2>/dev/null | head -n 1 || true)"
  if [[ "$UFW_STATUS" == "Status: active" ]]; then
    $SUDO ufw allow 80/tcp >/dev/null
    $SUDO ufw allow 443/tcp >/dev/null
  fi
fi

if [[ "$ENABLE_TLS" == "1" ]]; then
  if ! command -v certbot >/dev/null 2>&1; then
    echo "[WARN] ENABLE_TLS=1 but certbot is not installed."
    echo "       Install it first, for example: apt-get install -y certbot python3-certbot-nginx"
    exit 1
  fi
  if [[ -z "$CERTBOT_EMAIL" ]]; then
    echo "[ERROR] ENABLE_TLS=1 requires CERTBOT_EMAIL."
    exit 1
  fi

  certbot_args=()
  for domain in $SERVER_NAMES; do
    certbot_args+=(-d "$domain")
  done

  $SUDO certbot --nginx --redirect --non-interactive --agree-tos \
    --email "$CERTBOT_EMAIL" "${certbot_args[@]}"

  $SUDO systemctl reload nginx
fi

echo
echo "Nginx site installed: $TARGET_PATH"
echo "Server names: $SERVER_NAMES"
echo "Proxy target: http://127.0.0.1:${NEXT_PORT}"
echo
echo "Remember to open ECS security-group ports 80 and 443."

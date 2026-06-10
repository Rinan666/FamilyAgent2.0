#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TUNNEL_ENV_FILE="$ROOT_DIR/.env.tunnel.local"
RUNTIME_PID_FILE="$ROOT_DIR/.codex-runtime-pids.txt"
LOG_DIR="$ROOT_DIR/logs"
TUNNEL_LOG_OUT="$LOG_DIR/cloudflared-tunnel.out.log"
TUNNEL_LOG_ERR="$LOG_DIR/cloudflared-tunnel.err.log"
TUNNEL_PID_NAME="tunnel"
ACTION="${1:-status}"
FOLLOW="${2:-}"

load_env_file() {
  local env_file="$1"
  local line
  local key
  local value
  if [[ ! -f "$env_file" ]]; then
    return
  fi

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
}

normalize_bool() {
  local value="${1:-}"
  local default="${2:-false}"
  if [[ -z "$value" ]]; then
    echo "$default"
    return
  fi

  case "${value,,}" in
    1|true|yes|on) echo "true" ;;
    0|false|no|off) echo "false" ;;
    *) echo "$default" ;;
  esac
}

get_config_value() {
  local name="$1"
  local default="${2:-}"
  local value="${!name:-}"
  if [[ -z "$value" ]]; then
    echo "$default"
  else
    echo "$value"
  fi
}

set_runtime_pid() {
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

remove_runtime_pid() {
  local name="$1"
  local tmp_file
  tmp_file="$(mktemp)"
  if [[ -f "$RUNTIME_PID_FILE" ]]; then
    grep -v "^${name}=" "$RUNTIME_PID_FILE" >"$tmp_file" || true
    if [[ -s "$tmp_file" ]]; then
      cat "$tmp_file" >"$RUNTIME_PID_FILE"
    else
      rm -f "$RUNTIME_PID_FILE"
    fi
  fi
  rm -f "$tmp_file"
}

get_runtime_pid() {
  if [[ ! -f "$RUNTIME_PID_FILE" ]]; then
    return
  fi
  grep -E "^${TUNNEL_PID_NAME}=" "$RUNTIME_PID_FILE" | tail -n 1 | cut -d'=' -f2
}

is_pid_alive() {
  local pid="${1:-}"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

resolved_config_path() {
  local default_config_path="${HOME}/.cloudflared/config.yml"
  get_config_value "TUNNEL_CONFIG_PATH" "$default_config_path"
}

find_cloudflared_pid() {
  local stored_pid
  local config_path
  stored_pid="$(get_runtime_pid || true)"
  if is_pid_alive "$stored_pid"; then
    echo "$stored_pid"
    return
  fi

  config_path="$(resolved_config_path)"
  if command -v pgrep >/dev/null 2>&1; then
    pgrep -f "cloudflared.*tunnel.*${config_path}" | head -n 1 || true
  fi
}

load_tunnel_config() {
  load_env_file "$TUNNEL_ENV_FILE"

  TUNNEL_ENABLED_VALUE="$(normalize_bool "$(get_config_value "TUNNEL_ENABLED" "true")" "true")"
  TUNNEL_PROVIDER_VALUE="$(get_config_value "TUNNEL_PROVIDER" "cloudflare")"
  TUNNEL_MODE_VALUE="$(get_config_value "TUNNEL_MODE" "named")"
  TUNNEL_CONFIG_PATH_VALUE="$(resolved_config_path)"
  TUNNEL_PUBLIC_HOST_VALUE="$(get_config_value "TUNNEL_PUBLIC_HOST" "")"
  TUNNEL_TARGET_URL_VALUE="$(get_config_value "TUNNEL_TARGET_URL" "http://127.0.0.1:3000")"
}

print_status() {
  load_tunnel_config

  local pid
  local state="stopped"
  pid="$(find_cloudflared_pid || true)"

  if [[ "$TUNNEL_ENABLED_VALUE" != "true" ]]; then
    state="disabled"
  elif [[ ! -f "$TUNNEL_CONFIG_PATH_VALUE" ]]; then
    state="not_configured"
  elif [[ -n "$pid" ]]; then
    state="running"
  fi

  echo "provider=$TUNNEL_PROVIDER_VALUE"
  echo "mode=$TUNNEL_MODE_VALUE"
  echo "enabled=$TUNNEL_ENABLED_VALUE"
  echo "state=$state"
  echo "pid=${pid:-}"
  echo "public_host=$TUNNEL_PUBLIC_HOST_VALUE"
  echo "target_url=$TUNNEL_TARGET_URL_VALUE"
  echo "config_path=$TUNNEL_CONFIG_PATH_VALUE"
  echo "log_out=$TUNNEL_LOG_OUT"
  echo "log_err=$TUNNEL_LOG_ERR"
}

ensure_tunnel_preconditions() {
  load_tunnel_config

  if [[ "$TUNNEL_ENABLED_VALUE" != "true" ]]; then
    echo "[ERROR] Tunnel is disabled. Set TUNNEL_ENABLED=true in .env.tunnel.local or your shell."
    exit 1
  fi
  if [[ "$TUNNEL_PROVIDER_VALUE" != "cloudflare" ]]; then
    echo "[ERROR] Unsupported tunnel provider: $TUNNEL_PROVIDER_VALUE"
    exit 1
  fi
  if [[ "$TUNNEL_MODE_VALUE" != "named" ]]; then
    echo "[ERROR] Unsupported tunnel mode: $TUNNEL_MODE_VALUE"
    exit 1
  fi
  if ! command -v cloudflared >/dev/null 2>&1; then
    echo "[ERROR] cloudflared not found on PATH."
    exit 1
  fi
  if [[ ! -f "$TUNNEL_CONFIG_PATH_VALUE" ]]; then
    echo "[ERROR] Tunnel config not found: $TUNNEL_CONFIG_PATH_VALUE"
    exit 1
  fi

  mkdir -p "$LOG_DIR"
}

start_tunnel() {
  ensure_tunnel_preconditions

  local existing_pid
  existing_pid="$(find_cloudflared_pid || true)"
  if [[ -n "$existing_pid" ]]; then
    set_runtime_pid "$TUNNEL_PID_NAME" "$existing_pid"
    echo "Tunnel already running (PID $existing_pid)"
    print_status
    return
  fi

  cloudflared tunnel --config "$TUNNEL_CONFIG_PATH_VALUE" run >"$TUNNEL_LOG_OUT" 2>"$TUNNEL_LOG_ERR" &
  local pid=$!
  sleep 1
  set_runtime_pid "$TUNNEL_PID_NAME" "$pid"
  echo "Tunnel started (PID $pid)"
  print_status
}

stop_tunnel() {
  load_tunnel_config

  local pid
  pid="$(find_cloudflared_pid || true)"
  if [[ -z "$pid" ]]; then
    remove_runtime_pid "$TUNNEL_PID_NAME"
    echo "Tunnel already stopped"
    print_status
    return
  fi

  kill "$pid" 2>/dev/null || true
  sleep 1
  if is_pid_alive "$pid"; then
    kill -9 "$pid" 2>/dev/null || true
  fi
  remove_runtime_pid "$TUNNEL_PID_NAME"
  echo "Tunnel stopped (PID $pid)"
  print_status
}

show_logs() {
  print_status
  for path in "$TUNNEL_LOG_OUT" "$TUNNEL_LOG_ERR"; do
    echo
    echo "==> $path <=="
    if [[ ! -f "$path" ]]; then
      echo "(missing)"
      continue
    fi

    if [[ "$FOLLOW" == "--follow" || "$FOLLOW" == "-f" ]]; then
      tail -n 40 -f "$path"
    else
      tail -n 40 "$path"
    fi
  done
}

case "$ACTION" in
  up) start_tunnel ;;
  down) stop_tunnel ;;
  status) print_status ;;
  logs) show_logs ;;
  *)
    echo "Usage: ./tunnel.sh {up|down|status|logs} [--follow]"
    exit 1
    ;;
esac

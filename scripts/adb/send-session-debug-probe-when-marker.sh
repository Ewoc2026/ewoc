#!/usr/bin/env bash
set -euo pipefail

SERIAL=""
MATCH_EVENT=""
TITLE=""
MESSAGE=""
MODE="show"
CLEAR_FIRST=false
TIMEOUT_SEC=120

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/send-session-debug-probe-when-marker.sh [options]

Options:
  --serial <id>        Use a specific adb device serial.
  --event <marker>     Required TEST_MARKER substring to wait for.
  --title <text>       Probe title.
  --message <text>     Probe message text.
  --show               Show the probe immediately when the marker appears.
  --arm                Arm the probe when the marker appears.
  --clear              Clear logcat before waiting.
  --timeout <seconds>  Stop waiting after this long. Default: 120.
  --help               Show this help.

Purpose:
  Wait for one specific TEST_MARKER breadcrumb, then send a session debug
  probe through the standard helper. This avoids hand-typed timing loops when
  a rider prompt should appear at a precise handoff milestone such as
  `continue_ride_handoff_session_ready`.

Examples:
  ./scripts/adb/send-session-debug-probe-when-marker.sh \
    --serial R92Y40YAZPB \
    --event continue_ride_handoff_session_ready \
    --title "Continue ride" \
    --message "Check handoff now." \
    --show
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "Missing value for --serial" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --event)
      [[ $# -ge 2 ]] || { echo "Missing value for --event" >&2; exit 2; }
      MATCH_EVENT="$2"
      shift 2
      ;;
    --title)
      [[ $# -ge 2 ]] || { echo "Missing value for --title" >&2; exit 2; }
      TITLE="$2"
      shift 2
      ;;
    --message)
      [[ $# -ge 2 ]] || { echo "Missing value for --message" >&2; exit 2; }
      MESSAGE="$2"
      shift 2
      ;;
    --show)
      MODE="show"
      shift
      ;;
    --arm)
      MODE="arm"
      shift
      ;;
    --clear)
      CLEAR_FIRST=true
      shift
      ;;
    --timeout)
      [[ $# -ge 2 ]] || { echo "Missing value for --timeout" >&2; exit 2; }
      TIMEOUT_SEC="$2"
      shift 2
      ;;
    --help|-h)
      print_help
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      print_help
      exit 2
      ;;
  esac
done

if [[ -z "$MATCH_EVENT" ]]; then
  echo "--event is required." >&2
  print_help
  exit 2
fi

if [[ -z "$MESSAGE" ]]; then
  echo "--message is required." >&2
  print_help
  exit 2
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH." >&2
  exit 1
fi

ADB_CMD=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB_CMD+=(-s "$SERIAL")
fi

STATE="$("${ADB_CMD[@]}" get-state 2>/dev/null || true)"
if [[ "$STATE" != "device" ]]; then
  echo "No active adb device in state 'device' (state='$STATE')." >&2
  exit 1
fi

if [[ "$CLEAR_FIRST" == true ]]; then
  "${ADB_CMD[@]}" logcat -c
fi

echo "Waiting for TEST_MARKER containing: $MATCH_EVENT"
echo "Mode: $MODE"
echo "Timeout: ${TIMEOUT_SEC}s"

exec 3< <("${ADB_CMD[@]}" logcat -v time -s TEST_MARKER DEBUG_VALIDATION)
deadline=$((SECONDS + TIMEOUT_SEC))

while (( SECONDS < deadline )); do
  if IFS= read -r -t 1 line <&3; then
    printf '%s\n' "$line"
    if [[ "$line" == *"$MATCH_EVENT"* ]]; then
      echo "Matched marker. Sending session debug probe."
      probe_cmd=("./scripts/adb/send-session-debug-probe.sh")
      if [[ -n "$SERIAL" ]]; then
        probe_cmd+=(--serial "$SERIAL")
      fi
      probe_cmd+=(--"$MODE")
      if [[ -n "$TITLE" ]]; then
        probe_cmd+=(--title "$TITLE")
      fi
      probe_cmd+=(--message "$MESSAGE")
      "${probe_cmd[@]}"
      exit 0
    fi
  fi
done

echo "Timed out waiting for TEST_MARKER containing '$MATCH_EVENT'." >&2
exit 1

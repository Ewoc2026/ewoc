#!/usr/bin/env bash
set -euo pipefail

SERIAL=""
CLEAR_FIRST=false

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/watch-session-debug-probe.sh [options]

Options:
  --serial <id>   Use a specific adb device serial.
  --clear         Clear logcat before starting the probe watcher.
  --help          Show this help.

Purpose:
  Follow only the session debug probe lifecycle and rider-signal events so
  live-riding tests do not depend on scanning the full filtered Ewoc log.

Events shown:
  - session_debug_probe_armed
  - session_debug_probe_shown
  - session_debug_probe_auto_shown
  - session_debug_probe_signal
  - session_debug_probe_hidden
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "Missing value for --serial" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --clear)
      CLEAR_FIRST=true
      shift
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

echo "Watching session debug probe events on $("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
echo "Press Ctrl+C to stop."

"${ADB_CMD[@]}" logcat -v time -s DEBUG_VALIDATION TEST_MARKER \
  | awk '
      /session_debug_probe_armed/ ||
      /session_debug_probe_shown/ ||
      /session_debug_probe_auto_shown/ ||
      /session_debug_probe_signal/ ||
      /session_debug_probe_hidden/ {
        print
        fflush()
      }
    '

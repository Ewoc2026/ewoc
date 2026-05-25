#!/usr/bin/env bash
set -euo pipefail

PACKAGE="io.github.ewoc2026.ewoc"
FILTER_REGEX='io\.github\.ewoc2026\.ewoc|com\.example\.ergometerapp|APP_BUILD|TEST_MARKER|DEBUG_VALIDATION|FTMS|SESSION|WORKOUT|BluetoothGatt|BluetoothLeScanner'

MODE="follow"
CLEAR_FIRST=false
SERIAL=""
OUTPUT_PATH=""
RAW=false
PID_ONLY=false

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/logcat-ergometer.sh [options]

Options:
  --serial <id>       Use a specific adb device serial.
  --dump              Dump current buffer and exit.
  --follow            Follow live logcat stream (default).
  --clear             Clear logcat buffer before reading.
  --output <path>     Write output to given file path.
  --raw               Do not filter lines (capture full logcat output).
  --pid-only          Capture only current app process logs using --pid.
  --help              Show this help.

Examples:
  ./scripts/adb/logcat-ergometer.sh --clear
  ./scripts/adb/logcat-ergometer.sh --dump
  ./scripts/adb/logcat-ergometer.sh --pid-only --clear
  ./scripts/adb/logcat-ergometer.sh --serial R92Y40YAZPB --clear --output .local/logs/session.log

Default filtered tags now include:
  APP_BUILD, TEST_MARKER, DEBUG_VALIDATION, FTMS, SESSION, WORKOUT
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "Missing value for --serial" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --dump)
      MODE="dump"
      shift
      ;;
    --follow)
      MODE="follow"
      shift
      ;;
    --clear)
      CLEAR_FIRST=true
      shift
      ;;
    --output)
      [[ $# -ge 2 ]] || { echo "Missing value for --output" >&2; exit 2; }
      OUTPUT_PATH="$2"
      shift 2
      ;;
    --raw)
      RAW=true
      shift
      ;;
    --pid-only)
      PID_ONLY=true
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
  ADB_CMD+=( -s "$SERIAL" )
fi

STATE="$("${ADB_CMD[@]}" get-state 2>/dev/null || true)"
if [[ "$STATE" != "device" ]]; then
  echo "No active adb device in state 'device' (state='$STATE')." >&2
  echo "Tip: run 'adb devices -l' and ensure USB authorization is accepted." >&2
  exit 1
fi

if [[ -z "$OUTPUT_PATH" ]]; then
  timestamp="$(date +%Y%m%d-%H%M%S)"
  OUTPUT_PATH=".local/logs/logcat-ergometer-${timestamp}.log"
fi

mkdir -p "$(dirname "$OUTPUT_PATH")"

if [[ "$CLEAR_FIRST" == true ]]; then
  "${ADB_CMD[@]}" logcat -c
fi

if [[ "$MODE" == "dump" ]]; then
  LOGCAT_ARGS=(-d -v time)
else
  LOGCAT_ARGS=(-v time)
fi

if [[ "$PID_ONLY" == true ]]; then
  APP_PID="$("${ADB_CMD[@]}" shell pidof -s "$PACKAGE" | tr -d '\r')"
  if [[ -z "$APP_PID" ]]; then
    echo "App process for '$PACKAGE' is not running; cannot use --pid-only." >&2
    echo "Tip: start the app first, or run without --pid-only." >&2
    exit 1
  fi
  LOGCAT_ARGS+=(--pid "$APP_PID")
fi

echo "Device: $("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
echo "Package filter context: ${PACKAGE}"
echo "Mode: ${MODE}"
echo "PID only: ${PID_ONLY}"
echo "Output: ${OUTPUT_PATH}"

if [[ "$RAW" == true ]]; then
  "${ADB_CMD[@]}" logcat "${LOGCAT_ARGS[@]}" | tee "$OUTPUT_PATH"
else
  "${ADB_CMD[@]}" logcat "${LOGCAT_ARGS[@]}" \
    | awk -v re="$FILTER_REGEX" '$0 ~ re' \
    | tee "$OUTPUT_PATH"
fi

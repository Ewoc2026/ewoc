#!/usr/bin/env bash
set -euo pipefail

SERIAL=""
OUT_DIR=".local/captures"
RECORD_SECONDS=5
SKIP_SCREENSHOT=false
SKIP_RECORD=false

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/capture.sh [options]

Options:
  --serial <id>         Use specific adb device serial.
  --out-dir <path>      Output directory (default: .local/captures).
  --seconds <n>         Screenrecord duration seconds (default: 5).
  --no-screenshot       Skip screenshot capture.
  --no-record           Skip screen recording capture.
  --help                Show this help.

Examples:
  ./scripts/adb/capture.sh
  ./scripts/adb/capture.sh --serial R92Y40YAZPB --seconds 10
  ./scripts/adb/capture.sh --no-record
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "Missing value for --serial" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --out-dir)
      [[ $# -ge 2 ]] || { echo "Missing value for --out-dir" >&2; exit 2; }
      OUT_DIR="$2"
      shift 2
      ;;
    --seconds)
      [[ $# -ge 2 ]] || { echo "Missing value for --seconds" >&2; exit 2; }
      RECORD_SECONDS="$2"
      shift 2
      ;;
    --no-screenshot)
      SKIP_SCREENSHOT=true
      shift
      ;;
    --no-record)
      SKIP_RECORD=true
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

if ! [[ "$RECORD_SECONDS" =~ ^[0-9]+$ ]] || [[ "$RECORD_SECONDS" -lt 1 ]]; then
  echo "--seconds must be a positive integer." >&2
  exit 2
fi

mkdir -p "$OUT_DIR"
TS="$(date +%Y%m%d-%H%M%S)"
MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
MIN_REASONABLE_PNG_SIZE_BYTES=50000

echo "Device: ${MODEL}"
echo "Output dir: ${OUT_DIR}"
echo "Timestamp: ${TS}"

# Improve screenshot reliability on devices that occasionally return a blank frame.
"${ADB_CMD[@]}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
"${ADB_CMD[@]}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
sleep 1

if [[ "$SKIP_SCREENSHOT" == false ]]; then
  PNG="${OUT_DIR}/screenshot-${TS}.png"
  "${ADB_CMD[@]}" exec-out screencap -p > "$PNG"
  PNG_SIZE_BYTES="$(wc -c < "$PNG" | tr -d '[:space:]')"
  if [[ "${PNG_SIZE_BYTES}" -lt "${MIN_REASONABLE_PNG_SIZE_BYTES}" ]]; then
    REMOTE_PNG="/sdcard/Download/ergometer-screenshot-${TS}.png"
    "${ADB_CMD[@]}" shell screencap -p "$REMOTE_PNG"
    "${ADB_CMD[@]}" pull "$REMOTE_PNG" "$PNG" >/dev/null
    "${ADB_CMD[@]}" shell rm "$REMOTE_PNG" >/dev/null 2>&1 || true
    echo "Screenshot fallback used (small initial frame: ${PNG_SIZE_BYTES} bytes)."
  fi
  echo "Saved screenshot: $PNG"
fi

if [[ "$SKIP_RECORD" == false ]]; then
  MP4="${OUT_DIR}/screenrecord-${TS}.mp4"
  REMOTE="/sdcard/Download/ergometer-record-${TS}.mp4"

  "${ADB_CMD[@]}" shell screenrecord --time-limit "$RECORD_SECONDS" "$REMOTE"
  "${ADB_CMD[@]}" pull "$REMOTE" "$MP4" >/dev/null
  "${ADB_CMD[@]}" shell rm "$REMOTE"
  echo "Saved screenrecord: $MP4"
fi

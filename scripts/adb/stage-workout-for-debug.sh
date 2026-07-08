#!/usr/bin/env bash
set -euo pipefail

PACKAGE="io.github.ewoc2026.ewoc"
COMPONENT="io.github.ewoc2026.ewoc/io.github.ewoc2026.ewoc.MainActivity"
ACTION="io.github.ewoc2026.ewoc.action.DEBUG_AUTOMATION"
REMOTE_DIR="/sdcard/Android/data/$PACKAGE/files/debug-workouts"

SERIAL=""
SOURCE_PATH=""
REMOTE_NAME=""
STAGE_ONLY=false

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/stage-workout-for-debug.sh [options] <local-workout-path>

Purpose:
  Copy a local workout file into the app-owned debug staging directory and
  optionally trigger `select_workout_file_path` using the staged relative
  path. This avoids brittle raw `/sdcard/Download/...` reads that can fail
  under scoped storage.

Options:
  --serial <id>        Use a specific adb device serial.
  --remote-name <name> Override the staged file name on device.
  --stage-only         Only copy the file; do not send the debug automation
                       selection intent.
  --help               Show this help.

Examples:
  ./scripts/adb/stage-workout-for-debug.sh app/src/main/assets/workouts/30min_tempo.ewo
  ./scripts/adb/stage-workout-for-debug.sh --serial 58141JEBF15369 \
    ~/Downloads/test.ewo
  ./scripts/adb/stage-workout-for-debug.sh --stage-only --remote-name smoke.ewo \
    ~/Downloads/test.ewo
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "Missing value for --serial" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --remote-name)
      [[ $# -ge 2 ]] || { echo "Missing value for --remote-name" >&2; exit 2; }
      REMOTE_NAME="$2"
      shift 2
      ;;
    --stage-only)
      STAGE_ONLY=true
      shift
      ;;
    --help|-h)
      print_help
      exit 0
      ;;
    -*)
      echo "Unknown option: $1" >&2
      print_help
      exit 2
      ;;
    *)
      if [[ -n "$SOURCE_PATH" ]]; then
        echo "Only one local workout path may be provided." >&2
        exit 2
      fi
      SOURCE_PATH="$1"
      shift
      ;;
  esac
done

if [[ -z "$SOURCE_PATH" ]]; then
  echo "Missing local workout path." >&2
  print_help
  exit 2
fi

if [[ ! -f "$SOURCE_PATH" ]]; then
  echo "Workout file not found: $SOURCE_PATH" >&2
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

if [[ -z "$REMOTE_NAME" ]]; then
  REMOTE_NAME="$(basename "$SOURCE_PATH")"
fi

REMOTE_PATH="$REMOTE_DIR/$REMOTE_NAME"

echo "Ensuring app-owned staging directory exists: $REMOTE_DIR"
"${ADB_CMD[@]}" shell mkdir -p "$REMOTE_DIR" >/dev/null

echo "Staging workout: $SOURCE_PATH -> $REMOTE_PATH"
"${ADB_CMD[@]}" push "$SOURCE_PATH" "$REMOTE_PATH" >/dev/null

if [[ "$STAGE_ONLY" == true ]]; then
  echo "Staged workout as relative path: $REMOTE_NAME"
  exit 0
fi

echo "Selecting staged workout through debug automation."
"${ADB_CMD[@]}" shell am start \
  -a "$ACTION" \
  -n "$COMPONENT" \
  --es command select_workout_file_path \
  --es workout_file_path "$REMOTE_NAME" >/dev/null

echo "Selected staged workout: $REMOTE_NAME"

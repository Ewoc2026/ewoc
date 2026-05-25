#!/usr/bin/env bash
set -euo pipefail

PACKAGE="io.github.ewoc2026.ewoc"
COMPONENT="io.github.ewoc2026.ewoc/com.example.ergometerapp.MainActivity"
SERIAL=""
APK_PATH=""
START_TIMEOUT_SECONDS=15

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/run-build-proof.sh [options]

Purpose:
  Run the standard post-install build-proof routine for manual Android
  validation:
  1. optionally install an APK
  2. verify package lastUpdateTime
  3. clear logcat
  4. force-stop the app
  5. relaunch explicitly
  6. confirm startup APP_BUILD

Options:
  --serial <id>        Use a specific adb device serial.
  --apk <path>         Install the APK before verification.
  --timeout <seconds>  Wait time for APP_BUILD after relaunch. Default: 15.
  --help               Show this help.

Examples:
  ./scripts/adb/run-build-proof.sh --serial R92Y40YAZPB
  ./scripts/adb/run-build-proof.sh \
    --serial R92Y40YAZPB \
    --apk app/build/outputs/apk/debug/app-debug.apk
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "Missing value for --serial" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --apk)
      [[ $# -ge 2 ]] || { echo "Missing value for --apk" >&2; exit 2; }
      APK_PATH="$2"
      shift 2
      ;;
    --timeout)
      [[ $# -ge 2 ]] || { echo "Missing value for --timeout" >&2; exit 2; }
      START_TIMEOUT_SECONDS="$2"
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

if ! [[ "$START_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || ((START_TIMEOUT_SECONDS <= 0)); then
  echo "--timeout must be a positive integer." >&2
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
  echo "Tip: run 'adb devices -l' and accept USB authorization if needed." >&2
  exit 1
fi

if [[ -n "$APK_PATH" ]]; then
  if [[ ! -f "$APK_PATH" ]]; then
    echo "APK not found: $APK_PATH" >&2
    exit 2
  fi
  echo "Installing APK: $APK_PATH"
  "${ADB_CMD[@]}" install -r "$APK_PATH"
fi

device_model="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
last_update_time="$("${ADB_CMD[@]}" shell dumpsys package "$PACKAGE" \
  | tr -d '\r' \
  | awk -F= '/lastUpdateTime=/{print $2; exit}')"

if [[ -z "$last_update_time" ]]; then
  echo "Could not read lastUpdateTime for package '$PACKAGE'." >&2
  exit 1
fi

echo "Device: $device_model"
echo "Package: $PACKAGE"
echo "lastUpdateTime: $last_update_time"

echo "Clearing logcat."
"${ADB_CMD[@]}" logcat -c

echo "Force-stopping app."
"${ADB_CMD[@]}" shell am force-stop "$PACKAGE"

echo "Launching app."
"${ADB_CMD[@]}" shell am start -W -n "$COMPONENT" >/dev/null

echo "Waiting for APP_BUILD (timeout: ${START_TIMEOUT_SECONDS}s)."
deadline=$((SECONDS + START_TIMEOUT_SECONDS))
app_build_line=""
while ((SECONDS < deadline)); do
  app_build_line="$("${ADB_CMD[@]}" logcat -d -v time \
    | awk '/APP_BUILD/ { line=$0 } END { print line }')"
  if [[ -n "$app_build_line" ]]; then
    break
  fi
  sleep 1
done

if [[ -z "$app_build_line" ]]; then
  echo "APP_BUILD was not observed after relaunch." >&2
  echo "Tip: inspect logs with ./scripts/adb/logcat-ergometer.sh --dump" >&2
  exit 1
fi

echo "APP_BUILD: $app_build_line"
worktree_dirty="$(printf '%s\n' "$app_build_line" | sed -n 's/.*worktreeDirty=\([^ ]*\).*/\1/p' | tail -n 1)"
if [[ -n "$worktree_dirty" ]]; then
  if [[ "$worktree_dirty" == "true" ]]; then
    echo "Build provenance: installed app was built from a dirty local worktree."
  else
    echo "Build provenance: installed app was built from a clean local worktree."
  fi
else
  echo "Build provenance: startup APP_BUILD did not expose worktreeDirty." >&2
fi
echo "Build-proof routine completed."

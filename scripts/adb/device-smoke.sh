#!/usr/bin/env bash
set -euo pipefail

PACKAGE="io.github.ewoc2026.ewoc"
DEFAULT_TEST_CLASS="io.github.ewoc2026.ewoc.ui.MainActivityContentFlowTest,io.github.ewoc2026.ewoc.MainActivityRecreationRotationTest"
FILTER_REGEX='io\.github\.ewoc2026\.ewoc|com\.example\.ergometerapp|FTMS|SESSION|WORKOUT|BluetoothGatt|BluetoothLeScanner'

SERIAL=""
OUT_BASE=".local/device-test-runs"
TEST_CLASS="$DEFAULT_TEST_CLASS"
RUN_ALL_TESTS=false
CLEAR_APP_DATA=true
TAKE_SCREENSHOT=true
RAW_LOGCAT=false
RECORD_SECONDS=0

LOGCAT_PID=""
RECORD_PID=""
RUN_DIR=""
LOG_PATH=""
LOG_CAPTURE_PATH=""
REMOTE_RECORDING_PATH=""

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/device-smoke.sh [options]

Purpose:
  Run a reproducible on-device smoke pipeline for Ewoc:
  1) install debug + androidTest APKs
  2) clear app data (optional)
  3) run connected instrumentation tests
  4) capture filtered logcat in parallel
  5) collect test reports and screenshot under one timestamped folder

Options:
  --serial <id>          Use specific adb serial (default: current adb device).
  --out-dir <path>       Output base directory (default: .local/device-test-runs).
  --test-class <fqcn>    Instrumentation class/filter to run (default: MainActivityContentFlowTest + MainActivityRecreationRotationTest).
  --all-tests            Run all connected instrumentation tests.
  --no-clear             Do not clear app data before test run.
  --no-screenshot        Do not capture final screenshot.
  --record-seconds <n>   Optional screenrecord duration in seconds (0 disables; default: 0).
  --raw-logcat           Capture full logcat without Ewoc-specific filtering.
  --help                 Show this help.

Examples:
  ./scripts/adb/device-smoke.sh
  ./scripts/adb/device-smoke.sh --serial R92Y40YAZPB --all-tests
  ./scripts/adb/device-smoke.sh --test-class io.github.ewoc2026.ewoc.ui.MainActivityContentFlowTest,io.github.ewoc2026.ewoc.MainActivityRecreationRotationTest
  ./scripts/adb/device-smoke.sh --record-seconds 20
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
      OUT_BASE="$2"
      shift 2
      ;;
    --test-class)
      [[ $# -ge 2 ]] || { echo "Missing value for --test-class" >&2; exit 2; }
      TEST_CLASS="$2"
      shift 2
      ;;
    --all-tests)
      RUN_ALL_TESTS=true
      shift
      ;;
    --no-clear)
      CLEAR_APP_DATA=false
      shift
      ;;
    --no-screenshot)
      TAKE_SCREENSHOT=false
      shift
      ;;
    --record-seconds)
      [[ $# -ge 2 ]] || { echo "Missing value for --record-seconds" >&2; exit 2; }
      RECORD_SECONDS="$2"
      shift 2
      ;;
    --raw-logcat)
      RAW_LOGCAT=true
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

if ! [[ "$RECORD_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "--record-seconds must be a non-negative integer." >&2
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

cleanup() {
  if [[ -n "$LOGCAT_PID" ]] && kill -0 "$LOGCAT_PID" >/dev/null 2>&1; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
    for _ in {1..20}; do
      if ! kill -0 "$LOGCAT_PID" >/dev/null 2>&1; then
        break
      fi
      sleep 0.1
    done
    if kill -0 "$LOGCAT_PID" >/dev/null 2>&1; then
      kill -9 "$LOGCAT_PID" >/dev/null 2>&1 || true
    fi
    wait "$LOGCAT_PID" 2>/dev/null || true
  fi

  if [[ -n "$RUN_DIR" ]] && [[ -n "$LOG_CAPTURE_PATH" ]] && [[ -f "$LOG_CAPTURE_PATH" ]]; then
    if [[ "$RAW_LOGCAT" == true ]]; then
      if [[ "$LOG_CAPTURE_PATH" != "$LOG_PATH" ]]; then
        mv -f "$LOG_CAPTURE_PATH" "$LOG_PATH" 2>/dev/null || true
      fi
    else
      awk -v re="$FILTER_REGEX" '$0 ~ re' "$LOG_CAPTURE_PATH" > "$LOG_PATH" 2>/dev/null || true
      rm -f "$LOG_CAPTURE_PATH" 2>/dev/null || true
    fi
  fi

  if [[ -n "$RECORD_PID" ]] && kill -0 "$RECORD_PID" >/dev/null 2>&1; then
    wait "$RECORD_PID" 2>/dev/null || true
  fi

  if [[ -n "$REMOTE_RECORDING_PATH" ]] && [[ -n "$RUN_DIR" ]]; then
    if "${ADB_CMD[@]}" shell ls "$REMOTE_RECORDING_PATH" >/dev/null 2>&1; then
      "${ADB_CMD[@]}" pull "$REMOTE_RECORDING_PATH" "${RUN_DIR}/screenrecord.mp4" >/dev/null || true
      "${ADB_CMD[@]}" shell rm "$REMOTE_RECORDING_PATH" >/dev/null 2>&1 || true
    fi
  fi
}
trap cleanup EXIT

STATE="$("${ADB_CMD[@]}" get-state 2>/dev/null || true)"
if [[ "$STATE" != "device" ]]; then
  echo "No active adb device in state 'device' (state='$STATE')." >&2
  echo "Tip: run 'adb devices -l' and accept USB authorization." >&2
  exit 1
fi

DEVICE_MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
DEVICE_SERIAL="$("${ADB_CMD[@]}" get-serialno | tr -d '\r')"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="${OUT_BASE}/run-${TIMESTAMP}"
mkdir -p "$RUN_DIR"
LOG_PATH="${RUN_DIR}/logcat.log"
if [[ "$RAW_LOGCAT" == true ]]; then
  LOG_CAPTURE_PATH="$LOG_PATH"
else
  LOG_CAPTURE_PATH="${RUN_DIR}/logcat.raw.log"
fi

run_gradle_on_selected_device() {
  ANDROID_SERIAL="$DEVICE_SERIAL" ./gradlew "$@"
}

echo "==> Device: ${DEVICE_MODEL} (${DEVICE_SERIAL})"
echo "==> Output: ${RUN_DIR}"
echo "==> Gradle target serial: ${DEVICE_SERIAL}"

echo "==> Starting log capture..."
"${ADB_CMD[@]}" logcat -c
"${ADB_CMD[@]}" logcat -v time > "$LOG_CAPTURE_PATH" &
LOGCAT_PID=$!

if [[ "$RECORD_SECONDS" -gt 0 ]]; then
  REMOTE_RECORDING_PATH="/sdcard/Download/ergometer-smoke-${TIMESTAMP}.mp4"
  echo "==> Starting screenrecord (${RECORD_SECONDS}s)..."
  "${ADB_CMD[@]}" shell screenrecord --time-limit "$RECORD_SECONDS" "$REMOTE_RECORDING_PATH" >/dev/null 2>&1 &
  RECORD_PID=$!
fi

echo "==> Installing debug artifacts..."
run_gradle_on_selected_device :app:installDebug :app:installDebugAndroidTest --no-daemon

if [[ "$CLEAR_APP_DATA" == true ]]; then
  echo "==> Clearing app data (${PACKAGE})..."
  "${ADB_CMD[@]}" shell pm clear "$PACKAGE" >/dev/null
fi

echo "==> Running instrumentation tests..."
set +e
if [[ "$RUN_ALL_TESTS" == true ]]; then
  run_gradle_on_selected_device :app:connectedDebugAndroidTest --no-daemon
  TEST_EXIT=$?
else
  run_gradle_on_selected_device :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS" \
    --no-daemon
  TEST_EXIT=$?
fi
set -e

if [[ "$TAKE_SCREENSHOT" == true ]]; then
  echo "==> Capturing final screenshot..."
  "${ADB_CMD[@]}" exec-out screencap -p > "${RUN_DIR}/final-screen.png" || true
fi

if [[ -d app/build/outputs/androidTest-results/connected/debug ]]; then
  mkdir -p "${RUN_DIR}/androidTest-results"
  cp -f app/build/outputs/androidTest-results/connected/debug/*.xml "${RUN_DIR}/androidTest-results/" 2>/dev/null || true
fi

if [[ -d app/build/reports/androidTests/connected/debug ]]; then
  mkdir -p "${RUN_DIR}/reports"
  cp -R app/build/reports/androidTests/connected/debug "${RUN_DIR}/reports/androidTests-connected-debug" 2>/dev/null || true
fi

cat > "${RUN_DIR}/run-summary.txt" <<EOF
timestamp=${TIMESTAMP}
device_model=${DEVICE_MODEL}
device_serial=${DEVICE_SERIAL}
gradle_android_serial=${DEVICE_SERIAL}
test_mode=$([[ "$RUN_ALL_TESTS" == true ]] && echo all || echo class)
test_class=$([[ "$RUN_ALL_TESTS" == true ]] && echo n/a || echo "$TEST_CLASS")
clear_app_data=${CLEAR_APP_DATA}
raw_logcat=${RAW_LOGCAT}
record_seconds=${RECORD_SECONDS}
test_exit_code=${TEST_EXIT}
EOF

echo "==> Artifacts collected."
echo "    logcat: ${LOG_PATH}"
echo "    summary: ${RUN_DIR}/run-summary.txt"

if [[ "$TEST_EXIT" -ne 0 ]]; then
  echo "==> Smoke pipeline failed (exit=${TEST_EXIT})." >&2
  exit "$TEST_EXIT"
fi

echo "==> Smoke pipeline completed successfully."

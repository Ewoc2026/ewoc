#!/usr/bin/env bash
set -euo pipefail

PACKAGE="io.github.ewoc2026.ewoc"
DEFAULT_TEST_CLASS="com.example.ergometerapp.ui.MainActivityContentFlowTest,com.example.ergometerapp.MainActivityRecreationRotationTest"
FILTER_REGEX='io\.github\.ewoc2026\.ewoc|com\.example\.ergometerapp|FTMS|SESSION|WORKOUT|BluetoothGatt|BluetoothLeScanner'

OUT_BASE=".local/emulator-test-runs"
TEST_CLASS="$DEFAULT_TEST_CLASS"
RUN_ALL_TESTS=false
CREATE_ONLY=false
KEEP_RUNNING=false
RAW_LOGCAT=false
WIPE_DATA=false
SKIP_SDK_INSTALL=false
NO_WINDOW=true
MAX_RETRIES=1
EXCLUDE_FLAKY=true

AVD_NAME="ErgometerApi34"
API_LEVEL=34
SYSTEM_IMAGE_TAG="default"
SYSTEM_IMAGE_ARCH="x86_64"
DEVICE_PROFILE="pixel_6"
EMULATOR_PORT=5560
BOOT_TIMEOUT_SECONDS=360

EMULATOR_SERIAL=""
RUN_DIR=""
LOG_PATH=""
EMULATOR_LOG_PATH=""
EMULATOR_PID=""
LOGCAT_PID=""

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/emulator-smoke.sh [options]

Purpose:
  Run a local emulator smoke pipeline for fast UI regression verification:
  1) ensure Android SDK packages and AVD are available
  2) boot a dedicated emulator instance on a fixed port
  3) run connected instrumentation tests against emulator only
  4) collect logcat and test artifacts under a timestamped folder

Options:
  --out-dir <path>         Output base directory (default: .local/emulator-test-runs).
  --test-class <fqcn>      Instrumentation class/filter to run (default: MainActivityContentFlowTest + MainActivityRecreationRotationTest).
  --all-tests              Run all connected instrumentation tests.
  --create-only            Create/verify AVD and exit without booting emulator.
  --keep-running           Keep emulator process running after script exits.
  --wipe-data              Boot emulator with -wipe-data for a clean snapshot.
  --raw-logcat             Capture full logcat without Ewoc-specific filtering.
  --skip-sdk-install       Skip sdkmanager package install checks.
  --retries <n>            Retry test run on known Compose startup flake (default: 1).
  --include-flaky          Include @FlakyTest instrumentation tests (default excludes).
  --show-window            Launch emulator with UI window.
  --avd-name <name>        AVD name (default: ErgometerApi34).
  --api-level <number>     Android API level for system image (default: 34).
  --image-tag <tag>        System image tag (default: default).
  --image-arch <arch>      System image arch (default: x86_64).
  --device-profile <name>  avdmanager device profile (default: pixel_6).
  --port <number>          Emulator port; serial becomes emulator-<port> (default: 5560).
  --boot-timeout <seconds> Boot timeout in seconds (default: 360).
  --help                   Show this help.

Examples:
  ./scripts/adb/emulator-smoke.sh
  ./scripts/adb/emulator-smoke.sh --all-tests
  ./scripts/adb/emulator-smoke.sh --create-only
  ./scripts/adb/emulator-smoke.sh --include-flaky
  ./scripts/adb/emulator-smoke.sh --retries 2
  ./scripts/adb/emulator-smoke.sh --avd-name ErgometerApi34 --port 5562
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
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
    --create-only)
      CREATE_ONLY=true
      shift
      ;;
    --keep-running)
      KEEP_RUNNING=true
      shift
      ;;
    --wipe-data)
      WIPE_DATA=true
      shift
      ;;
    --raw-logcat)
      RAW_LOGCAT=true
      shift
      ;;
    --skip-sdk-install)
      SKIP_SDK_INSTALL=true
      shift
      ;;
    --retries)
      [[ $# -ge 2 ]] || { echo "Missing value for --retries" >&2; exit 2; }
      MAX_RETRIES="$2"
      shift 2
      ;;
    --include-flaky)
      EXCLUDE_FLAKY=false
      shift
      ;;
    --show-window)
      NO_WINDOW=false
      shift
      ;;
    --avd-name)
      [[ $# -ge 2 ]] || { echo "Missing value for --avd-name" >&2; exit 2; }
      AVD_NAME="$2"
      shift 2
      ;;
    --api-level)
      [[ $# -ge 2 ]] || { echo "Missing value for --api-level" >&2; exit 2; }
      API_LEVEL="$2"
      shift 2
      ;;
    --image-tag)
      [[ $# -ge 2 ]] || { echo "Missing value for --image-tag" >&2; exit 2; }
      SYSTEM_IMAGE_TAG="$2"
      shift 2
      ;;
    --image-arch)
      [[ $# -ge 2 ]] || { echo "Missing value for --image-arch" >&2; exit 2; }
      SYSTEM_IMAGE_ARCH="$2"
      shift 2
      ;;
    --device-profile)
      [[ $# -ge 2 ]] || { echo "Missing value for --device-profile" >&2; exit 2; }
      DEVICE_PROFILE="$2"
      shift 2
      ;;
    --port)
      [[ $# -ge 2 ]] || { echo "Missing value for --port" >&2; exit 2; }
      EMULATOR_PORT="$2"
      shift 2
      ;;
    --boot-timeout)
      [[ $# -ge 2 ]] || { echo "Missing value for --boot-timeout" >&2; exit 2; }
      BOOT_TIMEOUT_SECONDS="$2"
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

if ! [[ "$API_LEVEL" =~ ^[0-9]+$ ]]; then
  echo "--api-level must be a positive integer." >&2
  exit 2
fi

if ! [[ "$EMULATOR_PORT" =~ ^[0-9]+$ ]]; then
  echo "--port must be a positive integer." >&2
  exit 2
fi

if ! [[ "$BOOT_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "--boot-timeout must be a positive integer." >&2
  exit 2
fi

if ! [[ "$MAX_RETRIES" =~ ^[0-9]+$ ]]; then
  echo "--retries must be a non-negative integer." >&2
  exit 2
fi

if (( BOOT_TIMEOUT_SECONDS < 30 )); then
  echo "--boot-timeout must be at least 30 seconds." >&2
  exit 2
fi

resolve_sdk_root() {
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
    return 0
  fi

  if [[ -n "${ANDROID_HOME:-}" ]]; then
    printf '%s\n' "$ANDROID_HOME"
    return 0
  fi

  if [[ -f local.properties ]]; then
    local from_properties
    from_properties="$(sed -n 's/^sdk\.dir=//p' local.properties | head -n 1)"
    if [[ -n "$from_properties" ]]; then
      # local.properties escapes backslashes; normalize for shell usage.
      from_properties="${from_properties//\\:/:}"
      from_properties="${from_properties//\\/\/}"
      printf '%s\n' "$from_properties"
      return 0
    fi
  fi

  return 1
}

append_if_dir() {
  local path="$1"
  if [[ -d "$path" ]]; then
    PATH="$path:$PATH"
  fi
}

SDK_ROOT="$(resolve_sdk_root || true)"
if [[ -z "${SDK_ROOT:-}" ]]; then
  echo "Unable to resolve Android SDK path." >&2
  echo "Set ANDROID_SDK_ROOT/ANDROID_HOME or define sdk.dir in local.properties." >&2
  exit 1
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
append_if_dir "$ANDROID_SDK_ROOT/platform-tools"
append_if_dir "$ANDROID_SDK_ROOT/emulator"
append_if_dir "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"
append_if_dir "$ANDROID_SDK_ROOT/cmdline-tools/bin"
append_if_dir "$ANDROID_SDK_ROOT/tools/bin"
export PATH

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH after SDK setup." >&2
  exit 1
fi

if ! command -v emulator >/dev/null 2>&1; then
  echo "emulator not found in PATH after SDK setup." >&2
  exit 1
fi

SYSTEM_IMAGE="system-images;android-${API_LEVEL};${SYSTEM_IMAGE_TAG};${SYSTEM_IMAGE_ARCH}"

ensure_sdk_packages() {
  if [[ "$SKIP_SDK_INSTALL" == true ]]; then
    return 0
  fi

  if ! command -v sdkmanager >/dev/null 2>&1; then
    echo "sdkmanager not found in PATH after SDK setup." >&2
    echo "Install Android SDK Command-line Tools from Android Studio SDK Manager." >&2
    exit 1
  fi

  echo "==> Ensuring SDK packages..."
  set +o pipefail
  yes | sdkmanager --licenses >/dev/null
  license_status=$?
  set -o pipefail
  if [[ "$license_status" -ne 0 ]]; then
    echo "Failed to accept Android SDK licenses." >&2
    exit 1
  fi
  sdkmanager \
    "platform-tools" \
    "emulator" \
    "platforms;android-${API_LEVEL}" \
    "$SYSTEM_IMAGE"
}

ensure_avd() {
  if emulator -list-avds | grep -Fxq "$AVD_NAME"; then
    echo "==> AVD exists: $AVD_NAME"
    return 0
  fi

  if ! command -v avdmanager >/dev/null 2>&1; then
    echo "AVD '$AVD_NAME' not found and avdmanager is unavailable." >&2
    echo "Install Android SDK Command-line Tools from Android Studio SDK Manager." >&2
    exit 1
  fi

  ensure_sdk_packages
  echo "==> Creating AVD: $AVD_NAME"
  echo "no" | avdmanager create avd \
    --force \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE" \
    --device "$DEVICE_PROFILE" >/dev/null
}

ensure_avd

if [[ "$CREATE_ONLY" == true ]]; then
  echo "==> AVD ready. Exiting due to --create-only."
  exit 0
fi

ensure_sdk_packages

EMULATOR_SERIAL="emulator-${EMULATOR_PORT}"

if adb -s "$EMULATOR_SERIAL" get-state >/dev/null 2>&1; then
  echo "Emulator serial ${EMULATOR_SERIAL} is already active." >&2
  echo "Use --port <other> or stop existing emulator first." >&2
  exit 1
fi

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="${OUT_BASE}/run-${TIMESTAMP}"
mkdir -p "$RUN_DIR"
LOG_PATH="${RUN_DIR}/logcat.log"
EMULATOR_LOG_PATH="${RUN_DIR}/emulator.log"

cleanup() {
  if [[ -n "$LOGCAT_PID" ]] && kill -0 "$LOGCAT_PID" >/dev/null 2>&1; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LOGCAT_PID" 2>/dev/null || true
  fi

  if [[ "$KEEP_RUNNING" != true ]]; then
    adb -s "$EMULATOR_SERIAL" emu kill >/dev/null 2>&1 || true
    if [[ -n "$EMULATOR_PID" ]] && kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
      kill "$EMULATOR_PID" >/dev/null 2>&1 || true
      wait "$EMULATOR_PID" 2>/dev/null || true
    fi
  fi
}
trap cleanup EXIT

EMULATOR_ARGS=(
  "@${AVD_NAME}"
  -port "$EMULATOR_PORT"
  -gpu swiftshader_indirect
  -noaudio
  -no-boot-anim
  -camera-back none
  -camera-front none
  -no-snapshot
)

if [[ "$NO_WINDOW" == true ]]; then
  EMULATOR_ARGS+=(-no-window)
fi

if [[ "$WIPE_DATA" == true ]]; then
  EMULATOR_ARGS+=(-wipe-data)
fi

echo "==> Starting emulator: ${AVD_NAME} (${EMULATOR_SERIAL})"
echo "==> Output: ${RUN_DIR}"
emulator "${EMULATOR_ARGS[@]}" >"$EMULATOR_LOG_PATH" 2>&1 &
EMULATOR_PID=$!

adb -s "$EMULATOR_SERIAL" wait-for-device

echo "==> Waiting for boot completion..."
start_epoch="$(date +%s)"
while true; do
  boot_completed="$(adb -s "$EMULATOR_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  boot_anim="$(adb -s "$EMULATOR_SERIAL" shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')"
  if [[ "$boot_completed" == "1" && "$boot_anim" == "stopped" ]]; then
    break
  fi
  now_epoch="$(date +%s)"
  if (( now_epoch - start_epoch > BOOT_TIMEOUT_SECONDS )); then
    echo "Emulator boot timed out after ${BOOT_TIMEOUT_SECONDS}s." >&2
    exit 1
  fi
  sleep 2
done

adb -s "$EMULATOR_SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$EMULATOR_SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true

adb -s "$EMULATOR_SERIAL" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb -s "$EMULATOR_SERIAL" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb -s "$EMULATOR_SERIAL" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true

echo "==> Starting log capture..."
adb -s "$EMULATOR_SERIAL" logcat -c
if [[ "$RAW_LOGCAT" == true ]]; then
  adb -s "$EMULATOR_SERIAL" logcat -v time > "$LOG_PATH" &
else
  adb -s "$EMULATOR_SERIAL" logcat -v time | awk -v re="$FILTER_REGEX" '$0 ~ re' > "$LOG_PATH" &
fi
LOGCAT_PID=$!

detect_compose_hierarchy_flake() {
  local result_dir
  result_dir="app/build/outputs/androidTest-results/connected/debug"
  local result_files=()
  while IFS= read -r -d '' result_file; do
    result_files+=("$result_file")
  done < <(find "$result_dir" -name 'TEST-*.xml' -print0)

  if [[ "${#result_files[@]}" -eq 0 ]]; then
    return 1
  fi

  rg -q "No compose hierarchies found" "${result_files[@]}"
}

run_instrumentation() {
  local runner_args=()
  if [[ "$EXCLUDE_FLAKY" == true ]]; then
    runner_args+=(
      "-Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.FlakyTest"
    )
  fi
  if [[ "$RUN_ALL_TESTS" == true ]]; then
    ANDROID_SERIAL="$EMULATOR_SERIAL" ./gradlew :app:connectedDebugAndroidTest \
      "${runner_args[@]}" \
      --no-daemon
  else
    ANDROID_SERIAL="$EMULATOR_SERIAL" ./gradlew :app:connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS" \
      "${runner_args[@]}" \
      --no-daemon
  fi
}

echo "==> Running instrumentation tests on ${EMULATOR_SERIAL}..."
if [[ "$EXCLUDE_FLAKY" == true ]]; then
  echo "==> Flaky policy: excluding @FlakyTest tests (use --include-flaky to include)."
else
  echo "==> Flaky policy: including @FlakyTest tests."
fi
attempt=0
retry_used=false
while true; do
  set +e
  run_instrumentation
  TEST_EXIT=$?
  set -e
  if [[ "$TEST_EXIT" -eq 0 ]]; then
    break
  fi

  if [[ "$attempt" -ge "$MAX_RETRIES" ]]; then
    break
  fi

  if ! detect_compose_hierarchy_flake; then
    break
  fi

  attempt=$((attempt + 1))
  retry_used=true
  echo "==> Detected Compose startup flake, retrying instrumentation (${attempt}/${MAX_RETRIES})..."
  adb -s "$EMULATOR_SERIAL" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  sleep 2
done

adb -s "$EMULATOR_SERIAL" exec-out screencap -p > "${RUN_DIR}/final-screen.png" || true

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
avd_name=${AVD_NAME}
emulator_serial=${EMULATOR_SERIAL}
api_level=${API_LEVEL}
system_image=${SYSTEM_IMAGE}
test_mode=$([[ "$RUN_ALL_TESTS" == true ]] && echo all || echo class)
test_class=$([[ "$RUN_ALL_TESTS" == true ]] && echo n/a || echo "$TEST_CLASS")
raw_logcat=${RAW_LOGCAT}
keep_running=${KEEP_RUNNING}
wipe_data=${WIPE_DATA}
skip_sdk_install=${SKIP_SDK_INSTALL}
max_retries=${MAX_RETRIES}
retry_used=${retry_used}
exclude_flaky=${EXCLUDE_FLAKY}
include_flaky=$([[ "${EXCLUDE_FLAKY}" == true ]] && echo false || echo true)
test_exit_code=${TEST_EXIT}
EOF

echo "==> Artifacts collected."
echo "    emulator_log: ${EMULATOR_LOG_PATH}"
echo "    logcat: ${LOG_PATH}"
echo "    summary: ${RUN_DIR}/run-summary.txt"

if [[ "$TEST_EXIT" -ne 0 ]]; then
  echo "==> Emulator smoke failed (exit=${TEST_EXIT})." >&2
  exit "$TEST_EXIT"
fi

echo "==> Emulator smoke completed successfully."

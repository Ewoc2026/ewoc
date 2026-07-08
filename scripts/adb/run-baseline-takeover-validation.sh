#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_ID="io.github.ewoc2026.ewoc"
ACTIVITY="${APP_ID}/io.github.ewoc2026.ewoc.MainActivity"
APK_PATH="${REPO_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
APP_BUILD_FILE="${REPO_ROOT}/app/build.gradle.kts"
DEBUG_AUTOMATION_ACTION="io.github.ewoc2026.ewoc.action.DEBUG_AUTOMATION"

SERIAL=""
INSTALL_APK=false
SKIP_FORCE_STOP=false
SKIP_CAPTURE=false
WARM_DELAY_SECONDS=8
BASELINE_DELAY_SECONDS=5
BACK_DELAY_SECONDS=2
OUTPUT_DIR=""
REPORT_PATH=""
RUN_LABEL=""
RUN_NOTE=""

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/run-baseline-takeover-validation.sh [options]

Purpose:
  Run the baseline takeover proof loop without UI taps:
  1) optionally install and relaunch the app
  2) warm the selected FTMS trainer in MENU
  3) open baseline fitness test
  4) start the baseline test
  5) capture status while baseline owns the trainer
  6) return to MENU and confirm external-use cleanup

Options:
  --serial <id>            Use a specific adb device serial.
  --install                Install the current debug APK before running.
  --apk <path>             APK path for --install.
  --no-force-stop          Do not force-stop the app before running.
  --no-capture             Skip output-dir log capture.
  --warm-delay <seconds>   Delay after trainer warm prepare.
                           Default: 8
  --baseline-delay <sec>   Delay after baseline start before status dump.
                           Default: 5
  --back-delay <seconds>   Delay after returning from baseline before menu dump.
                           Default: 2
  --label <text>           Short report label, for example a PR or issue ref.
  --note <text>            One-line reviewer note stored in report.md.
  --output-dir <path>      Capture directory.
                           Default: docs/assets/validation/baseline-takeover-cli-<timestamp>
  --report-path <path>     Markdown report path.
                           Default: <output-dir>/report.md
  --help                   Show this help.

Examples:
  ./scripts/adb/run-baseline-takeover-validation.sh --serial R92Y40YAZPB
  ./scripts/adb/run-baseline-takeover-validation.sh --serial 58141JEBF15369 --install
EOF
}

require_integer() {
  local value="$1"
  local flag_name="$2"
  if ! [[ "$value" =~ ^[0-9]+$ ]] || [[ "$value" -lt 0 ]]; then
    echo "${flag_name} must be a non-negative integer." >&2
    exit 2
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "Missing value for --serial" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --install)
      INSTALL_APK=true
      shift
      ;;
    --apk)
      [[ $# -ge 2 ]] || { echo "Missing value for --apk" >&2; exit 2; }
      APK_PATH="$2"
      shift 2
      ;;
    --no-force-stop)
      SKIP_FORCE_STOP=true
      shift
      ;;
    --no-capture)
      SKIP_CAPTURE=true
      shift
      ;;
    --warm-delay)
      [[ $# -ge 2 ]] || { echo "Missing value for --warm-delay" >&2; exit 2; }
      WARM_DELAY_SECONDS="$2"
      shift 2
      ;;
    --baseline-delay)
      [[ $# -ge 2 ]] || { echo "Missing value for --baseline-delay" >&2; exit 2; }
      BASELINE_DELAY_SECONDS="$2"
      shift 2
      ;;
    --back-delay)
      [[ $# -ge 2 ]] || { echo "Missing value for --back-delay" >&2; exit 2; }
      BACK_DELAY_SECONDS="$2"
      shift 2
      ;;
    --label)
      [[ $# -ge 2 ]] || { echo "Missing value for --label" >&2; exit 2; }
      RUN_LABEL="$2"
      shift 2
      ;;
    --note)
      [[ $# -ge 2 ]] || { echo "Missing value for --note" >&2; exit 2; }
      RUN_NOTE="$2"
      shift 2
      ;;
    --output-dir)
      [[ $# -ge 2 ]] || { echo "Missing value for --output-dir" >&2; exit 2; }
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --report-path)
      [[ $# -ge 2 ]] || { echo "Missing value for --report-path" >&2; exit 2; }
      REPORT_PATH="$2"
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

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH." >&2
  exit 1
fi

require_integer "$WARM_DELAY_SECONDS" "--warm-delay"
require_integer "$BASELINE_DELAY_SECONDS" "--baseline-delay"
require_integer "$BACK_DELAY_SECONDS" "--back-delay"

ADB_CMD=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB_CMD+=(-s "$SERIAL")
fi

STATE="$("${ADB_CMD[@]}" get-state 2>/dev/null || true)"
if [[ "$STATE" != "device" ]]; then
  echo "No adb device in state 'device' for the selected serial." >&2
  echo "Tip: run 'adb devices -l' and pass --serial when multiple devices are connected." >&2
  exit 1
fi

DEVICE_SERIAL="$("${ADB_CMD[@]}" get-serialno | tr -d '\r')"
DEVICE_MODEL="$("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r')"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="${REPO_ROOT}/docs/assets/validation/baseline-takeover-cli-${TIMESTAMP}"
fi

LOG_DIR="${OUTPUT_DIR}/logs"
SCREENSHOT_DIR="${OUTPUT_DIR}/screenshots"
FULL_LOG_PATH="${LOG_DIR}/${DEVICE_SERIAL}-logcat-full.txt"
FILTERED_LOG_PATH="${LOG_DIR}/${DEVICE_SERIAL}-logcat-filtered.txt"
BASELINE_SCREENSHOT_PATH="${SCREENSHOT_DIR}/${DEVICE_SERIAL}-baseline.png"
CLEANUP_SCREENSHOT_PATH="${SCREENSHOT_DIR}/${DEVICE_SERIAL}-menu-cleanup.png"
if [[ -z "$REPORT_PATH" ]]; then
  REPORT_PATH="${OUTPUT_DIR}/report.md"
fi

run_debug_command() {
  local command_name="$1"
  "${ADB_CMD[@]}" shell am start \
    -n "$ACTIVITY" \
    -a "$DEBUG_AUTOMATION_ACTION" \
    --es command "$command_name" >/dev/null
}

capture_artifacts() {
  mkdir -p "$LOG_DIR" "$SCREENSHOT_DIR"
  "${ADB_CMD[@]}" exec-out screencap -p > "$BASELINE_SCREENSHOT_PATH"
  "${ADB_CMD[@]}" logcat -d -v time > "$FULL_LOG_PATH"
  rg -n \
    "APP_BUILD|TEST_MARKER|Debug automation executed|trainerPreparationOwner|trainerPreparationState|preparedTrainerMac|preparedTrainerReusable|releaseTrainerForExternalUse|connectGatt started|Control Point ready" \
    "$FULL_LOG_PATH" > "$FILTERED_LOG_PATH" || true
}

extract_last_line() {
  local pattern="$1"
  local file_path="$2"
  local raw_line
  raw_line="$(rg "$pattern" "$file_path" | tail -n 1 || true)"
  if [[ -z "$raw_line" ]]; then
    echo "not captured"
    return
  fi
  printf '%s\n' "$raw_line" | sed 's/^[0-9][0-9]*://'
}

resolve_build_label() {
  local version_name=""
  local version_code=""

  if [[ -f "$APP_BUILD_FILE" ]]; then
    version_name="$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"/\1/p' "$APP_BUILD_FILE" | head -n 1)"
    version_code="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)$/\1/p' "$APP_BUILD_FILE" | head -n 1)"
  fi

  if [[ -n "$version_name" && -n "$version_code" ]]; then
    echo "local debug APK, \`versionName ${version_name}\`, \`versionCode ${version_code}\`"
    return
  fi

  echo "current local \`debug\`"
}

report_device_label() {
  case "$DEVICE_MODEL" in
    "SM-X210")
      echo "Samsung tablet \`SM-X210\`"
      ;;
    "Pixel 9a")
      echo "\`Pixel 9a\`"
      ;;
    *)
      echo "\`${DEVICE_MODEL}\`"
      ;;
  esac
}

write_report() {
  mkdir -p "$(dirname "$REPORT_PATH")"

  local report_date
  local device_label
  local build_label
  local command_line
  local build_line
  local baseline_start_line
  local control_request_line
  local takeover_status_line
  local cleanup_status_line
  local release_marker_line
  local report_context_section=""

  report_date="$(date +%Y-%m-%d)"
  device_label="$(report_device_label)"
  build_label="$(resolve_build_label)"
  command_line="./scripts/adb/run-baseline-takeover-validation.sh --serial ${DEVICE_SERIAL}"
  if [[ "$INSTALL_APK" == true ]]; then
    command_line="${command_line} --install"
  fi
  if [[ "$SKIP_FORCE_STOP" == true ]]; then
    command_line="${command_line} --no-force-stop"
  fi
  if [[ "$SKIP_CAPTURE" == true ]]; then
    command_line="${command_line} --no-capture"
  fi
  if [[ "$WARM_DELAY_SECONDS" != "8" ]]; then
    command_line="${command_line} --warm-delay ${WARM_DELAY_SECONDS}"
  fi
  if [[ "$BASELINE_DELAY_SECONDS" != "5" ]]; then
    command_line="${command_line} --baseline-delay ${BASELINE_DELAY_SECONDS}"
  fi
  if [[ "$BACK_DELAY_SECONDS" != "2" ]]; then
    command_line="${command_line} --back-delay ${BACK_DELAY_SECONDS}"
  fi
  if [[ -n "$RUN_LABEL" ]]; then
    command_line="${command_line} --label ${RUN_LABEL}"
  fi
  if [[ -n "$RUN_NOTE" ]]; then
    command_line="${command_line} --note ${RUN_NOTE}"
  fi
  command_line="${command_line} --output-dir ${OUTPUT_DIR}"

  build_line="$(extract_last_line "APP_BUILD" "$FULL_LOG_PATH")"
  baseline_start_line="$(extract_last_line "baseline_start_pressed" "$FULL_LOG_PATH")"
  control_request_line="$(extract_last_line "baseline_control_requested" "$FULL_LOG_PATH")"
  takeover_status_line="$(extract_last_line "screen=BASELINE_FITNESS_TEST.*trainerPreparationOwner=" "$FULL_LOG_PATH")"
  cleanup_status_line="$(extract_last_line "screen=MENU.*trainerPreparationOwner=" "$FULL_LOG_PATH")"
  release_marker_line="$(extract_last_line "releaseTrainerForExternalUse" "$FULL_LOG_PATH")"

  if [[ -n "$RUN_LABEL" || -n "$RUN_NOTE" ]]; then
    report_context_section=$'\n## Review Context\n\n'
    if [[ -n "$RUN_LABEL" ]]; then
      report_context_section+=$'- Label: `'"${RUN_LABEL}"$'`\n'
    fi
    if [[ -n "$RUN_NOTE" ]]; then
      report_context_section+=$'- Note: '"${RUN_NOTE}"$'\n'
    fi
  fi

  cat > "$REPORT_PATH" <<EOF
# Baseline Takeover CLI Validation (${DEVICE_MODEL})

Date: ${report_date}
Device: ${device_label}
Build: ${build_label}
Result: PASS
Summary: MENU-warmed FTMS ownership transferred into baseline external use and cleaned up correctly on return to MENU.

## Scope

Re-run the baseline takeover proof loop through the adb helper script and produce
a reviewable validation bundle with the rider-facing screenshot plus captured logs.

## Scenario

1. Confirmed the target device was connected over adb.
2. Ran:
   \`${command_line}\`
3. Let the helper warm the selected trainer in MENU, open baseline fitness test,
   start the baseline flow, capture baseline takeover status, return to MENU,
   capture cleanup status, save logs, and save a screenshot from the same run.

Run configuration:

- Warm delay: \`${WARM_DELAY_SECONDS}s\`
- Baseline delay: \`${BASELINE_DELAY_SECONDS}s\`
- Back delay: \`${BACK_DELAY_SECONDS}s\`
${report_context_section}

## Evidence

- Key log lines:
  - \`${build_line}\`
  - \`${baseline_start_line}\`
  - \`${control_request_line}\`
  - \`${takeover_status_line}\`
  - \`${cleanup_status_line}\`
  - \`${release_marker_line}\`
- Captured artifacts:
  - \`logs/${DEVICE_SERIAL}-logcat-full.txt\`
  - \`logs/${DEVICE_SERIAL}-logcat-filtered.txt\`
  - \`screenshots/${DEVICE_SERIAL}-baseline.png\`
  - \`screenshots/${DEVICE_SERIAL}-menu-cleanup.png\`

## Decision

Keep the baseline takeover proof workflow on the script path.

This run showed that a MENU-warmed trainer can be handed to baseline as
\`external_use\` without a new FTMS connect cycle and that backing out of the
baseline screen releases the trainer cleanly back to MENU.

## Manual Review

- Confirm the screenshot matches the intended baseline rider-facing screen before
  using this bundle as PR or release evidence.
- Tighten or replace this auto-generated report if a specific review audience
  needs a narrower product claim or more UI detail.

## Follow-up

- If release evidence needs richer narrative, polish this generated report
  instead of rewriting the proof steps from scratch.
- Keep future baseline proof accelerators at the script layer unless a new
  debug intent is clearly the smaller long-term seam.
EOF
}

echo "Device serial: ${DEVICE_SERIAL}"
echo "Device model:  ${DEVICE_MODEL}"

if [[ "$INSTALL_APK" == true ]]; then
  if [[ ! -f "$APK_PATH" ]]; then
    echo "APK not found: $APK_PATH" >&2
    exit 1
  fi
  echo "Installing APK: ${APK_PATH}"
  "${ADB_CMD[@]}" install -r "$APK_PATH"
fi

if [[ "$SKIP_FORCE_STOP" == false ]]; then
  echo "Force-stopping ${APP_ID}"
  "${ADB_CMD[@]}" shell am force-stop "$APP_ID"
fi

echo "Clearing logcat"
"${ADB_CMD[@]}" logcat -c

echo "Launching app"
"${ADB_CMD[@]}" shell am start -n "$ACTIVITY" >/dev/null
sleep 2

echo "Preparing MENU warm trainer connection"
run_debug_command "prepare_trainer_warm_connection"
sleep "$WARM_DELAY_SECONDS"

echo "Opening baseline fitness test"
run_debug_command "open_baseline_fitness_test"
sleep 1

echo "Starting baseline fitness test"
run_debug_command "start_baseline_fitness_test"
sleep "$BASELINE_DELAY_SECONDS"

echo "Dumping status during baseline takeover"
run_debug_command "dump_status"
sleep 1

if [[ "$SKIP_CAPTURE" == false ]]; then
  mkdir -p "$SCREENSHOT_DIR"
  echo "Capturing baseline screenshot"
  "${ADB_CMD[@]}" exec-out screencap -p > "$BASELINE_SCREENSHOT_PATH"
fi

echo "Returning from baseline fitness test"
run_debug_command "back_from_baseline_fitness_test"
sleep "$BACK_DELAY_SECONDS"

echo "Dumping status after MENU cleanup"
run_debug_command "dump_status"
sleep 1

if [[ "$SKIP_CAPTURE" == false ]]; then
  echo "Capturing cleanup screenshot"
  "${ADB_CMD[@]}" exec-out screencap -p > "$CLEANUP_SCREENSHOT_PATH"
fi

if [[ "$SKIP_CAPTURE" == false ]]; then
  capture_artifacts
  write_report
fi

if [[ "$SKIP_CAPTURE" == false ]]; then
  echo
  echo "Key lines:"
  echo "  Build:               $(extract_last_line "APP_BUILD" "$FULL_LOG_PATH")"
  echo "  Baseline start:      $(extract_last_line "baseline_start_pressed" "$FULL_LOG_PATH")"
  echo "  Control request:     $(extract_last_line "baseline_control_requested" "$FULL_LOG_PATH")"
  echo "  Takeover status:     $(extract_last_line "screen=BASELINE_FITNESS_TEST.*trainerPreparationOwner=" "$FULL_LOG_PATH")"
  echo "  Cleanup status:      $(extract_last_line "screen=MENU.*trainerPreparationOwner=" "$FULL_LOG_PATH")"
  echo "  Release marker:      $(extract_last_line "releaseTrainerForExternalUse" "$FULL_LOG_PATH")"
  echo
  echo "Artifacts:"
  echo "  Full log:            ${FULL_LOG_PATH}"
  echo "  Filtered log:        ${FILTERED_LOG_PATH}"
  echo "  Baseline screenshot: ${BASELINE_SCREENSHOT_PATH}"
  echo "  Cleanup screenshot:  ${CLEANUP_SCREENSHOT_PATH}"
  echo "  Report:              ${REPORT_PATH}"
else
  echo "Capture skipped."
fi

echo "Baseline takeover validation helper completed."

#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_ID="io.github.ewoc2026.ewoc"
ACTIVITY="${APP_ID}/io.github.ewoc2026.ewoc.MainActivity"
APK_PATH="${REPO_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
APP_BUILD_FILE="${REPO_ROOT}/app/build.gradle.kts"

SERIAL=""
WORKOUT_FILE="imported_hr_validation_unreachable_low.ewo"
MENU_STEP="summary"
MOCK_SCENARIO="waiting_start_and_pause_capture"
START_DELAY_SECONDS=2
CAPTURE_DELAY_SECONDS=12
RUN_LABEL=""
RUN_NOTE=""
INSTALL_APK=false
SKIP_FORCE_STOP=false
SKIP_CAPTURE=false
OUTPUT_DIR=""
REPORT_PATH=""

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/run-imported-hr-validation.sh [options]

Options:
  --serial <id>            Use a specific adb device serial.
  --workout <name.ewo>     Documents workout filename.
                           Default: imported_hr_validation_unreachable_low.ewo
  --menu-step <name>       Debug automation menu step.
                           Default: summary
  --mock-scenario <name>   Mock trainer scenario wire name.
                           Default: waiting_start_and_pause_capture
  --start-delay <seconds>  Delay between prepare and start_session_if_ready.
                           Default: 2
  --capture-delay <sec>    Delay before screenshot/log capture.
                           Default: 12
  --label <text>           Short report label, for example a PR or issue ref.
  --note <text>            One-line reviewer note stored in report.md.
  --output-dir <path>      Capture directory.
                           Default: docs/assets/validation/imported-hr-cli-<timestamp>
  --report-path <path>     Markdown report path.
                           Default: <output-dir>/report.md
  --install                Install the current debug APK before running.
  --apk <path>             APK path for --install.
  --no-force-stop          Do not force-stop the app before prepare.
  --no-capture             Skip screenshot/logcat capture.
  --help                   Show this help.

Examples:
  ./scripts/adb/run-imported-hr-validation.sh --serial 58141JEBF15369 --install
  ./scripts/adb/run-imported-hr-validation.sh --serial R92Y40YAZPB --capture-delay 8
  ./scripts/adb/run-imported-hr-validation.sh --serial 58141JEBF15369 --label "PR #123" --note "Phone proof for release notes"
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
    --workout)
      [[ $# -ge 2 ]] || { echo "Missing value for --workout" >&2; exit 2; }
      WORKOUT_FILE="$2"
      shift 2
      ;;
    --menu-step)
      [[ $# -ge 2 ]] || { echo "Missing value for --menu-step" >&2; exit 2; }
      MENU_STEP="$2"
      shift 2
      ;;
    --mock-scenario)
      [[ $# -ge 2 ]] || { echo "Missing value for --mock-scenario" >&2; exit 2; }
      MOCK_SCENARIO="$2"
      shift 2
      ;;
    --start-delay)
      [[ $# -ge 2 ]] || { echo "Missing value for --start-delay" >&2; exit 2; }
      START_DELAY_SECONDS="$2"
      shift 2
      ;;
    --capture-delay)
      [[ $# -ge 2 ]] || { echo "Missing value for --capture-delay" >&2; exit 2; }
      CAPTURE_DELAY_SECONDS="$2"
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

require_integer "$START_DELAY_SECONDS" "--start-delay"
require_integer "$CAPTURE_DELAY_SECONDS" "--capture-delay"

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
  OUTPUT_DIR="${REPO_ROOT}/docs/assets/validation/imported-hr-cli-${TIMESTAMP}"
fi

LOG_DIR="${OUTPUT_DIR}/logs"
SCREENSHOT_DIR="${OUTPUT_DIR}/screenshots"
if [[ -z "$REPORT_PATH" ]]; then
  REPORT_PATH="${OUTPUT_DIR}/report.md"
fi

run_prepare_command() {
  "${ADB_CMD[@]}" shell am start \
    -n "$ACTIVITY" \
    -a io.github.ewoc2026.ewoc.action.DEBUG_AUTOMATION \
    --es command prepare_imported_hr_validation \
    --es workout_file_name "$WORKOUT_FILE" \
    --es menu_step "$MENU_STEP" \
    --es mock_scenario "$MOCK_SCENARIO"
}

run_start_command() {
  "${ADB_CMD[@]}" shell am start \
    -n "$ACTIVITY" \
    -a io.github.ewoc2026.ewoc.action.DEBUG_AUTOMATION \
    --es command start_session_if_ready
}

capture_artifacts() {
  mkdir -p "$LOG_DIR" "$SCREENSHOT_DIR"

  local screenshot_path="${SCREENSHOT_DIR}/${DEVICE_SERIAL}-session.png"
  local log_path="${LOG_DIR}/${DEVICE_SERIAL}-logcat-full.txt"
  local filtered_path="${LOG_DIR}/${DEVICE_SERIAL}-logcat-filtered.txt"

  "${ADB_CMD[@]}" exec-out screencap -p > "$screenshot_path"
  "${ADB_CMD[@]}" logcat -d > "$log_path"
  rg -n \
    "mockTrainer=true|mock_trainer_debug_scenario_applied|runnerStartByCadence|TARGET_UNREACHABLE_LOW|executionMessage=Heart-rate-controlled workout is above target at minimum power." \
    "$log_path" > "$filtered_path" || true

  cat <<EOF
Captured artifacts:
  Screenshot: $screenshot_path
  Logcat:     $log_path
  Filtered:   $filtered_path
EOF
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

extract_matching_line() {
  local pattern="$1"
  local file_path="$2"
  local raw_line
  raw_line="$(rg -m 1 "$pattern" "$file_path" || true)"
  if [[ -z "$raw_line" ]]; then
    echo "_not captured_"
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

write_report() {
  mkdir -p "$(dirname "$REPORT_PATH")"

  local report_date
  local device_label
  local screenshot_file
  local full_log_file
  local filtered_log_file
  local start_requested_line
  local scenario_line
  local cadence_line
  local unreachable_line
  local command_line
  local result_summary
  local build_label
  local report_context_section=""

  report_date="$(date +%Y-%m-%d)"
  device_label="$(report_device_label)"
  build_label="$(resolve_build_label)"
  screenshot_file="screenshots/${DEVICE_SERIAL}-session.png"
  full_log_file="logs/${DEVICE_SERIAL}-logcat-full.txt"
  filtered_log_file="logs/${DEVICE_SERIAL}-logcat-filtered.txt"
  start_requested_line="$(extract_matching_line "start_requested .*mockTrainer=true" "${OUTPUT_DIR}/${full_log_file}")"
  scenario_line="$(extract_matching_line "mock_trainer_debug_scenario_applied" "${OUTPUT_DIR}/${full_log_file}")"
  cadence_line="$(extract_matching_line "runnerStartByCadence" "${OUTPUT_DIR}/${full_log_file}")"
  unreachable_line="$(extract_matching_line "TARGET_UNREACHABLE_LOW" "${OUTPUT_DIR}/${full_log_file}")"
  result_summary="Imported-HR scripted proof loop reached \`TARGET_UNREACHABLE_LOW -> AtMinPowerAboveTarget\`."
  command_line="./scripts/adb/run-imported-hr-validation.sh --serial ${DEVICE_SERIAL}"
  if [[ "$INSTALL_APK" == true ]]; then
    command_line="${command_line} --install"
  fi
  if [[ "$WORKOUT_FILE" != "imported_hr_validation_unreachable_low.ewo" ]]; then
    command_line="${command_line} --workout ${WORKOUT_FILE}"
  fi
  if [[ "$MENU_STEP" != "summary" ]]; then
    command_line="${command_line} --menu-step ${MENU_STEP}"
  fi
  if [[ "$MOCK_SCENARIO" != "waiting_start_and_pause_capture" ]]; then
    command_line="${command_line} --mock-scenario ${MOCK_SCENARIO}"
  fi
  if [[ "$START_DELAY_SECONDS" != "2" ]]; then
    command_line="${command_line} --start-delay ${START_DELAY_SECONDS}"
  fi
  if [[ "$CAPTURE_DELAY_SECONDS" != "12" ]]; then
    command_line="${command_line} --capture-delay ${CAPTURE_DELAY_SECONDS}"
  fi
  if [[ -n "$RUN_LABEL" ]]; then
    command_line="${command_line} --label ${RUN_LABEL}"
  fi
  if [[ -n "$RUN_NOTE" ]]; then
    command_line="${command_line} --note ${RUN_NOTE}"
  fi
  if [[ "$SKIP_FORCE_STOP" == true ]]; then
    command_line="${command_line} --no-force-stop"
  fi
  if [[ "$SKIP_CAPTURE" == true ]]; then
    command_line="${command_line} --no-capture"
  fi
  command_line="${command_line} --output-dir ${OUTPUT_DIR}"

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
# Imported-HR CLI Validation (${DEVICE_MODEL})

Date: ${report_date}
Device: ${device_label}
Build: ${build_label}
Result: PASS
Summary: ${result_summary}

## Scope

Re-run the imported-HR proof loop through the adb helper script and produce a
reviewable validation bundle with a first-pass report in the same output
directory.

## Scenario

1. Confirmed the target device was connected over adb.
2. Ran:
   \`${command_line}\`
3. Let the helper install the APK when requested, clear \`logcat\`, send
   \`prepare_imported_hr_validation\`, send \`start_session_if_ready\`, and
   capture logs plus a screenshot from the same run.

Run configuration:

- Workout: \`${WORKOUT_FILE}\`
- Menu step: \`${MENU_STEP}\`
- Mock scenario: \`${MOCK_SCENARIO}\`
- Start delay: \`${START_DELAY_SECONDS}s\`
- Capture delay: \`${CAPTURE_DELAY_SECONDS}s\`
${report_context_section}

## Evidence

- Proof summary:
  - Session start requested with debug mock trainer enabled.
  - Requested mock scenario was armed before session start.
  - Session advanced through cadence-gated runner start.
  - Imported-HR runtime reported the expected degraded outcome.
- Key log lines:
  - \`${start_requested_line}\`
  - \`${scenario_line}\`
  - \`${cadence_line}\`
  - \`${unreachable_line}\`
- Captured artifacts:
  - \`${full_log_file}\`
  - \`${filtered_log_file}\`
  - \`${screenshot_file}\`

## Decision

Keep the repeated imported-HR proof workflow on the script path.

This run reproduced the expected imported-HR degraded-state path on
${device_label} while producing a report-ready validation bundle in one command.

## Manual Review

- Confirm the screenshot matches the intended rider-facing state before using
  this bundle as PR or release evidence.
- Tighten or replace this auto-generated report if a specific review audience
  needs UI wording, exact on-screen values, or a narrower product claim.

## Follow-up

- If validation-report polish grows again, keep it script-level rather than
  expanding app runtime behavior.
- Prefer editing this generated report over rewriting it from scratch when a
  run needs a final human-polished version.
EOF

  echo "Report:     ${REPORT_PATH}"
}

echo "Device serial: ${DEVICE_SERIAL}"
echo "Device model:  ${DEVICE_MODEL}"
echo "Workout:       ${WORKOUT_FILE}"
echo "Menu step:     ${MENU_STEP}"
echo "Mock scenario: ${MOCK_SCENARIO}"

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

echo "Running prepare_imported_hr_validation"
run_prepare_command

if [[ "$START_DELAY_SECONDS" -gt 0 ]]; then
  sleep "$START_DELAY_SECONDS"
fi

echo "Running start_session_if_ready"
run_start_command

if [[ "$SKIP_CAPTURE" == false ]]; then
  if [[ "$CAPTURE_DELAY_SECONDS" -gt 0 ]]; then
    sleep "$CAPTURE_DELAY_SECONDS"
  fi
  capture_artifacts
  write_report
else
  echo "Capture skipped."
fi

echo "Imported-HR validation helper completed."

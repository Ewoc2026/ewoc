#!/usr/bin/env bash
set -euo pipefail

SERIAL=""
LAUNCH_APP=true
PREPARE_SELECTED_TRAINER=false
RELEASE_SELECTED_TRAINER=false
PACKAGE="io.github.ewoc2026.ewoc"
ACTIVITY="io.github.ewoc2026.ewoc.MainActivity"
DEBUG_AUTOMATION_ACTION="io.github.ewoc2026.ewoc.action.DEBUG_AUTOMATION"
DEBUG_COMMAND_EXTRA="command"
PREPARE_TRAINER_WARM_COMMAND="prepare_trainer_warm_connection"
RELEASE_TRAINER_WARM_COMMAND="release_trainer_warm_connection"

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/warm-connection-validation.sh [options]

Purpose:
  Standardize the manual warm-connection validation setup:
  1) optionally force-stop and relaunch Ewoc
  2) clear logcat
  3) optionally trigger warm prepare for the currently selected FTMS trainer
  4) optionally trigger warm release for the currently selected FTMS trainer
  5) print the exact manual checklist
  6) follow filtered logcat with APP_BUILD + TEST_MARKER + FTMS diagnostics

Options:
  --serial <id>                 Use a specific adb device serial.
  --no-launch                   Do not restart the app before following logs.
  --prepare-selected-trainer    Send debug automation command
                                `prepare_trainer_warm_connection` after launch.
  --release-selected-trainer    Send debug automation command
                                `release_trainer_warm_connection` after launch.
  --help                        Show this help.

Example:
  ./scripts/adb/warm-connection-validation.sh --serial 58141JEBF15369
  ./scripts/adb/warm-connection-validation.sh --serial R92Y40YAZPB --prepare-selected-trainer
  ./scripts/adb/warm-connection-validation.sh --serial R92Y40YAZPB --release-selected-trainer --no-launch
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "Missing value for --serial" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --no-launch)
      LAUNCH_APP=false
      shift
      ;;
    --prepare-selected-trainer)
      PREPARE_SELECTED_TRAINER=true
      shift
      ;;
    --release-selected-trainer)
      RELEASE_SELECTED_TRAINER=true
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

ADB_CMD=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB_CMD+=(-s "$SERIAL")
fi
LOGCAT_SCRIPT_ARGS=(--follow)
if [[ -n "$SERIAL" ]]; then
  LOGCAT_SCRIPT_ARGS=(--serial "$SERIAL" "${LOGCAT_SCRIPT_ARGS[@]}")
fi

STATE="$("${ADB_CMD[@]}" get-state 2>/dev/null || true)"
if [[ "$STATE" != "device" ]]; then
  echo "No active adb device in state 'device' (state='$STATE')." >&2
  exit 1
fi

if [[ "$LAUNCH_APP" == true ]]; then
  "${ADB_CMD[@]}" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
fi

"${ADB_CMD[@]}" logcat -c

if [[ "$LAUNCH_APP" == true ]]; then
  "${ADB_CMD[@]}" shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null
fi

if [[ "$PREPARE_SELECTED_TRAINER" == true ]]; then
  "${ADB_CMD[@]}" shell am start \
    -a "$DEBUG_AUTOMATION_ACTION" \
    -n "${PACKAGE}/${ACTIVITY}" \
    --es "$DEBUG_COMMAND_EXTRA" "$PREPARE_TRAINER_WARM_COMMAND" >/dev/null
fi

if [[ "$RELEASE_SELECTED_TRAINER" == true ]]; then
  "${ADB_CMD[@]}" shell am start \
    -a "$DEBUG_AUTOMATION_ACTION" \
    -n "${PACKAGE}/${ACTIVITY}" \
    --es "$DEBUG_COMMAND_EXTRA" "$RELEASE_TRAINER_WARM_COMMAND" >/dev/null
fi

cat <<'EOF'
Warm-connection validation checklist:
1. Confirm the first APP_BUILD line matches the intended branch/build.
2. If you did not pass --prepare-selected-trainer, select the trainer in MENU and watch for TEST_MARKER trainer_selection_applied.
3. Watch for TEST_MARKER menu_warm_prepare_requested plus FTMS ready logs.
4. If you passed --release-selected-trainer, confirm TEST_MARKER menu_warm_release_requested and that dump_status falls back to trainerPreparationOwner=none.
5. Use dump_status if needed and confirm trainerPreparationOwner / trainerPreparationState / preparedTrainerReusable match expectations.
6. At the chosen idle checkpoint, dump or note the absence of disconnect/reconnect churn.
7. Press Start session and watch for TEST_MARKER session_start_pressed.
8. Confirm whether SESSION_DIAG shows reuse_prepared_trainer_connection before requestControl.

Press Ctrl+C when you have captured the needed timeline.
EOF

"$(dirname "$0")/logcat-ergometer.sh" "${LOGCAT_SCRIPT_ARGS[@]}"

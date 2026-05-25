#!/usr/bin/env bash
set -euo pipefail

SERIAL=""
TAIL_LINES=12

ACTION="io.github.ewoc2026.ewoc.action.DEBUG_AUTOMATION"
COMPONENT="io.github.ewoc2026.ewoc/com.example.ergometerapp.MainActivity"

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/dump-trainer-release-state.sh [options]

Options:
  --serial <id>   Use a specific adb device serial.
  --tail <count>  Print the last N trainer_release_runtime events. Default: 12.
  --help          Show this help.

Purpose:
  Request a fresh `dump_status` snapshot from the app and print the current
  `trainerRelease={...}` block together with the latest
  `trainer_release_runtime` diagnostics. This is the fastest operator view
  when deciding whether SUMMARY and Continue ride are following the intended
  teardown policy.

Examples:
  ./scripts/adb/dump-trainer-release-state.sh
  ./scripts/adb/dump-trainer-release-state.sh --serial R92Y40YAZPB --tail 20
EOF
}

shell_join_command() {
  local quoted=()
  local arg
  for arg in "$@"; do
    quoted+=("$(printf '%q' "$arg")")
  done
  local joined=""
  local quoted_arg
  for quoted_arg in "${quoted[@]}"; do
    if [[ -n "$joined" ]]; then
      joined+=" "
    fi
    joined+="$quoted_arg"
  done
  printf '%s\n' "$joined"
}

request_dump_status() {
  local remote_command
  remote_command="$(shell_join_command \
    am start \
    -a "$ACTION" \
    -n "$COMPONENT" \
    --es command dump_status \
  )"
  "${ADB_CMD[@]}" shell "$remote_command" >/dev/null
  sleep 1
}

extract_latest_status_line() {
  local log_dump="$1"
  printf '%s\n' "$log_dump" \
    | grep 'Debug automation executed: Debug automation status:' \
    | tail -n 1 || true
}

extract_trainer_release_block() {
  local status_line="$1"
  printf '%s\n' "$status_line" \
    | sed -n 's/.*trainerRelease={\([^}]*\)}.*/\1/p' \
    | tail -n 1
}

print_trainer_release_block() {
  local release_block="$1"
  if [[ -z "$release_block" ]]; then
    echo "Could not find trainerRelease={...} in the latest dump_status line." >&2
    return
  fi

  echo "trainerRelease={$release_block}"
  echo "Expanded fields:"
  printf '%s\n' "$release_block" \
    | tr ' ' '\n' \
    | sed 's/^/  /'
}

print_recent_runtime_events() {
  local log_dump="$1"
  local runtime_lines
  runtime_lines="$(printf '%s\n' "$log_dump" \
    | grep 'SESSION_DIAG category=trainer_release_runtime' \
    | tail -n "$TAIL_LINES" || true)"

  echo
  echo "Recent trainer_release_runtime events (last $TAIL_LINES):"
  if [[ -z "$runtime_lines" ]]; then
    echo "  none found in current logcat buffer"
    return
  fi
  printf '%s\n' "$runtime_lines"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "Missing value for --serial" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --tail)
      [[ $# -ge 2 ]] || { echo "Missing value for --tail" >&2; exit 2; }
      TAIL_LINES="$2"
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

ADB_CMD=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB_CMD+=(-s "$SERIAL")
fi

STATE="$("${ADB_CMD[@]}" get-state 2>/dev/null || true)"
if [[ "$STATE" != "device" ]]; then
  echo "No active adb device in state 'device' (state='$STATE')." >&2
  exit 1
fi

request_dump_status
LOG_DUMP="$("${ADB_CMD[@]}" logcat -d -v time -s DEBUG_VALIDATION FTMS 2>/dev/null || true)"
STATUS_LINE="$(extract_latest_status_line "$LOG_DUMP")"
RELEASE_BLOCK="$(extract_trainer_release_block "$STATUS_LINE")"

print_trainer_release_block "$RELEASE_BLOCK"
print_recent_runtime_events "$LOG_DUMP"

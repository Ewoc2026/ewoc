#!/usr/bin/env bash
set -euo pipefail

SERIAL=""
MODE="show"
TITLE=""
MESSAGE=""
PRESET=""
VERIFY=true
VERIFY_TIMEOUT_SEC=5

ACTION="io.github.ewoc2026.ewoc.action.DEBUG_AUTOMATION"
COMPONENT="io.github.ewoc2026.ewoc/io.github.ewoc2026.ewoc.MainActivity"
PACKAGE_NAME="io.github.ewoc2026.ewoc"
REMOTE_PROBE_EVENT_PATH="files/debug/session-debug-probe-events.jsonl"

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/send-session-debug-probe.sh [options]

Options:
  --serial <id>                Use a specific adb device serial.
  --show                       Show the probe immediately. This is the default.
  --arm                        Arm the probe to auto-show once pedaling resumes.
  --title <text>               Optional probe title.
  --message <text>             Probe message text.
  --preset disconnect_wait_menu
                               Use the standard disconnect prompt copy that tells
                               the rider to wait for MENU before testing the knob.
  --no-verify                  Skip post-send queue/status verification.
  --verify-timeout <seconds>   Wait this long for the expected queue event.
  --help                       Show this help.

Purpose:
  Send a session debug probe through the typed debug-intent path without
  retyping the full `adb shell am start ...` command each time.

Examples:
  ./scripts/adb/send-session-debug-probe.sh \
    --serial R92Y40YAZPB \
    --preset disconnect_wait_menu

  ./scripts/adb/send-session-debug-probe.sh \
    --serial R92Y40YAZPB \
    --arm \
    --title "Safety probe" \
    --message "Hold steady. Observe pedals."
EOF
}

read_probe_events() {
  "${ADB_CMD[@]}" exec-out run-as "$PACKAGE_NAME" cat "$REMOTE_PROBE_EVENT_PATH" 2>/dev/null || true
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

latest_probe_seq() {
  read_probe_events \
    | sed -n 's/.*"seq":\([0-9][0-9]*\).*/\1/p' \
    | tail -n 1
}

find_probe_event_after_seq() {
  local since_seq="$1"
  local expected_event="$2"
  read_probe_events | awk -v min_seq="$since_seq" -v expected_event="$expected_event" '
    {
      seq = -1
      event = ""
      if (match($0, /"seq":[0-9]+/)) {
        seq = substr($0, RSTART + 6, RLENGTH - 6) + 0
      }
      if (match($0, /"event":"[^"]+"/)) {
        event = substr($0, RSTART + 9, RLENGTH - 10)
      }
      if (seq > (min_seq + 0) && event == expected_event) {
        print
        found = 1
        exit 0
      }
    }
    END {
      exit(found ? 0 : 1)
    }
  '
}

request_debug_status_line() {
  local remote_command
  remote_command="$(shell_join_command \
    am start \
    -a "$ACTION" \
    -n "$COMPONENT" \
    --es command dump_status \
  )"
  "${ADB_CMD[@]}" shell "$remote_command" >/dev/null
  sleep 1
  "${ADB_CMD[@]}" logcat -d -s DEBUG_VALIDATION \
    | grep 'Debug automation executed: Debug automation status:' \
    | tail -n 1 || true
}

extract_status_field() {
  local status_line="$1"
  local field_name="$2"
  printf '%s\n' "$status_line" \
    | sed -n "s/.*${field_name}=\([^,]*\).*/\1/p" \
    | tail -n 1
}

verify_probe_delivery() {
  local base_seq="$1"
  local expected_event="shown"
  if [[ "$MODE" == "arm" ]]; then
    expected_event="armed"
  fi

  local deadline=$((SECONDS + VERIFY_TIMEOUT_SEC))
  local verified_event_line=""
  while (( SECONDS < deadline )); do
    if verified_event_line="$(find_probe_event_after_seq "$base_seq" "$expected_event")"; then
      break
    fi
    sleep 1
  done

  if [[ -n "$verified_event_line" ]]; then
    echo "Verified probe queue event: $verified_event_line"
  else
    echo "Warning: did not observe '$expected_event' in $REMOTE_PROBE_EVENT_PATH within ${VERIFY_TIMEOUT_SEC}s." >&2
  fi

  local status_line
  status_line="$(request_debug_status_line)"
  if [[ -z "$status_line" ]]; then
    echo "Warning: could not read fresh dump_status output after probe send." >&2
    return
  fi

  local visible armed ready blocker screen ftms_ready cadence power
  visible="$(extract_status_field "$status_line" "sessionDebugProbeVisible")"
  armed="$(extract_status_field "$status_line" "sessionDebugProbeArmed")"
  ready="$(extract_status_field "$status_line" "sessionDebugProbeAutoShowReady")"
  blocker="$(extract_status_field "$status_line" "sessionDebugProbeAutoShowBlocker")"
  screen="$(extract_status_field "$status_line" "screen")"
  ftms_ready="$(extract_status_field "$status_line" "ftmsReady")"
  cadence="$(extract_status_field "$status_line" "bikeCadenceRpm")"
  power="$(extract_status_field "$status_line" "bikePowerW")"

  echo "Probe status: screen=${screen:-unknown} visible=${visible:-unknown} armed=${armed:-unknown} autoShowReady=${ready:-unknown} blocker=${blocker:-unknown} ftmsReady=${ftms_ready:-unknown} cadenceRpm=${cadence:-unknown} powerW=${power:-unknown}"
}

disconnect_wait_menu_message() {
  cat <<'EOF'
Disconnect sent. Wait for MENU, then try knob once. Ready equals manual back. Noticeable equals still locked. Unsafe equals abrupt drop. Abort equals stop.
EOF
}

apply_preset() {
  case "$1" in
    disconnect_wait_menu)
      if [[ -z "$TITLE" ]]; then
        TITLE="DISC"
      fi
      if [[ -z "$MESSAGE" ]]; then
        MESSAGE="$(disconnect_wait_menu_message)"
      fi
      ;;
    *)
      echo "Unknown preset: $1" >&2
      exit 2
      ;;
  esac
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "Missing value for --serial" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --show)
      MODE="show"
      shift
      ;;
    --arm)
      MODE="arm"
      shift
      ;;
    --title)
      [[ $# -ge 2 ]] || { echo "Missing value for --title" >&2; exit 2; }
      TITLE="$2"
      shift 2
      ;;
    --message)
      [[ $# -ge 2 ]] || { echo "Missing value for --message" >&2; exit 2; }
      MESSAGE="$2"
      shift 2
      ;;
    --preset)
      [[ $# -ge 2 ]] || { echo "Missing value for --preset" >&2; exit 2; }
      PRESET="$2"
      shift 2
      ;;
    --no-verify)
      VERIFY=false
      shift
      ;;
    --verify-timeout)
      [[ $# -ge 2 ]] || { echo "Missing value for --verify-timeout" >&2; exit 2; }
      VERIFY_TIMEOUT_SEC="$2"
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

if [[ -n "$PRESET" ]]; then
  apply_preset "$PRESET"
fi

if [[ -z "$MESSAGE" ]]; then
  echo "A probe message is required. Use --message or --preset." >&2
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

COMMAND="show_session_debug_probe"
if [[ "$MODE" == "arm" ]]; then
  COMMAND="arm_session_debug_probe_when_pedaling"
fi

BASE_SEQ="$(latest_probe_seq)"
BASE_SEQ="${BASE_SEQ:-0}"

START_CMD=(
  am start
  -a "$ACTION"
  -n "$COMPONENT"
  --es command "$COMMAND"
)

if [[ -n "$TITLE" ]]; then
  START_CMD+=(--es probe_title "$TITLE")
fi

START_CMD+=(--es probe_message "$MESSAGE")

echo "Sending session debug probe with command '$COMMAND'."
if [[ -n "$TITLE" ]]; then
  echo "Title: $TITLE"
fi
echo "Message: $MESSAGE"

REMOTE_START_CMD="$(shell_join_command "${START_CMD[@]}")"
"${ADB_CMD[@]}" shell "$REMOTE_START_CMD"

if [[ "$VERIFY" == true ]]; then
  verify_probe_delivery "$BASE_SEQ"
fi

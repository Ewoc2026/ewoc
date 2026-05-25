#!/usr/bin/env bash
set -euo pipefail

SERIAL=""
TAIL_LINES=""
SINCE_SEQ=""
PACKAGE_NAME="io.github.ewoc2026.ewoc"
REMOTE_PATH="files/debug/session-debug-probe-events.jsonl"

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/adb/read-session-debug-probe-events.sh [options]

Options:
  --serial <id>     Use a specific adb device serial.
  --tail <count>    Print only the last N queue entries.
  --since-seq <n>   Print only entries with seq > n.
  --help            Show this help.

Purpose:
  Read the app-private session debug probe event queue directly via
  `run-as`, without depending on broad logcat capture.

Examples:
  ./scripts/adb/read-session-debug-probe-events.sh --serial R92Y40YAZPB
  ./scripts/adb/read-session-debug-probe-events.sh --since-seq 3
  ./scripts/adb/read-session-debug-probe-events.sh --tail 5
EOF
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
    --since-seq)
      [[ $# -ge 2 ]] || { echo "Missing value for --since-seq" >&2; exit 2; }
      SINCE_SEQ="$2"
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

RAW_OUTPUT="$("${ADB_CMD[@]}" exec-out run-as "$PACKAGE_NAME" cat "$REMOTE_PATH" 2>/dev/null || true)"
if [[ -z "$RAW_OUTPUT" ]]; then
  echo "No session debug probe events found at $REMOTE_PATH." >&2
  exit 0
fi

FILTERED_OUTPUT="$RAW_OUTPUT"
if [[ -n "$SINCE_SEQ" ]]; then
  FILTERED_OUTPUT="$(printf '%s\n' "$FILTERED_OUTPUT" | awk -v min_seq="$SINCE_SEQ" '
    match($0, /"seq":([0-9]+)/, found) {
      if ((found[1] + 0) > (min_seq + 0)) {
        print
      }
      next
    }
    {
      print
    }
  ')"
fi

if [[ -n "$TAIL_LINES" ]]; then
  FILTERED_OUTPUT="$(printf '%s\n' "$FILTERED_OUTPUT" | tail -n "$TAIL_LINES")"
fi

printf '%s\n' "$FILTERED_OUTPUT"

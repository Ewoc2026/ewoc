#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

STOP_FIRST=false
USE_FALLBACK=true
VERBOSE_REASON=true
GRADLE_ARGS=()

usage() {
  cat <<'EOF'
Usage:
  ./scripts/gradle-safe.sh [options] <gradle-args...>

Options:
  --stop-first     Run `./gradlew --stop` before the main build.
  --no-fallback    Do not retry with any fallback lane.
  --quiet-reason   Do not print the detected retry reason.
  --help           Show this help.

Purpose:
  Run a Gradle command through one shared serial wrapper that avoids the most
  common Kotlin incremental-cache false negatives in this repository.

Default behavior:
  - adds `--no-daemon` if it is missing
  - if the first run fails with a transient Kotlin compile-state signature,
    retries once with:
      --rerun-tasks
  - if the first run fails with a known daemon/cache signature, runs
    `./gradlew --stop` and retries once with:
      --no-daemon --no-configuration-cache -Dkotlin.incremental=false

Examples:
  ./scripts/gradle-safe.sh :app:compileDebugKotlin
  ./scripts/gradle-safe.sh :app:testDebugUnitTest --tests io.github.ewoc2026.ewoc.session.SessionOrchestratorFlowTest
  ./scripts/gradle-safe.sh --stop-first :app:lintDebug
EOF
}

print_cmd() {
  printf '==> '
  printf '%q ' "$@"
  printf '\n'
}

has_arg() {
  local needle="$1"
  shift
  local arg
  for arg in "$@"; do
    if [[ "$arg" == "$needle" ]]; then
      return 0
    fi
  done
  return 1
}

looks_like_cache_contention() {
  local logfile="$1"
  rg -q \
    'Could not close incremental caches|Storage for \[.*\] is already registered|Failed to compile with Kotlin daemon|Using fallback strategy: Compile without Kotlin daemon' \
    "$logfile"
}

looks_like_transient_compile_state() {
  local logfile="$1"
  local unresolved_count
  unresolved_count="$(rg -c "Unresolved reference" "$logfile" || true)"
  [[ "${unresolved_count:-0}" -ge 5 ]] &&
    rg -q \
      'Cannot infer type for value parameter|Compilation error\. See log for more details' \
      "$logfile"
}

run_gradle_capture() {
  local logfile="$1"
  shift
  print_cmd ./gradlew "$@"
  set +e
  ./gradlew "$@" 2>&1 | tee "$logfile"
  local cmd_status=${PIPESTATUS[0]}
  set -e
  return "$cmd_status"
}

stop_gradle_daemons() {
  print_cmd ./gradlew --stop
  ./gradlew --stop >/dev/null 2>&1 || true
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stop-first)
      STOP_FIRST=true
      shift
      ;;
    --no-fallback)
      USE_FALLBACK=false
      shift
      ;;
    --quiet-reason)
      VERBOSE_REASON=false
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      GRADLE_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ ${#GRADLE_ARGS[@]} -eq 0 ]]; then
  usage >&2
  exit 2
fi

if ! has_arg "--no-daemon" "${GRADLE_ARGS[@]}"; then
  GRADLE_ARGS+=("--no-daemon")
fi

if [[ "$STOP_FIRST" == true ]]; then
  stop_gradle_daemons
fi

LOGFILE="$(mktemp -t gradle-safe.XXXXXX.log)"
trap 'rm -f "$LOGFILE"' EXIT

if run_gradle_capture "$LOGFILE" "${GRADLE_ARGS[@]}"; then
  exit 0
fi

if [[ "$USE_FALLBACK" != true ]]; then
  exit 1
fi

if looks_like_cache_contention "$LOGFILE"; then
  if [[ "$VERBOSE_REASON" == true ]]; then
    echo
    echo "Detected Kotlin/Gradle cache contention signature. Retrying once with the safe fallback lane."
  fi

  stop_gradle_daemons

  FALLBACK_ARGS=("${GRADLE_ARGS[@]}")
  if ! has_arg "--no-configuration-cache" "${FALLBACK_ARGS[@]}"; then
    FALLBACK_ARGS+=("--no-configuration-cache")
  fi
  if ! has_arg "-Dkotlin.incremental=false" "${FALLBACK_ARGS[@]}"; then
    FALLBACK_ARGS+=("-Dkotlin.incremental=false")
  fi

  run_gradle_capture "$LOGFILE" "${FALLBACK_ARGS[@]}"
fi

if looks_like_transient_compile_state "$LOGFILE" && ! has_arg "--rerun-tasks" "${GRADLE_ARGS[@]}"; then
  if [[ "$VERBOSE_REASON" == true ]]; then
    echo
    echo "Detected a transient Kotlin compile-state signature. Retrying once with --rerun-tasks."
  fi

  RERUN_ARGS=("${GRADLE_ARGS[@]}" "--rerun-tasks")
  run_gradle_capture "$LOGFILE" "${RERUN_ARGS[@]}"
fi

exit 1

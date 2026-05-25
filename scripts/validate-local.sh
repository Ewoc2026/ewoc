#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

MODE="${1:-safe}"

usage() {
  cat <<'EOF'
Usage: ./scripts/validate-local.sh [fast|safe]

Validation lanes:
  fast  - environment doctor + debug Kotlin compile
  safe  - fast lane + unit tests + Android test compile + lint
EOF
}

run_step() {
  echo
  echo "==> $*"
  "$@"
}

case "$MODE" in
  fast)
    run_step ./scripts/dev-doctor.sh
    run_step ./scripts/gradle-safe.sh :app:compileDebugKotlin
    ;;
  safe)
    run_step ./scripts/dev-doctor.sh
    # Run Kotlin/Gradle lanes sequentially to avoid incremental-cache contention.
    run_step ./scripts/gradle-safe.sh :app:compileDebugKotlin
    run_step ./scripts/gradle-safe.sh :app:testDebugUnitTest
    run_step ./scripts/gradle-safe.sh :app:compileDebugAndroidTestKotlin
    run_step ./scripts/gradle-safe.sh :app:lintDebug
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "Unknown mode: $MODE" >&2
    usage >&2
    exit 1
    ;;
esac

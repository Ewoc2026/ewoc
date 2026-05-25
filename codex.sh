#!/usr/bin/env bash
set -euo pipefail

# Resolve symlinks so this wrapper works even when invoked via ~/bin/codex.
SOURCE="${BASH_SOURCE[0]}"
while [[ -L "$SOURCE" ]]; do
  SOURCE_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ "$SOURCE" != /* ]] && SOURCE="$SOURCE_DIR/$SOURCE"
done
SCRIPT_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
START_SCRIPT="$SCRIPT_DIR/scripts/start-codex.sh"

echo "[codex.sh] Wrapper script active. Delegating to scripts/start-codex.sh"

if [[ ! -x "$START_SCRIPT" ]]; then
  echo "Error: expected executable script at $START_SCRIPT"
  exit 1
fi

exec "$START_SCRIPT" "$@"

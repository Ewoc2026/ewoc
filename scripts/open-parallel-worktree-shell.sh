#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

BRANCH_NAME=""
BASE_REF="HEAD"
WORKTREE_PATH=""
GRADLE_HOME_PARENT="${HOME}/.gradle-worktrees"
RUN_BOOTSTRAP=true
COMMAND_STRING=""

usage() {
  cat <<'EOF'
Usage:
  ./scripts/open-parallel-worktree-shell.sh --branch <name> [options]

Options:
  --branch <name>       Branch to reuse or create. Required.
  --base <ref>          Base ref when creating a new branch. Default: HEAD.
  --path <path>         Explicit worktree path if a new worktree must be created.
  --gradle-parent <path>
                        Parent directory for per-worktree GRADLE_USER_HOME hints.
                        Default: ~/.gradle-worktrees
  --cmd <command>       Run one command in the prepared worktree instead of
                        opening an interactive shell.
  --no-bootstrap        Skip `./scripts/session-bootstrap.sh` before shell/cmd.
  --help                Show this help.

Purpose:
  Reuse or create a parallel worktree, export its suggested GRADLE_USER_HOME,
  optionally run the standard bootstrap, and then either open a shell there or
  execute one command. This is the smooth everyday entry point for isolated
  local parallel work.

Examples:
  ./scripts/open-parallel-worktree-shell.sh --branch feat/parallel-summary
  ./scripts/open-parallel-worktree-shell.sh --branch feat/parallel-lint --cmd "./scripts/gradle-safe.sh :app:lintDebug"
EOF
}

find_existing_worktree_for_branch() {
  local target_branch="refs/heads/$1"
  local current_path=""
  local current_branch=""

  while IFS= read -r line; do
    if [[ -z "$line" ]]; then
      if [[ -n "$current_path" && "$current_branch" == "$target_branch" ]]; then
        printf '%s\n' "$current_path"
        return 0
      fi
      current_path=""
      current_branch=""
      continue
    fi

    case "$line" in
      worktree\ *)
        current_path="${line#worktree }"
        ;;
      branch\ *)
        current_branch="${line#branch }"
        ;;
    esac
  done < <(git worktree list --porcelain)

  if [[ -n "$current_path" && "$current_branch" == "$target_branch" ]]; then
    printf '%s\n' "$current_path"
    return 0
  fi
  return 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --branch)
      [[ $# -ge 2 ]] || { echo "Missing value for --branch" >&2; exit 2; }
      BRANCH_NAME="$2"
      shift 2
      ;;
    --base)
      [[ $# -ge 2 ]] || { echo "Missing value for --base" >&2; exit 2; }
      BASE_REF="$2"
      shift 2
      ;;
    --path)
      [[ $# -ge 2 ]] || { echo "Missing value for --path" >&2; exit 2; }
      WORKTREE_PATH="$2"
      shift 2
      ;;
    --gradle-parent)
      [[ $# -ge 2 ]] || { echo "Missing value for --gradle-parent" >&2; exit 2; }
      GRADLE_HOME_PARENT="$2"
      shift 2
      ;;
    --cmd)
      [[ $# -ge 2 ]] || { echo "Missing value for --cmd" >&2; exit 2; }
      COMMAND_STRING="$2"
      shift 2
      ;;
    --no-bootstrap)
      RUN_BOOTSTRAP=false
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$BRANCH_NAME" ]]; then
  echo "--branch is required." >&2
  usage >&2
  exit 2
fi

if existing_path="$(find_existing_worktree_for_branch "$BRANCH_NAME")"; then
  WORKTREE_PATH="$existing_path"
  echo "Reusing existing worktree: $WORKTREE_PATH"
else
  create_args=(--branch "$BRANCH_NAME" --base "$BASE_REF" --gradle-parent "$GRADLE_HOME_PARENT" --no-shell-hint)
  if [[ -n "$WORKTREE_PATH" ]]; then
    create_args+=(--path "$WORKTREE_PATH")
  fi
  ./scripts/new-parallel-worktree.sh "${create_args[@]}"
  if [[ -z "$WORKTREE_PATH" ]]; then
    if ! WORKTREE_PATH="$(find_existing_worktree_for_branch "$BRANCH_NAME")"; then
      echo "Failed to resolve the newly created worktree path for $BRANCH_NAME." >&2
      exit 1
    fi
  fi
fi

WORKTREE_PATH="$(python3 -c 'import os,sys; print(os.path.abspath(sys.argv[1]))' "$WORKTREE_PATH")"
GRADLE_HOME_PARENT="$(python3 -c 'import os,sys; print(os.path.abspath(os.path.expanduser(sys.argv[1])))' "$GRADLE_HOME_PARENT")"
GRADLE_USER_HOME_PATH="${GRADLE_HOME_PARENT}/$(basename "$WORKTREE_PATH")"
mkdir -p "$GRADLE_USER_HOME_PATH"

cd "$WORKTREE_PATH"
export GRADLE_USER_HOME="$GRADLE_USER_HOME_PATH"

echo "Using worktree: $WORKTREE_PATH"
echo "Using GRADLE_USER_HOME: $GRADLE_USER_HOME"

if [[ "$RUN_BOOTSTRAP" == true && -x "./scripts/session-bootstrap.sh" ]]; then
  echo
  ./scripts/session-bootstrap.sh
  echo
fi

if [[ -n "$COMMAND_STRING" ]]; then
  exec "${SHELL:-/bin/bash}" -lc "$COMMAND_STRING"
fi

echo "Opening interactive shell in $WORKTREE_PATH"
exec "${SHELL:-/bin/bash}" -i

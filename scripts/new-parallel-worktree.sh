#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

BRANCH_NAME=""
BASE_REF="HEAD"
WORKTREE_PATH=""
WORKTREE_PARENT="$(cd "$PROJECT_ROOT/.." && pwd)"
GRADLE_HOME_PARENT="${HOME}/.gradle-worktrees"
OPEN_SHELL_HINT=true

usage() {
  cat <<'EOF'
Usage:
  ./scripts/new-parallel-worktree.sh --branch <name> [options]

Options:
  --branch <name>     Branch to create or reuse in the new worktree. Required.
  --base <ref>        Base ref when creating a new branch. Default: HEAD.
  --path <path>       Exact worktree path. Default: ../<repo>-<branch>
  --parent <path>     Parent directory for the default path.
  --gradle-parent <path>
                      Parent directory for per-worktree GRADLE_USER_HOME hints.
                      Default: ~/.gradle-worktrees
  --no-shell-hint     Do not print the follow-up shell snippet.
  --help              Show this help.

Purpose:
  Create an isolated parallel worktree for long-running builds, parallel
  validation, or task isolation. This avoids same-checkout Kotlin/Gradle
  cache contention instead of trying to force parallel build safety through
  shared local state.

Examples:
  ./scripts/new-parallel-worktree.sh --branch feat/summary-manual-pass
  ./scripts/new-parallel-worktree.sh --branch fix/probe-copy --base main
  ./scripts/new-parallel-worktree.sh --branch feat/parallel-lint --path ../ergometer-app-parallel-lint
EOF
}

sanitize_branch_for_path() {
  printf '%s' "$1" | tr '/:@' '-' | tr -cd 'A-Za-z0-9._-'
}

print_cmd() {
  printf '==> '
  printf '%q ' "$@"
  printf '\n'
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
    --parent)
      [[ $# -ge 2 ]] || { echo "Missing value for --parent" >&2; exit 2; }
      WORKTREE_PARENT="$2"
      shift 2
      ;;
    --gradle-parent)
      [[ $# -ge 2 ]] || { echo "Missing value for --gradle-parent" >&2; exit 2; }
      GRADLE_HOME_PARENT="$2"
      shift 2
      ;;
    --no-shell-hint)
      OPEN_SHELL_HINT=false
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

if [[ -z "$WORKTREE_PATH" ]]; then
  repo_name="$(basename "$PROJECT_ROOT")"
  branch_path_suffix="$(sanitize_branch_for_path "$BRANCH_NAME")"
  WORKTREE_PATH="${WORKTREE_PARENT}/${repo_name}-${branch_path_suffix}"
fi

WORKTREE_PATH="$(python3 -c 'import os,sys; print(os.path.abspath(sys.argv[1]))' "$WORKTREE_PATH")"
GRADLE_HOME_PARENT="$(python3 -c 'import os,sys; print(os.path.abspath(os.path.expanduser(sys.argv[1])))' "$GRADLE_HOME_PARENT")"

if [[ -e "$WORKTREE_PATH" ]]; then
  echo "Target path already exists: $WORKTREE_PATH" >&2
  exit 1
fi

mkdir -p "$WORKTREE_PARENT"
mkdir -p "$GRADLE_HOME_PARENT"

branch_exists=false
if git show-ref --verify --quiet "refs/heads/${BRANCH_NAME}"; then
  branch_exists=true
fi

if [[ "$branch_exists" == true ]]; then
  print_cmd git worktree add "$WORKTREE_PATH" "$BRANCH_NAME"
  git worktree add "$WORKTREE_PATH" "$BRANCH_NAME"
else
  print_cmd git worktree add -b "$BRANCH_NAME" "$WORKTREE_PATH" "$BASE_REF"
  git worktree add -b "$BRANCH_NAME" "$WORKTREE_PATH" "$BASE_REF"
fi

GRADLE_USER_HOME_PATH="${GRADLE_HOME_PARENT}/$(basename "$WORKTREE_PATH")"
mkdir -p "$GRADLE_USER_HOME_PATH"

echo
echo "Parallel worktree ready."
echo "Branch: $BRANCH_NAME"
echo "Path:   $WORKTREE_PATH"
echo "Base:   $BASE_REF"
echo "Suggested GRADLE_USER_HOME: $GRADLE_USER_HOME_PATH"

if [[ "$OPEN_SHELL_HINT" == true ]]; then
  echo
  echo "Suggested next commands:"
  echo "  cd \"$WORKTREE_PATH\""
  echo "  export GRADLE_USER_HOME=\"$GRADLE_USER_HOME_PATH\""
  echo "  ./scripts/session-bootstrap.sh"
  echo "  ./scripts/gradle-safe.sh :app:compileDebugKotlin"
fi

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"

usage() {
  cat <<USAGE
Usage: ${SCRIPT_NAME} --target-sha <sha> [options]

Wait for (or locate once) the first scheduled Android Build run whose head commit contains
<target-sha>, then download smoke artifacts and scan logs for known regression markers.

Required:
  --target-sha <sha>        Commit SHA that must be an ancestor of run headSha.

Options:
  --repo <owner/name>       GitHub repository. Defaults to GH_REPOSITORY or gh current repo.
  --workflow <file>         Workflow file name. Default: android-build.yml
  --event <name>            Workflow event filter. Default: schedule
  --poll-seconds <n>        Poll interval for watch mode. Default: 60
  --max-wait-minutes <n>    Stop waiting after N minutes (0 = no timeout). Default: 0
  --run-limit <n>           How many runs to inspect per poll. Default: 30
  --output-root <path>      Output root for downloaded artifacts. Default: docs/assets/validation
  --once                    Evaluate current runs once and exit if no match is found.
  --watch                   Keep polling until a matching run is found/completed (default).
  --help                    Show this help.

Examples:
  ${SCRIPT_NAME} --target-sha 892dfec6
  ${SCRIPT_NAME} --target-sha 892dfec6 --once
  ${SCRIPT_NAME} --target-sha 892dfec6 --poll-seconds 30 --max-wait-minutes 1440
USAGE
}

log() {
  printf '[%s] %s\n' "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" "$*"
}

die() {
  echo "error: $*" >&2
  exit 1
}

require_positive_int() {
  local value="$1"
  local name="$2"

  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    die "${name} must be a non-negative integer"
  fi
}

TARGET_SHA=""
REPOSITORY="${GH_REPOSITORY:-}"
WORKFLOW_FILE="android-build.yml"
EVENT_NAME="schedule"
POLL_SECONDS=60
MAX_WAIT_MINUTES=0
RUN_LIMIT=30
OUTPUT_ROOT="docs/assets/validation"
MODE="watch"
MARKER_PATTERN='performScrollTo\(\) failed|Quit to summary|criticalFlowScreensRenderExpectedAnchors|FAILED'

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-sha)
      TARGET_SHA="${2:-}"
      shift 2
      ;;
    --repo)
      REPOSITORY="${2:-}"
      shift 2
      ;;
    --workflow)
      WORKFLOW_FILE="${2:-}"
      shift 2
      ;;
    --event)
      EVENT_NAME="${2:-}"
      shift 2
      ;;
    --poll-seconds)
      POLL_SECONDS="${2:-}"
      shift 2
      ;;
    --max-wait-minutes)
      MAX_WAIT_MINUTES="${2:-}"
      shift 2
      ;;
    --run-limit)
      RUN_LIMIT="${2:-}"
      shift 2
      ;;
    --output-root)
      OUTPUT_ROOT="${2:-}"
      shift 2
      ;;
    --once)
      MODE="once"
      shift
      ;;
    --watch)
      MODE="watch"
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      die "unknown argument: $1"
      ;;
  esac
done

[[ -n "${TARGET_SHA}" ]] || die "--target-sha is required"
require_positive_int "${POLL_SECONDS}" "poll-seconds"
require_positive_int "${MAX_WAIT_MINUTES}" "max-wait-minutes"
require_positive_int "${RUN_LIMIT}" "run-limit"
[[ "${RUN_LIMIT}" -gt 0 ]] || die "run-limit must be > 0"
[[ "${POLL_SECONDS}" -gt 0 ]] || die "poll-seconds must be > 0"

if ! command -v gh >/dev/null 2>&1; then
  die "GitHub CLI (gh) is required"
fi

if ! command -v rg >/dev/null 2>&1; then
  die "ripgrep (rg) is required"
fi

if ! gh auth status >/dev/null 2>&1; then
  die "gh auth is not configured. Run: gh auth login"
fi

if [[ -z "${REPOSITORY}" ]]; then
  REPOSITORY="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
fi

if ! git cat-file -e "${TARGET_SHA}^{commit}" >/dev/null 2>&1; then
  git fetch origin "${TARGET_SHA}" --depth=1 >/dev/null 2>&1 || true
fi

TARGET_SHA_FULL="$(git rev-parse --verify "${TARGET_SHA}^{commit}" 2>/dev/null || true)"
[[ -n "${TARGET_SHA_FULL}" ]] || die "target commit ${TARGET_SHA} is not available locally"

ensure_commit_available() {
  local sha="$1"

  if git cat-file -e "${sha}^{commit}" >/dev/null 2>&1; then
    return 0
  fi

  git fetch origin "${sha}" --depth=1 >/dev/null 2>&1 || true
  git cat-file -e "${sha}^{commit}" >/dev/null 2>&1
}

run_contains_target() {
  local head_sha="$1"

  if ! ensure_commit_available "${head_sha}"; then
    log "warning: unable to fetch commit ${head_sha}; skipping run containment check"
    return 1
  fi

  git merge-base --is-ancestor "${TARGET_SHA_FULL}" "${head_sha}"
}

list_runs_tsv() {
  gh run list \
    --repo "${REPOSITORY}" \
    --workflow "${WORKFLOW_FILE}" \
    --event "${EVENT_NAME}" \
    --limit "${RUN_LIMIT}" \
    --json databaseId,headSha,status,conclusion,createdAt,updatedAt,url \
    --jq '.[] | [.databaseId, .headSha, .status, (.conclusion // "null"), .createdAt, .updatedAt, .url] | @tsv'
}

select_first_matching_run_tsv() {
  local selected_line=""
  local selected_created_at=""

  while IFS=$'\t' read -r run_id head_sha status conclusion created_at updated_at url; do
    [[ -n "${run_id}" ]] || continue
    [[ -n "${head_sha}" ]] || continue

    if run_contains_target "${head_sha}"; then
      if [[ -z "${selected_created_at}" || "${created_at}" < "${selected_created_at}" ]]; then
        selected_created_at="${created_at}"
        selected_line="${run_id}\t${head_sha}\t${status}\t${conclusion}\t${created_at}\t${updated_at}\t${url}"
      fi
    fi
  done < <(list_runs_tsv)

  if [[ -n "${selected_line}" ]]; then
    printf '%b\n' "${selected_line}"
  fi
}

run_view_tsv() {
  local run_id="$1"

  gh run view "${run_id}" \
    --repo "${REPOSITORY}" \
    --json status,conclusion,headSha,createdAt,updatedAt,url \
    --jq '[.status, (.conclusion // "null"), .headSha, .createdAt, .updatedAt, .url] | @tsv'
}

wait_for_completion() {
  local run_id="$1"
  local started_epoch="$2"
  local max_wait_seconds=$((MAX_WAIT_MINUTES * 60))
  local status conclusion head_sha created_at updated_at url

  while true; do
    IFS=$'\t' read -r status conclusion head_sha created_at updated_at url < <(run_view_tsv "${run_id}")

    if [[ "${status}" == "completed" ]]; then
      printf '%s\t%s\t%s\t%s\t%s\t%s\n' "${status}" "${conclusion}" "${head_sha}" "${created_at}" "${updated_at}" "${url}"
      return 0
    fi

    if [[ "${max_wait_seconds}" -gt 0 ]]; then
      local now_epoch
      now_epoch="$(date +%s)"
      if (( now_epoch - started_epoch >= max_wait_seconds )); then
        die "timed out after ${MAX_WAIT_MINUTES} minutes while waiting for run ${run_id}"
      fi
    fi

    log "run ${run_id} status=${status}; waiting ${POLL_SECONDS}s"
    sleep "${POLL_SECONDS}"
  done
}

timestamp_from_iso8601() {
  local iso="$1"
  date -u -d "${iso}" +"%Y%m%dT%H%M%SZ" 2>/dev/null || echo "${iso}" | sed -E 's/[^0-9TZ]//g'
}

write_run_metadata() {
  local run_id="$1"
  local head_sha="$2"
  local status="$3"
  local conclusion="$4"
  local created_at="$5"
  local updated_at="$6"
  local url="$7"
  local metadata_path="$8"

  {
    echo "run_id=${run_id}"
    echo "workflow=${WORKFLOW_FILE}"
    echo "event=${EVENT_NAME}"
    echo "head_sha=${head_sha}"
    echo "status=${status}"
    echo "conclusion=${conclusion}"
    echo "created_at=${created_at}"
    echo "updated_at=${updated_at}"
    echo "url=${url}"
  } > "${metadata_path}"

  gh run view "${run_id}" \
    --repo "${REPOSITORY}" \
    --json jobs \
    --jq '.jobs[] | "job_" + (.name | ascii_downcase | gsub("[^a-z0-9]+"; "_")) + "=" + (.conclusion // .status // "unknown")' \
    >> "${metadata_path}" || true
}

download_artifacts() {
  local run_id="$1"
  local destination_dir="$2"
  local artifact_name="android-instrumentation-smoke-${run_id}"

  mkdir -p "${destination_dir}"

  if gh run download "${run_id}" --repo "${REPOSITORY}" -n "${artifact_name}" -D "${destination_dir}"; then
    return 0
  fi

  log "named artifact ${artifact_name} was not found; downloading all run artifacts"
  gh run download "${run_id}" --repo "${REPOSITORY}" -D "${destination_dir}"
}

scan_markers() {
  local destination_dir="$1"
  local summary_file="${destination_dir}/failure-marker-scan.txt"

  shopt -s nullglob
  local log_files=("${destination_dir}"/smoke-run*.log)
  shopt -u nullglob

  if [[ ${#log_files[@]} -eq 0 ]]; then
    {
      echo "status=missing_logs"
      echo "pattern=${MARKER_PATTERN}"
      echo "message=No smoke-run*.log files were found in artifact directory"
    } > "${summary_file}"
    log "warning: no smoke-run*.log files found under ${destination_dir}"
    return 0
  fi

  local matches
  matches="$(rg -n "${MARKER_PATTERN}" "${log_files[@]}" || true)"

  if [[ -n "${matches}" ]]; then
    {
      echo "status=markers_found"
      echo "pattern=${MARKER_PATTERN}"
      echo "matches<<EOF"
      printf '%s\n' "${matches}"
      echo "EOF"
    } > "${summary_file}"

    log "regression markers were found in smoke logs"
    printf '%s\n' "${matches}"
    return 0
  fi

  {
    echo "status=clean"
    echo "pattern=${MARKER_PATTERN}"
    echo "message=No recurrence markers were found in smoke-run logs"
  } > "${summary_file}"

  log "no recurrence markers found in smoke logs"
}

log "repository=${REPOSITORY} workflow=${WORKFLOW_FILE} event=${EVENT_NAME} mode=${MODE} target_sha=${TARGET_SHA_FULL}"

start_epoch="$(date +%s)"
max_wait_seconds=$((MAX_WAIT_MINUTES * 60))
matched_tsv=""

while true; do
  matched_tsv="$(select_first_matching_run_tsv || true)"

  if [[ -n "${matched_tsv}" ]]; then
    break
  fi

  if [[ "${MODE}" == "once" ]]; then
    log "no matching run found in the latest ${RUN_LIMIT} runs"
    exit 2
  fi

  if [[ "${max_wait_seconds}" -gt 0 ]]; then
    now_epoch="$(date +%s)"
    if (( now_epoch - start_epoch >= max_wait_seconds )); then
      die "timed out after ${MAX_WAIT_MINUTES} minutes without a matching run"
    fi
  fi

  log "no matching run yet; polling again in ${POLL_SECONDS}s"
  sleep "${POLL_SECONDS}"
done

IFS=$'\t' read -r run_id head_sha status conclusion created_at updated_at run_url <<< "${matched_tsv}"
log "selected matching run id=${run_id} head_sha=${head_sha} status=${status} created_at=${created_at}"

if [[ "${status}" != "completed" ]]; then
  IFS=$'\t' read -r status conclusion head_sha created_at updated_at run_url < <(wait_for_completion "${run_id}" "${start_epoch}")
  log "run ${run_id} completed with conclusion=${conclusion}"
fi

timestamp="$(timestamp_from_iso8601 "${created_at}")"
destination_dir="${OUTPUT_ROOT}/ci-${EVENT_NAME}-smoke-${timestamp}-run-${run_id}"

if compgen -G "${OUTPUT_ROOT}/ci-${EVENT_NAME}-smoke-*-run-${run_id}" > /dev/null; then
  log "existing capture directory for run ${run_id} already exists; writing into ${destination_dir}"
fi

download_artifacts "${run_id}" "${destination_dir}"
write_run_metadata "${run_id}" "${head_sha}" "${status}" "${conclusion}" "${created_at}" "${updated_at}" "${run_url}" "${destination_dir}/run-metadata.txt"
scan_markers "${destination_dir}"

log "capture complete"
log "artifact_dir=${destination_dir}"

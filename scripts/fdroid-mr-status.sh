#!/usr/bin/env bash
set -euo pipefail

mr_iid="${1:-39065}"
project_path="${FDROIDDATA_PROJECT_PATH:-fdroid/fdroiddata}"
encoded_project_path="${project_path//\//%2F}"
api_base="https://gitlab.com/api/v4"

json_get() {
  curl --silent --show-error --fail "$api_base$1"
}

mr_json="$(json_get "/projects/$encoded_project_path/merge_requests/$mr_iid")"

python3 - "$mr_json" <<'PY'
import json
import sys

mr = json.loads(sys.argv[1])
pipeline = mr.get("head_pipeline") or {}
labels = ", ".join(mr.get("labels") or []) or "-"
print(f"MR !{mr['iid']}: {mr['title']}")
print(f"State: {mr['state']} | merge_status: {mr.get('merge_status', '-')}")
print(f"Labels: {labels}")
print(f"Source: {mr.get('source_project_id')}:{mr.get('source_branch')}")
print(f"Target: {mr.get('target_project_id')}:{mr.get('target_branch')}")
if pipeline:
    print(
        "Pipeline: "
        f"{pipeline.get('id')} {pipeline.get('status')} "
        f"({pipeline.get('web_url')})"
    )
else:
    print("Pipeline: -")
PY

pipeline_project="$(python3 - "$mr_json" <<'PY'
import json
import sys

mr = json.loads(sys.argv[1])
pipeline = mr.get("head_pipeline") or {}
print(pipeline.get("project_id") or "")
PY
)"

pipeline_id="$(python3 - "$mr_json" <<'PY'
import json
import sys

mr = json.loads(sys.argv[1])
pipeline = mr.get("head_pipeline") or {}
print(pipeline.get("id") or "")
PY
)"

if [[ -n "$pipeline_project" && -n "$pipeline_id" ]]; then
  jobs_json="$(json_get "/projects/$pipeline_project/pipelines/$pipeline_id/jobs?per_page=100")"
  python3 - "$jobs_json" <<'PY'
import json
import sys

jobs = json.loads(sys.argv[1])
if not jobs:
    print("Jobs: -")
    raise SystemExit

print("Jobs:")
for job in sorted(jobs, key=lambda item: (item.get("stage") or "", item.get("name") or "")):
    status = job.get("status") or "-"
    stage = job.get("stage") or "-"
    name = job.get("name") or "-"
    print(f"- {status:12} {stage:16} {name}")
PY
fi

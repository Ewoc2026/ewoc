#!/usr/bin/env python3
"""Generate weekly flaky smoke metrics from GitHub Actions runs."""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any

API_ROOT = "https://api.github.com"
FAILED_TEST_PATTERNS = (
    re.compile(r"(?P<class>[A-Za-z0-9_.$]+Test) > (?P<test>[^\[]+)\[[^\]]+\]\s+FAILED"),
    re.compile(r"(?P<class>[A-Za-z0-9_.$]+Test) > (?P<test>[^\[]+)\s+FAILED"),
)


@dataclass
class SmokeJob:
    run_id: int
    run_number: int
    run_url: str
    event: str
    job_id: int
    conclusion: str


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Prevent urllib from auto-following redirects so we can handle signed log URLs safely."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[override]
        return None


def github_request(path: str, token: str, binary: bool = False) -> tuple[Any, dict[str, str]]:
    """Fetch a GitHub REST resource and return parsed payload with response headers."""
    request = urllib.request.Request(
        f"{API_ROOT}{path}",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )

    try:
        with urllib.request.urlopen(request) as response:
            headers = {k.lower(): v for k, v in response.headers.items()}
            payload = response.read()
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"GitHub API request failed: {path} -> {error.code}: {body}") from error

    if binary:
        return payload, headers

    return json.loads(payload.decode("utf-8")), headers


def download_job_log_archive(repo: str, job_id: int, token: str) -> bytes:
    """Download a job log archive by resolving the API redirect first."""
    request = urllib.request.Request(
        f"{API_ROOT}/repos/{repo}/actions/jobs/{job_id}/logs",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )

    location = ""
    opener = urllib.request.build_opener(_NoRedirect())
    try:
        with opener.open(request) as response:
            location = response.headers.get("Location", "")
    except urllib.error.HTTPError as error:
        if error.code in (302, 307, 308):
            location = error.headers.get("Location", "")
        elif error.code in (404, 410):
            return b""
        else:
            body = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(
                f"GitHub API request failed: /repos/{repo}/actions/jobs/{job_id}/logs -> {error.code}: {body}"
            ) from error

    if not location:
        return b""

    try:
        with urllib.request.urlopen(location) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"Log archive download failed for job {job_id}: {error.code}: {body}"
        ) from error


def list_workflow_runs(repo: str, workflow_file: str, since_iso: str, token: str) -> list[dict[str, Any]]:
    """Load all workflow runs in the selected period."""
    runs: list[dict[str, Any]] = []
    page = 1

    while True:
        query = urllib.parse.urlencode(
            {
                "per_page": 100,
                "page": page,
                "created": f">={since_iso}",
                "status": "completed",
            }
        )
        payload, _ = github_request(
            f"/repos/{repo}/actions/workflows/{workflow_file}/runs?{query}",
            token,
        )

        batch = payload.get("workflow_runs", [])
        if not batch:
            break

        runs.extend(batch)
        if len(batch) < 100:
            break
        page += 1

    return runs


def list_smoke_jobs(repo: str, run: dict[str, Any], token: str) -> list[SmokeJob]:
    """Collect instrumentation smoke jobs for one workflow run."""
    jobs: list[SmokeJob] = []
    run_id = int(run["id"])
    run_number = int(run["run_number"])
    run_url = run.get("html_url", "")
    event = run.get("event", "unknown")

    page = 1
    while True:
        query = urllib.parse.urlencode({"per_page": 100, "page": page})
        payload, _ = github_request(f"/repos/{repo}/actions/runs/{run_id}/jobs?{query}", token)
        batch = payload.get("jobs", [])
        if not batch:
            break

        for job in batch:
            if job.get("name") != "android-instrumentation-smoke":
                continue
            jobs.append(
                SmokeJob(
                    run_id=run_id,
                    run_number=run_number,
                    run_url=run_url,
                    event=event,
                    job_id=int(job["id"]),
                    conclusion=(job.get("conclusion") or "unknown").lower(),
                )
            )

        if len(batch) < 100:
            break
        page += 1

    return jobs


def extract_failed_tests_from_job_log(repo: str, job_id: int, token: str) -> Counter[str]:
    """Parse failed test identifiers from a job log zip archive."""
    counters: Counter[str] = Counter()

    log_zip_data = download_job_log_archive(repo, job_id, token)
    if not log_zip_data:
        return counters
    if not log_zip_data.startswith(b"PK"):
        return counters

    try:
        with zipfile.ZipFile(io.BytesIO(log_zip_data)) as archive:
            for name in archive.namelist():
                if not name.endswith(".txt"):
                    continue

                content = archive.read(name).decode("utf-8", errors="replace")
                for line in content.splitlines():
                    for pattern in FAILED_TEST_PATTERNS:
                        match = pattern.search(line)
                        if not match:
                            continue

                        class_name = match.group("class")
                        test_name = match.group("test").strip()
                        counters[f"{class_name}#{test_name}"] += 1
                        break
    except zipfile.BadZipFile:
        return counters

    return counters


def build_report(
    repo: str,
    days: int,
    now_utc: datetime,
    smoke_jobs: list[SmokeJob],
    failed_tests: Counter[str],
) -> tuple[str, dict[str, Any]]:
    """Create markdown and JSON payloads for artifact and job summary."""
    total_jobs = len(smoke_jobs)
    failed_jobs = [job for job in smoke_jobs if job.conclusion == "failure"]
    failure_count = len(failed_jobs)
    failure_rate = (failure_count / total_jobs * 100.0) if total_jobs else 0.0

    top_three = failed_tests.most_common(3)
    recent_failed_runs: list[dict[str, Any]] = []
    for job in failed_jobs[:5]:
        recent_failed_runs.append(
            {
                "run_id": job.run_id,
                "run_number": job.run_number,
                "event": job.event,
                "url": job.run_url,
            }
        )

    since = now_utc - timedelta(days=days)
    lines = [
        "# Weekly Flake Metrics",
        "",
        f"- repository: `{repo}`",
        f"- window: `{since.strftime('%Y-%m-%dT%H:%M:%SZ')}` to `{now_utc.strftime('%Y-%m-%dT%H:%M:%SZ')}`",
        f"- smoke jobs: `{total_jobs}`",
        f"- failed smoke jobs: `{failure_count}`",
        f"- flake failure rate: `{failure_rate:.1f}%`",
        "",
        "## Top Failing Tests",
    ]

    if top_three:
        for test_name, count in top_three:
            lines.append(f"- `{test_name}`: `{count}` failures")
    else:
        lines.append("- No failed test signatures were parsed from available logs.")

    lines.append("")
    lines.append("## Recent Failed Runs")
    if recent_failed_runs:
        for run in recent_failed_runs:
            lines.append(
                f"- run #{run['run_number']} (`{run['event']}`): {run['url']}"
            )
    else:
        lines.append("- No failed instrumentation smoke runs in the selected window.")

    payload = {
        "repository": repo,
        "window_start": since.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "window_end": now_utc.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "smoke_job_count": total_jobs,
        "failed_smoke_job_count": failure_count,
        "failure_rate_percent": round(failure_rate, 1),
        "top_failed_tests": [
            {"name": test_name, "failures": count} for test_name, count in top_three
        ],
        "recent_failed_runs": recent_failed_runs,
    }

    return "\n".join(lines) + "\n", payload


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate weekly flake metrics from Actions logs")
    parser.add_argument("--workflow-file", default="android-build.yml")
    parser.add_argument("--days", type=int, default=7)
    parser.add_argument("--markdown-output", default="weekly-flake-report.md")
    parser.add_argument("--json-output", default="weekly-flake-report.json")
    args = parser.parse_args()

    token = os.getenv("GH_TOKEN") or os.getenv("GITHUB_TOKEN")
    repository = os.getenv("GH_REPOSITORY") or os.getenv("GITHUB_REPOSITORY")

    if not token:
        print("error: GH_TOKEN or GITHUB_TOKEN is required", file=sys.stderr)
        return 1

    if not repository:
        print("error: GH_REPOSITORY or GITHUB_REPOSITORY is required", file=sys.stderr)
        return 1

    now_utc = datetime.now(timezone.utc)
    since_iso = (now_utc - timedelta(days=args.days)).strftime("%Y-%m-%dT%H:%M:%SZ")

    runs = list_workflow_runs(repository, args.workflow_file, since_iso, token)

    smoke_jobs: list[SmokeJob] = []
    for run in runs:
        smoke_jobs.extend(list_smoke_jobs(repository, run, token))

    failed_tests: Counter[str] = Counter()
    for smoke_job in smoke_jobs:
        if smoke_job.conclusion != "failure":
            continue
        failed_tests.update(extract_failed_tests_from_job_log(repository, smoke_job.job_id, token))

    markdown_report, json_report = build_report(
        repo=repository,
        days=args.days,
        now_utc=now_utc,
        smoke_jobs=smoke_jobs,
        failed_tests=failed_tests,
    )

    with open(args.markdown_output, "w", encoding="utf-8") as markdown_file:
        markdown_file.write(markdown_report)

    with open(args.json_output, "w", encoding="utf-8") as json_file:
        json.dump(json_report, json_file, indent=2)
        json_file.write("\n")

    print(markdown_report)

    summary_path = os.getenv("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary_file:
            summary_file.write(markdown_report)

    return 0


if __name__ == "__main__":
    sys.exit(main())

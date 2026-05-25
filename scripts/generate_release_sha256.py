#!/usr/bin/env python3
"""
Generate a SHA256SUMS file for release artifacts.

Usage:
    python scripts/generate_release_sha256.py <artifact> [<artifact> ...]

The output file is written next to the current working directory unless
--output is provided.
"""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import sys


def sha256_for(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate SHA256SUMS.txt for release artifacts."
    )
    parser.add_argument(
        "artifacts",
        nargs="+",
        help="Artifact file paths to include in SHA256SUMS.txt.",
    )
    parser.add_argument(
        "--output",
        default="SHA256SUMS.txt",
        help="Output file path. Default: SHA256SUMS.txt",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    artifact_paths = [Path(item).expanduser().resolve() for item in args.artifacts]

    missing = [str(path) for path in artifact_paths if not path.is_file()]
    if missing:
        print("Missing artifact(s):", file=sys.stderr)
        for item in missing:
            print(f"- {item}", file=sys.stderr)
        return 1

    lines = []
    for path in artifact_paths:
        lines.append(f"{sha256_for(path)}  {path.name}")

    output_path = Path(args.output).expanduser().resolve()
    output_path.write_text("\n".join(lines) + "\n", encoding="ascii")
    print(output_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

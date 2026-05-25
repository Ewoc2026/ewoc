#!/usr/bin/env python3
"""Validate Android string resource parity and formatter placeholder safety.

The script treats app/src/main/res/values/strings.xml as source-of-truth and validates
all locale files matching app/src/main/res/values-*/strings.xml.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
import xml.etree.ElementTree as ET

PLACEHOLDER_RE = re.compile(
    r"%(?:\d+\$)?(?:[-#+ 0,(<]*)?(?:\d+)?(?:\.\d+)?(?:[tT])?[a-zA-Z%]"
)


@dataclass(frozen=True)
class StringEntry:
    name: str
    text: str


def extract_text(node: ET.Element) -> str:
    return "".join(node.itertext())


def parse_strings_file(path: Path) -> dict[str, StringEntry]:
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        raise ValueError(f"XML parse failed for {path}: {exc}") from exc

    root = tree.getroot()
    if root.tag != "resources":
        raise ValueError(f"Unexpected root tag in {path}: {root.tag}")

    entries: dict[str, StringEntry] = {}
    for node in root.findall("string"):
        name = node.get("name")
        if not name:
            continue
        if node.get("translatable") == "false":
            continue
        entries[name] = StringEntry(name=name, text=extract_text(node))
    return entries


def placeholder_tokens(text: str) -> list[str]:
    tokens = [token for token in PLACEHOLDER_RE.findall(text) if token != "%%"]
    return sorted(tokens)


def validate_locale(
    source: dict[str, StringEntry],
    locale_path: Path,
) -> list[str]:
    errors: list[str] = []

    try:
        locale_entries = parse_strings_file(locale_path)
    except ValueError as exc:
        return [str(exc)]

    source_keys = set(source.keys())
    locale_keys = set(locale_entries.keys())

    missing_keys = sorted(source_keys - locale_keys)
    extra_keys = sorted(locale_keys - source_keys)

    if missing_keys:
        errors.append(
            f"{locale_path}: missing keys ({len(missing_keys)}): {', '.join(missing_keys)}"
        )
    if extra_keys:
        errors.append(
            f"{locale_path}: extra keys ({len(extra_keys)}): {', '.join(extra_keys)}"
        )

    common_keys = sorted(source_keys & locale_keys)
    for key in common_keys:
        source_tokens = placeholder_tokens(source[key].text)
        locale_tokens = placeholder_tokens(locale_entries[key].text)
        if source_tokens != locale_tokens:
            errors.append(
                f"{locale_path}: placeholder mismatch for '{key}' "
                f"source={source_tokens} locale={locale_tokens}"
            )

    return errors


def discover_locale_files(res_dir: Path) -> list[Path]:
    files = []
    for values_dir in sorted(res_dir.glob("values-*")):
        candidate = values_dir / "strings.xml"
        if candidate.exists():
            files.append(candidate)
    return files


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate Android string locale parity and placeholders."
    )
    parser.add_argument(
        "--res-dir",
        default="app/src/main/res",
        help="Android resource directory (default: app/src/main/res)",
    )
    parser.add_argument(
        "--source",
        default="values/strings.xml",
        help="Source strings file path relative to --res-dir (default: values/strings.xml)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    res_dir = Path(args.res_dir)
    source_path = res_dir / args.source

    if not source_path.exists():
        print(f"ERROR: source strings file not found: {source_path}")
        return 1

    try:
        source_entries = parse_strings_file(source_path)
    except ValueError as exc:
        print(f"ERROR: {exc}")
        return 1

    locale_files = discover_locale_files(res_dir)
    if not locale_files:
        print("OK: no locale strings files found under values-*/strings.xml")
        return 0

    all_errors: list[str] = []
    for locale_file in locale_files:
        all_errors.extend(validate_locale(source_entries, locale_file))

    if all_errors:
        print("ERROR: string resource validation failed")
        for error in all_errors:
            print(f"- {error}")
        return 1

    print(
        f"OK: validated {len(locale_files)} locale file(s) against {source_path} "
        f"({len(source_entries)} source keys)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

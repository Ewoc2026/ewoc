#!/usr/bin/env bash

set -euo pipefail

DEFAULT_SANDBOX_ROOT="$HOME/.config/nrfconnect/nrfutil-sandboxes/device"
SANDBOX_ROOT="${NRFUTIL_DEVICE_SANDBOX_ROOT:-$DEFAULT_SANDBOX_ROOT}"

if [[ -n "${NRFUTIL_DEVICE_BIN:-}" ]]; then
  DEVICE_BIN="$NRFUTIL_DEVICE_BIN"
else
  # nRF Connect installs this binary into a versioned user sandbox.
  mapfile -t CANDIDATES < <(
    find "$SANDBOX_ROOT" -mindepth 3 -maxdepth 3 -path '*/bin/nrfutil-device' -type f 2>/dev/null | sort -V
  )
  if ((${#CANDIDATES[@]} > 0)); then
    DEVICE_BIN="${CANDIDATES[-1]}"
  else
    DEVICE_BIN=""
  fi
fi

if [[ -z "${DEVICE_BIN:-}" || ! -x "$DEVICE_BIN" ]]; then
  echo "nrfutil-device binary not found under $SANDBOX_ROOT" >&2
  echo "Install/open nRF Connect for Desktop once, or override with NRFUTIL_DEVICE_BIN=/absolute/path/to/nrfutil-device" >&2
  exit 1
fi

exec "$DEVICE_BIN" "$@"

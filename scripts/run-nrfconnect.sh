#!/usr/bin/env bash

set -euo pipefail

DEFAULT_APPIMAGE="$HOME/Downloads/nrfconnect-5.2.1-x86_64.AppImage"
APPIMAGE_PATH="${NRF_CONNECT_APPIMAGE:-$DEFAULT_APPIMAGE}"
APPIMAGE_BASENAME="$(basename "$APPIMAGE_PATH")"
APPIMAGE_STEM="${APPIMAGE_BASENAME%.AppImage}"
CACHE_ROOT="${XDG_CACHE_HOME:-$HOME/.cache}/ewoc"
EXTRACT_ROOT="$CACHE_ROOT/$APPIMAGE_STEM"
APPDIR="$EXTRACT_ROOT/squashfs-root"
APP_BINARY="$APPDIR/nrfconnect"
APP_RUNNER="$APPDIR/AppRun"

if [[ ! -f "$APPIMAGE_PATH" ]]; then
  echo "nRF Connect AppImage not found: $APPIMAGE_PATH" >&2
  echo "Override with NRF_CONNECT_APPIMAGE=/path/to/nrfconnect.AppImage" >&2
  exit 1
fi

mkdir -p "$EXTRACT_ROOT"

if [[ ! -x "$APP_BINARY" ]]; then
  rm -rf "$APPDIR"
  (
    cd "$EXTRACT_ROOT"
    "$APPIMAGE_PATH" --appimage-extract >/dev/null
  )
fi

if [[ ! -x "$APP_BINARY" || ! -x "$APP_RUNNER" ]]; then
  echo "nRF Connect extraction did not produce the expected binaries under $APPDIR" >&2
  exit 1
fi

chmod +x "$APPIMAGE_PATH"
exec env APPDIR="$APPDIR" "$APP_BINARY" --no-sandbox "$@"

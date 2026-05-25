#!/usr/bin/env bash

set -euo pipefail

DEFAULT_NRFUTIL_HOME_BIN="$HOME/.nrfutil/bin/nrfutil"
DEFAULT_NRFCONNECT_BIN="$HOME/.config/nrfconnect/nrfutil"
WIRESHARK_EXTCAP_DIR="${WIRESHARK_EXTCAP_DIR:-$HOME/.local/lib/wireshark/extcap}"
FORCE_BOOTSTRAP=false

if [[ $# -gt 1 ]]; then
  echo "Usage: $0 [--force]" >&2
  exit 1
fi

if [[ $# -eq 1 ]]; then
  if [[ "$1" == "--force" ]]; then
    FORCE_BOOTSTRAP=true
  else
    echo "Unknown argument: $1" >&2
    echo "Usage: $0 [--force]" >&2
    exit 1
  fi
fi

resolve_nrfutil_bin() {
  if [[ -n "${NRFUTIL_BIN:-}" && -x "${NRFUTIL_BIN}" ]]; then
    echo "$NRFUTIL_BIN"
    return 0
  fi

  if [[ -x "$DEFAULT_NRFUTIL_HOME_BIN" ]]; then
    echo "$DEFAULT_NRFUTIL_HOME_BIN"
    return 0
  fi

  if [[ -x "$DEFAULT_NRFCONNECT_BIN" ]]; then
    echo "$DEFAULT_NRFCONNECT_BIN"
    return 0
  fi

  return 1
}

if ! NRFUTIL_BIN="$(resolve_nrfutil_bin)"; then
  echo "nrfutil was not found under $DEFAULT_NRFUTIL_HOME_BIN or $DEFAULT_NRFCONNECT_BIN" >&2
  echo "Open nRF Connect for Desktop once, or override with NRFUTIL_BIN=/absolute/path/to/nrfutil" >&2
  exit 1
fi

if ! "$NRFUTIL_BIN" list 2>/dev/null | grep -q '^ble-sniffer[[:space:]]'; then
  echo "Installing nrfutil ble-sniffer command..."
  "$NRFUTIL_BIN" install ble-sniffer
fi

if [[ -x "$DEFAULT_NRFUTIL_HOME_BIN" ]]; then
  NRFUTIL_BIN="$DEFAULT_NRFUTIL_HOME_BIN"
fi

mkdir -p "$WIRESHARK_EXTCAP_DIR"

EXPECTED_FILES=(
  "$WIRESHARK_EXTCAP_DIR/nrfutil-ble-sniffer-shim"
  "$WIRESHARK_EXTCAP_DIR/nrfutil-ble-sniffer-hci-shim"
  "$WIRESHARK_EXTCAP_DIR/nrfutil-ble-sniffer-shim-config.json"
)

ALL_PRESENT=true
for path in "${EXPECTED_FILES[@]}"; do
  if [[ ! -e "$path" ]]; then
    ALL_PRESENT=false
    break
  fi
done

if [[ "$FORCE_BOOTSTRAP" == true ]]; then
  rm -f "${EXPECTED_FILES[@]}"
  ALL_PRESENT=false
fi

if [[ "$ALL_PRESENT" == true ]]; then
  echo "BLE sniffer extcap shims already exist in $WIRESHARK_EXTCAP_DIR; skipping bootstrap."
else
  echo "Bootstrapping Wireshark extcap shims into $WIRESHARK_EXTCAP_DIR..."
  "$NRFUTIL_BIN" ble-sniffer bootstrap
fi

for path in "${EXPECTED_FILES[@]}"; do
  if [[ ! -e "$path" ]]; then
    echo "Missing expected BLE sniffer bootstrap output: $path" >&2
    exit 1
  fi
done

FIRMWARE_ROOT="$HOME/.nrfutil/share/nrfutil-ble-sniffer/firmware"
echo "BLE sniffer bootstrap completed."

if [[ -d "$FIRMWARE_ROOT" ]]; then
  echo "Firmware directory: $FIRMWARE_ROOT"
  find "$FIRMWARE_ROOT" -maxdepth 1 -type f | sort
else
  echo "Firmware directory not found under $FIRMWARE_ROOT" >&2
fi

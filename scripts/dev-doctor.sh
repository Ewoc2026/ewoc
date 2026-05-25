#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

PASS_COUNT=0
WARN_COUNT=0
FAIL_COUNT=0
EXPECTED_JAVA_MAJOR=21
HAVE_RG=false
if command -v rg >/dev/null 2>&1; then
  HAVE_RG=true
fi

ok() {
  echo "[OK]   $1"
  PASS_COUNT=$((PASS_COUNT + 1))
}

warn() {
  echo "[WARN] $1"
  WARN_COUNT=$((WARN_COUNT + 1))
}

fail() {
  echo "[FAIL] $1"
  FAIL_COUNT=$((FAIL_COUNT + 1))
}

contains_regex() {
  local pattern="$1"
  if [[ "$HAVE_RG" == "true" ]]; then
    rg -q -- "$pattern"
  else
    grep -Eq -- "$pattern"
  fi
}

contains_exact_line() {
  local value="$1"
  if [[ "$HAVE_RG" == "true" ]]; then
    rg -qx -- "$value"
  else
    grep -Fxq -- "$value"
  fi
}

print_header() {
  echo
  echo "== $1 =="
}

check_cmd() {
  local cmd="$1"
  local label="$2"
  if command -v "$cmd" >/dev/null 2>&1; then
    ok "$label: $(command -v "$cmd")"
  else
    fail "$label not found in PATH ($cmd)"
  fi
}

print_header "Environment Variables"
if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  ok "ANDROID_SDK_ROOT is set to ${ANDROID_SDK_ROOT}"
else
  fail "ANDROID_SDK_ROOT is not set"
fi

if [[ -n "${ANDROID_HOME:-}" ]]; then
  ok "ANDROID_HOME is set to ${ANDROID_HOME}"
else
  warn "ANDROID_HOME is not set (ANDROID_SDK_ROOT is preferred)"
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  ok "JAVA_HOME is set to ${JAVA_HOME}"
else
  warn "JAVA_HOME is not set (Gradle may still work with system Java)"
fi

print_header "Toolchain"
check_cmd java "Java runtime"
check_cmd adb "Android Debug Bridge"
check_cmd sdkmanager "Android SDK manager"
check_cmd avdmanager "Android AVD manager"
if command -v emulator >/dev/null 2>&1; then
  ok "Android emulator: $(command -v emulator)"
elif [[ -n "${ANDROID_SDK_ROOT:-}" && -x "${ANDROID_SDK_ROOT}/emulator/emulator" ]]; then
  ok "Android emulator binary exists at ${ANDROID_SDK_ROOT}/emulator/emulator (not currently in PATH)"
else
  warn "Android emulator not found in PATH (add \$ANDROID_SDK_ROOT/emulator to PATH)"
fi

if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_OUTPUT="$(java -version 2>&1 | head -n 1)"
  echo "      $JAVA_VERSION_OUTPUT"
  if [[ "$JAVA_VERSION_OUTPUT" =~ \"([0-9]+)\. ]]; then
    JAVA_MAJOR_VERSION="${BASH_REMATCH[1]}"
    if [[ "$JAVA_MAJOR_VERSION" == "$EXPECTED_JAVA_MAJOR" ]]; then
      ok "Java major version matches project baseline ($EXPECTED_JAVA_MAJOR)"
    else
      warn "Java major version is $JAVA_MAJOR_VERSION (project baseline is $EXPECTED_JAVA_MAJOR)"
    fi
  fi
fi

if command -v adb >/dev/null 2>&1; then
  echo "      $(adb version | head -n 1)"
fi

print_header "Optional BLE Tooling"
if command -v wireshark >/dev/null 2>&1; then
  ok "Wireshark: $(command -v wireshark)"
else
  warn "Wireshark not found in PATH (needed for BLE sniffer packet capture)"
fi

if command -v tshark >/dev/null 2>&1; then
  ok "tshark: $(command -v tshark)"
else
  warn "tshark not found in PATH (optional for CLI packet inspection)"
fi

if [[ -x "$PROJECT_ROOT/scripts/run-nrfconnect.sh" ]]; then
  ok "nRF Connect wrapper: $PROJECT_ROOT/scripts/run-nrfconnect.sh"
else
  warn "nRF Connect wrapper is not executable ($PROJECT_ROOT/scripts/run-nrfconnect.sh)"
fi

if [[ -x "$PROJECT_ROOT/scripts/run-nrfutil-device.sh" ]]; then
  ok "nrfutil-device wrapper: $PROJECT_ROOT/scripts/run-nrfutil-device.sh"
else
  warn "nrfutil-device wrapper is not executable ($PROJECT_ROOT/scripts/run-nrfutil-device.sh)"
fi

if [[ -x "$PROJECT_ROOT/scripts/bootstrap-nrf-sniffer.sh" ]]; then
  ok "BLE sniffer bootstrap helper: $PROJECT_ROOT/scripts/bootstrap-nrf-sniffer.sh"
else
  warn "BLE sniffer bootstrap helper is not executable ($PROJECT_ROOT/scripts/bootstrap-nrf-sniffer.sh)"
fi

NRFUTIL_CORE_BIN=""
if [[ -x "$HOME/.nrfutil/bin/nrfutil" ]]; then
  NRFUTIL_CORE_BIN="$HOME/.nrfutil/bin/nrfutil"
elif [[ -x "$HOME/.config/nrfconnect/nrfutil" ]]; then
  NRFUTIL_CORE_BIN="$HOME/.config/nrfconnect/nrfutil"
fi

if [[ -n "$NRFUTIL_CORE_BIN" ]]; then
  ok "nrfutil core: $NRFUTIL_CORE_BIN"
  if "$NRFUTIL_CORE_BIN" list 2>/dev/null | grep -q '^ble-sniffer[[:space:]]'; then
    ok "nrfutil ble-sniffer command is installed"
  else
    warn "nrfutil ble-sniffer command is not installed yet (run scripts/bootstrap-nrf-sniffer.sh)"
  fi
else
  warn "nrfutil core not found under ~/.nrfutil/bin or ~/.config/nrfconnect"
fi

if command -v JLinkExe >/dev/null 2>&1; then
  ok "SEGGER J-Link: $(command -v JLinkExe)"
else
  warn "SEGGER J-Link not found in PATH (needed for nrfutil-device probe support)"
fi

if compgen -G "/dev/ttyACM*" >/dev/null; then
  if id -nG | tr ' ' '\n' | contains_exact_line "dialout"; then
    ok "dialout group is present for USB CDC access"
  else
    warn "USB CDC device detected but current user is missing dialout group membership"
  fi
else
  warn "No /dev/ttyACM* devices detected for Nordic DFU / UART tooling"
fi

WIRESHARK_EXTCAP_DIR="${WIRESHARK_EXTCAP_DIR:-$HOME/.local/lib/wireshark/extcap}"
if [[ -x "$WIRESHARK_EXTCAP_DIR/nrfutil-ble-sniffer-shim" ]]; then
  ok "Wireshark BLE sniffer extcap is bootstrapped"
else
  warn "Wireshark BLE sniffer extcap is not bootstrapped yet ($WIRESHARK_EXTCAP_DIR/nrfutil-ble-sniffer-shim)"
fi

print_header "SDK Components"
if command -v sdkmanager >/dev/null 2>&1; then
  INSTALLED="$(sdkmanager --list_installed 2>/dev/null || true)"
  for pkg in "platform-tools" "platforms;android-36" "build-tools;36.1.0"; do
    if echo "$INSTALLED" | contains_regex "$pkg"; then
      ok "Installed: $pkg"
    else
      warn "Missing recommended package: $pkg"
    fi
  done
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -x "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager" ]]; then
    ok "Installed: cmdline-tools;latest"
  else
    warn "Missing recommended package: cmdline-tools;latest"
  fi
else
  warn "Skipping SDK package checks because sdkmanager is missing"
fi

print_header "Connected Devices"
if command -v adb >/dev/null 2>&1; then
  if [[ "$HAVE_RG" == "true" ]]; then
    DEVICE_LINES="$(adb devices | tail -n +2 | rg -v '^\s*$' || true)"
  else
    DEVICE_LINES="$(adb devices | tail -n +2 | grep -Ev '^[[:space:]]*$' || true)"
  fi
  if [[ -n "$DEVICE_LINES" ]]; then
    ok "ADB device detected"
    echo "$DEVICE_LINES" | sed 's/^/      /'
  else
    warn "No ADB devices connected"
  fi
fi

print_header "Project Build Sanity"
if ./scripts/gradle-safe.sh :app:compileDebugKotlin >/tmp/ergometer-dev-doctor-build.log 2>&1; then
  ok "Gradle compile check passed (:app:compileDebugKotlin)"
else
  fail "Gradle compile check failed (:app:compileDebugKotlin)"
  echo "      See /tmp/ergometer-dev-doctor-build.log for details"
fi

print_header "Summary"
echo "Passed:  $PASS_COUNT"
echo "Warnings:$WARN_COUNT"
echo "Failed:  $FAIL_COUNT"

if [[ $FAIL_COUNT -eq 0 ]]; then
  echo
  echo "Environment check completed without blocking issues."
  exit 0
fi

echo
echo "Environment check found blocking issues."
exit 1

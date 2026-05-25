#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
UNSIGNED_APK="$PROJECT_ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
OUTPUT_DIR="$PROJECT_ROOT/app/build/outputs/apk/release/local-signed"
ALIGNED_APK="$OUTPUT_DIR/app-release-aligned.apk"
SIGNED_APK="$OUTPUT_DIR/app-release-debugsigned.apk"
DEBUG_KEYSTORE="${LOCAL_DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
DEBUG_KEY_ALIAS="${LOCAL_DEBUG_KEY_ALIAS:-androiddebugkey}"
DEBUG_STORE_PASS="${LOCAL_DEBUG_KEYSTORE_PASSWORD:-android}"
DEBUG_KEY_PASS="${LOCAL_DEBUG_KEY_PASSWORD:-android}"

usage() {
  cat <<'EOF'
Usage: ./scripts/sign-local-release-apk.sh [--install] [--serial <adb-serial>]

Signs app/build/outputs/apk/release/app-release-unsigned.apk with the local
Android debug keystore and writes the installable output to:
app/build/outputs/apk/release/local-signed/app-release-debugsigned.apk

Options:
  --install          Install the signed APK with adb after signing.
  --serial <serial>  Target a specific adb device for --install.
  --help             Show this help text.

Environment overrides:
  LOCAL_DEBUG_KEYSTORE
  LOCAL_DEBUG_KEY_ALIAS
  LOCAL_DEBUG_KEYSTORE_PASSWORD
  LOCAL_DEBUG_KEY_PASSWORD
EOF
}

INSTALL_APK=false
ADB_SERIAL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install)
      INSTALL_APK=true
      shift
      ;;
    --serial)
      if [[ $# -lt 2 ]]; then
        echo "--serial requires a device serial" >&2
        exit 1
      fi
      ADB_SERIAL="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "$SDK_ROOT" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must be set." >&2
  exit 1
fi

if [[ ! -f "$UNSIGNED_APK" ]]; then
  cat >&2 <<EOF
Unsigned release APK not found: $UNSIGNED_APK
Build it first with:
  ./gradlew :app:assembleRelease --no-daemon -Pergometer.release.minify=true
EOF
  exit 1
fi

if [[ ! -f "$DEBUG_KEYSTORE" ]]; then
  echo "Debug keystore not found: $DEBUG_KEYSTORE" >&2
  exit 1
fi

BUILD_TOOLS_DIR="$(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
if [[ -z "$BUILD_TOOLS_DIR" ]]; then
  echo "No Android build-tools directory found under $SDK_ROOT/build-tools" >&2
  exit 1
fi

APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
ZIPALIGN="$BUILD_TOOLS_DIR/zipalign"

if [[ ! -x "$APKSIGNER" || ! -x "$ZIPALIGN" ]]; then
  echo "Expected apksigner and zipalign under $BUILD_TOOLS_DIR" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
rm -f "$ALIGNED_APK" "$SIGNED_APK"

"$ZIPALIGN" -f -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"
cp "$ALIGNED_APK" "$SIGNED_APK"

"$APKSIGNER" sign \
  --ks "$DEBUG_KEYSTORE" \
  --ks-key-alias "$DEBUG_KEY_ALIAS" \
  --ks-pass "pass:$DEBUG_STORE_PASS" \
  --key-pass "pass:$DEBUG_KEY_PASS" \
  "$SIGNED_APK"

"$APKSIGNER" verify --verbose "$SIGNED_APK"

echo "Signed APK ready: $SIGNED_APK"

if [[ "$INSTALL_APK" == true ]]; then
  if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found in PATH" >&2
    exit 1
  fi

  ADB_ARGS=()
  if [[ -n "$ADB_SERIAL" ]]; then
    ADB_ARGS=(-s "$ADB_SERIAL")
  fi

  adb "${ADB_ARGS[@]}" install --no-streaming -r "$SIGNED_APK"
fi

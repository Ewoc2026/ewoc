#!/usr/bin/env bash

set -euo pipefail

APP_ID="io.github.ewoc2026.ewoc"
FDROIDDATA_DIR="${FDROIDDATA_DIR:-/tmp/fdroiddata-ewoc-submission}"
GITHUB_REPO="${GITHUB_REPO:-Ewoc2026/ewoc}"
SIGNING_ENV="${SIGNING_ENV:-}"
VERSION_NAME=""
VERSION_CODE=""
SOURCE_COMMIT=""
OUTPUT_APK=""
UPLOAD=false
CREATE_RELEASE=false
VERIFY_REFERENCE=false
TAG_NAME=""
RELEASE_NOTES=""

usage() {
  cat <<'EOF'
Usage:
  scripts/fdroid-reference-apk.sh --version-name X.Y.Z --version-code N --signing-env /path/to/release.env [options]

Builds Ewoc through fdroidserver, signs the fdroidserver-built unsigned APK as
a reproducible-build reference APK, and optionally uploads/verifies it against
the GitHub Release expected by fdroiddata.

Required:
  --version-name X.Y.Z       Version name, for example 1.0.1.
  --version-code N           Android versionCode, for example 5.
  --signing-env PATH         Env file that exports ERGOMETER_RELEASE_* values.

Options:
  --fdroiddata-dir PATH      fdroiddata checkout (default: /tmp/fdroiddata-ewoc-submission).
  --repo OWNER/REPO          GitHub repository for release upload (default: Ewoc2026/ewoc).
  --tag vX.Y.Z               GitHub tag/release name (default: v<version-name>).
  --source-commit SHA        Expected public source commit; checked against fdroiddata metadata when set.
  --output PATH              Output APK path (default: /tmp/ewoc-<version-name>-rb.apk).
  --upload                   Upload the APK to the matching GitHub Release with --clobber.
  --create-release           Create the GitHub Release if it does not exist.
  --release-notes TEXT       Notes used only when --create-release creates a release.
  --verify-reference         Run final fdroid build with Binaries metadata after upload or against an existing asset.
  --help                     Show this help.

Environment:
  GH_TOKEN may be used by gh. This script never prints token or signing values.

Notes:
  The reference APK is signed with apksigner --alignment-preserved true. Without
  that option, F-Droid's signature-copy comparison can fail even when the APK
  verifies normally.
EOF
}

die() {
  echo "error: $*" >&2
  exit 1
}

log() {
  echo "==> $*"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version-name)
      VERSION_NAME="${2:-}"
      shift 2
      ;;
    --version-code)
      VERSION_CODE="${2:-}"
      shift 2
      ;;
    --signing-env)
      SIGNING_ENV="${2:-}"
      shift 2
      ;;
    --fdroiddata-dir)
      FDROIDDATA_DIR="${2:-}"
      shift 2
      ;;
    --repo)
      GITHUB_REPO="${2:-}"
      shift 2
      ;;
    --tag)
      TAG_NAME="${2:-}"
      shift 2
      ;;
    --source-commit)
      SOURCE_COMMIT="${2:-}"
      shift 2
      ;;
    --output)
      OUTPUT_APK="${2:-}"
      shift 2
      ;;
    --upload)
      UPLOAD=true
      shift
      ;;
    --create-release)
      CREATE_RELEASE=true
      shift
      ;;
    --release-notes)
      RELEASE_NOTES="${2:-}"
      shift 2
      ;;
    --verify-reference)
      VERIFY_REFERENCE=true
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

[[ -n "$VERSION_NAME" ]] || die "--version-name is required"
[[ -n "$VERSION_CODE" ]] || die "--version-code is required"
[[ "$VERSION_CODE" =~ ^[0-9]+$ ]] || die "--version-code must be an integer"
[[ -n "$SIGNING_ENV" ]] || die "--signing-env is required"
[[ -f "$SIGNING_ENV" ]] || die "signing env file not found: $SIGNING_ENV"
[[ -d "$FDROIDDATA_DIR" ]] || die "fdroiddata dir not found: $FDROIDDATA_DIR"

TAG_NAME="${TAG_NAME:-v$VERSION_NAME}"
OUTPUT_APK="${OUTPUT_APK:-/tmp/ewoc-$VERSION_NAME-rb.apk}"
RELEASE_NOTES="${RELEASE_NOTES:-Ewoc $VERSION_NAME reproducible-build reference APK.}"

METADATA_FILE="$FDROIDDATA_DIR/metadata/$APP_ID.yml"
[[ -f "$METADATA_FILE" ]] || die "metadata not found: $METADATA_FILE"

command -v fdroid >/dev/null 2>&1 || die "fdroid not found in PATH"
command -v python3 >/dev/null 2>&1 || die "python3 not found in PATH"
if [[ "$UPLOAD" == true || "$CREATE_RELEASE" == true ]]; then
  command -v gh >/dev/null 2>&1 || die "gh not found in PATH"
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
APKSIGNER="$(find "$SDK_ROOT/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
[[ -n "$APKSIGNER" && -x "$APKSIGNER" ]] || die "apksigner not found under $SDK_ROOT/build-tools"

log "Checking fdroiddata metadata for $APP_ID"
python3 - "$METADATA_FILE" "$VERSION_NAME" "$VERSION_CODE" "$SOURCE_COMMIT" <<'PY'
import re
import sys
from pathlib import Path

metadata = Path(sys.argv[1]).read_text()
version_name = sys.argv[2]
version_code = sys.argv[3]
source_commit = sys.argv[4]

checks = {
    f"versionName: {version_name}": f"metadata does not contain versionName: {version_name}",
    f"versionCode: {version_code}": f"metadata does not contain versionCode: {version_code}",
    "Binaries:": "metadata does not contain Binaries",
    "AllowedAPKSigningKeys:": "metadata does not contain AllowedAPKSigningKeys",
}
for needle, message in checks.items():
    if needle not in metadata:
        raise SystemExit(message)

if source_commit and f"commit: {source_commit}" not in metadata:
    raise SystemExit(f"metadata does not contain commit: {source_commit}")

if not re.search(r"gradle:\s*\n\s*-\s*yes\b", metadata):
    raise SystemExit("metadata does not contain gradle: [yes] style marker")
PY

log "Running fdroid lint"
(
  cd "$FDROIDDATA_DIR"
  fdroid lint "$APP_ID"
)

log "Building unsigned APK with local Binaries disabled"
TMP_METADATA="$(mktemp)"
cp "$METADATA_FILE" "$TMP_METADATA"
restore_metadata() {
  cp "$TMP_METADATA" "$METADATA_FILE"
  rm -f "$TMP_METADATA"
}
trap restore_metadata EXIT

python3 - "$METADATA_FILE" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text()
text = re.sub(r"^Binaries:.*\n", "", text, flags=re.MULTILINE)
text = re.sub(r"^AllowedAPKSigningKeys:.*\n\n?", "", text, flags=re.MULTILINE)
path.write_text(text)
PY

(
  cd "$FDROIDDATA_DIR"
  fdroid build -v -t --no-tarball "$APP_ID:$VERSION_CODE"
)

restore_metadata
trap - EXIT

UNSIGNED_APK="$FDROIDDATA_DIR/tmp/${APP_ID}_${VERSION_CODE}.apk"
[[ -f "$UNSIGNED_APK" ]] || die "fdroid unsigned APK not found: $UNSIGNED_APK"

log "Signing fdroidserver-built APK"
set -a
# shellcheck disable=SC1090
source "$SIGNING_ENV"
set +a

required_signing_vars=(
  ERGOMETER_RELEASE_STORE_FILE
  ERGOMETER_RELEASE_STORE_PASSWORD
  ERGOMETER_RELEASE_KEY_ALIAS
  ERGOMETER_RELEASE_KEY_PASSWORD
)
for name in "${required_signing_vars[@]}"; do
  [[ -n "${!name:-}" ]] || die "$name is not set by signing env"
done
[[ -f "$ERGOMETER_RELEASE_STORE_FILE" ]] || die "release keystore not found"

rm -f "$OUTPUT_APK"
"$APKSIGNER" sign \
  --alignment-preserved true \
  --ks "$ERGOMETER_RELEASE_STORE_FILE" \
  --ks-key-alias "$ERGOMETER_RELEASE_KEY_ALIAS" \
  --ks-pass env:ERGOMETER_RELEASE_STORE_PASSWORD \
  --key-pass env:ERGOMETER_RELEASE_KEY_PASSWORD \
  --out "$OUTPUT_APK" \
  "$UNSIGNED_APK"

log "Verifying signed reference APK"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_APK" \
  | sed -n '/Verified using/p;/SHA-256/p'
sha256sum "$OUTPUT_APK"

if [[ "$CREATE_RELEASE" == true ]]; then
  if gh release view "$TAG_NAME" --repo "$GITHUB_REPO" >/dev/null 2>&1; then
    log "GitHub Release $TAG_NAME already exists"
  else
    log "Creating GitHub Release $TAG_NAME"
    gh release create "$TAG_NAME" --repo "$GITHUB_REPO" --title "Ewoc $VERSION_NAME" --notes "$RELEASE_NOTES"
  fi
fi

if [[ "$UPLOAD" == true ]]; then
  log "Uploading reference APK to GitHub Release $TAG_NAME"
  gh release upload "$TAG_NAME" "$OUTPUT_APK" --repo "$GITHUB_REPO" --clobber
fi

if [[ "$VERIFY_REFERENCE" == true ]]; then
  log "Running final fdroid reproducible-build comparison"
  (
    cd "$FDROIDDATA_DIR"
    fdroid build -v -t --no-tarball "$APP_ID:$VERSION_CODE"
  )
fi

log "Reference APK ready: $OUTPUT_APK"

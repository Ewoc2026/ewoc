# Release Checklist

This checklist keeps public Ewoc releases, F-Droid packaging, GitHub tags,
metadata, and local validation aligned. It is intentionally boring: every
release should leave behind enough proof that a later session can resume
without reconstructing the path from memory.

## Release Principles

- The public release source of truth is `https://github.com/Ewoc2026/ewoc`.
- Do not push the old private `origin`; public release work goes through a
  generated public snapshot.
- Keep `versionName`, `versionCode`, `CHANGELOG.md`, Fastlane changelogs, tags,
  GitHub Releases, and fdroiddata metadata in sync.
- Validate trainer-sensitive changes on real hardware before calling a release
  ready.
- Keep signing keys, tokens, keystores, and environment files outside the
  repository. Do not print secrets in terminals, logs, screenshots, docs, or
  chat.
- Prefer small release commits with clear validation notes over a large
  undocumented batch.

## Repositories And Working Copies

- Public source repository:
  `https://github.com/Ewoc2026/ewoc`
- Typical maintainer public snapshot checkout:
  `/tmp/ewoc-public-snapshot`
- Typical fdroiddata submission checkout:
  `/tmp/fdroiddata-ewoc-submission`
- F-Droid metadata draft:
  `docs/fdroiddata-metadata-draft.yml`
- F-Droid merge request:
  `https://gitlab.com/fdroid/fdroiddata/-/merge_requests/39065`
- Published F-Droid package:
  `https://f-droid.org/packages/io.github.ewoc2026.ewoc/`

The initial inclusion MR merged on June 22, 2026. Keep the MR link as release
history; normal future versions should use tag-based auto updates unless
fdroiddata needs a manual correction.

Maintainer token files, signing keystores, and signing environment files belong
outside this repository. Private handoff notes may record their local paths, but
public docs should never contain secret values.

## Release Decision Gate

Before changing version numbers, decide:

- What user-visible change is being released.
- Whether this is an Android app release, a desktop/editor release, a
  documentation-only public snapshot, or a combined release.
- The next `versionName` and monotonically increasing Android `versionCode`.
- Whether screenshots or Fastlane metadata need updates.
- Whether fdroiddata needs a metadata change or normal tag-based auto-update is
  enough.
- Whether the release needs a reproducible-build reference APK in GitHub
  Releases.

## Source Updates

For an Android app release:

- Update `versionCode` and `versionName` in `app/build.gradle.kts`.
- Update `CHANGELOG.md`.
- Add or update
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
- Update `README.md`, `docs/user-guide/`, `docs/privacy.md`, or architecture
  docs only when release truth changed.
- Update screenshots under
  `fastlane/metadata/android/en-US/images/tenInchScreenshots/` when the
  rider-facing experience changed materially.
- From the maintainer private branch, run the public snapshot marker checks
  through
  `./scripts/create-ewoc-public-snapshot.sh /tmp/ewoc-public-snapshot`.
  The generated public repository intentionally does not carry this private
  snapshot helper.

For a documentation-only public snapshot:

- Update the relevant docs in the private branch.
- Generate the public snapshot.
- Verify the snapshot diff contains only intended public files.

## Local Validation

Run the smallest relevant set first, then broaden before release:

```bash
./scripts/gradle-safe.sh :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
./scripts/gradle-safe.sh :app:testDebugUnitTest --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
./scripts/gradle-safe.sh :apps:desktop:compileKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
./scripts/gradle-safe.sh :modules:ewo-core:jvmTest --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
git diff --check
```

For unsigned release build validation:

```bash
env \
  -u ERGOMETER_RELEASE_STORE_FILE \
  -u ERGOMETER_RELEASE_STORE_PASSWORD \
  -u ERGOMETER_RELEASE_KEY_ALIAS \
  -u ERGOMETER_RELEASE_KEY_PASSWORD \
  ./scripts/gradle-safe.sh :app:assembleRelease :app:lintRelease --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
```

For trainer-sensitive changes:

- Use the debug mock trainer for fast iteration.
- Validate final behavior on a real trainer.
- Prefer the in-app debug overlay once a rider is actively pedaling.
- Capture screenshots from real hardware when metadata or documentation needs
  them.

## Public Snapshot

Generate and inspect the public tree from the maintainer private branch:

```bash
./scripts/create-ewoc-public-snapshot.sh /tmp/ewoc-public-snapshot
cd /tmp/ewoc-public-snapshot
git status --short --branch
git diff --stat
```

After changing into `/tmp/ewoc-public-snapshot`, run only commands that exist
in the generated public tree. The snapshot helper itself remains in the
maintainer branch.

Before pushing:

- Confirm the generated snapshot has no private domains, old package ids,
  backend-ingest markers, OpenAI endpoint markers, Google Services config, or
  signing material.
- Run the relevant build smoke checks from the snapshot itself.
- Commit the public snapshot with a message that describes the public change.
- Push to `https://github.com/Ewoc2026/ewoc`.
- Confirm GitHub Actions is green for the pushed commit.

## Tag And GitHub Release

For an Android release:

```bash
cd /tmp/ewoc-public-snapshot
git tag -a vX.Y.Z -m "Ewoc X.Y.Z"
git push origin vX.Y.Z
git rev-parse vX.Y.Z^{}
```

Then:

- Confirm GitHub Actions is green for the tag.
- Record the tag commit hash in release notes or handoff docs.
- Create or update the GitHub Release for `vX.Y.Z`.

## Reproducible-Build Reference APK

Use this path when F-Droid metadata has a `Binaries` entry. The reference APK
must match the fdroidserver-built source output.

Preferred helper path:

```bash
scripts/fdroid-reference-apk.sh \
  --version-name X.Y.Z \
  --version-code <versionCode> \
  --source-commit <public-source-commit> \
  --signing-env /path/to/signing-env-file \
  --create-release \
  --upload \
  --verify-reference
```

The helper temporarily disables `Binaries` in the local fdroiddata metadata to
produce the unsigned APK, restores the metadata, signs the fdroidserver-built
APK with `--alignment-preserved true`, optionally uploads it to GitHub Releases,
and can run the final reproducible-build comparison.

Manual path:

Build through fdroiddata first:

```bash
cd /tmp/fdroiddata-ewoc-submission
fdroid lint io.github.ewoc2026.ewoc
fdroid build -v -t --no-tarball io.github.ewoc2026.ewoc:<versionCode>
```

Sign the exact unsigned APK produced by fdroidserver:

```bash
set -a
source /path/to/signing-env-file
set +a

cp /tmp/fdroiddata-ewoc-submission/tmp/io.github.ewoc2026.ewoc_<versionCode>.apk \
  /tmp/ewoc-X.Y.Z-fdroid-unsigned.apk

apksigner sign \
  --alignment-preserved true \
  --ks "$ERGOMETER_RELEASE_STORE_FILE" \
  --ks-key-alias "$ERGOMETER_RELEASE_KEY_ALIAS" \
  --ks-pass env:ERGOMETER_RELEASE_STORE_PASSWORD \
  --key-pass env:ERGOMETER_RELEASE_KEY_PASSWORD \
  --out /tmp/ewoc-X.Y.Z-rb.apk \
  /tmp/ewoc-X.Y.Z-fdroid-unsigned.apk

apksigner verify --print-certs /tmp/ewoc-X.Y.Z-rb.apk
```

Keep `--alignment-preserved true` for the reference APK. Without it, recent
`apksigner` versions may realign the APK while signing; the APK can still
verify normally, but F-Droid's signature-copy reproducible-build comparison can
fail with an APK Signature Scheme digest mismatch.

Upload `/tmp/ewoc-X.Y.Z-rb.apk` to the matching GitHub Release as
`ewoc-X.Y.Z-rb.apk`.

Do not use a normal Gradle-signed worktree APK as the F-Droid reference. During
the first release, that path produced reproducible-build differences in Android
Gradle Plugin version-control metadata and dex/profile outputs.

## fdroiddata

Expected metadata shape for Ewoc:

- `Categories: Workout`
- `License: GPL-3.0-or-later`
- `RepoType: git`
- `Repo: https://github.com/Ewoc2026/ewoc.git`
- `Binaries: https://github.com/Ewoc2026/ewoc/releases/download/v%v/ewoc-%v-rb.apk`
- `AllowedAPKSigningKeys` set to
  `4c916c9c69984f8aa9313838ca8cd7f8938af62500b6e85afb5e1afba5451e63`
- Build `commit` set to the full public source commit hash.
- Add new releases as additional `Builds:` entries instead of replacing
  already-published build blocks. Keep `CurrentVersion` and
  `CurrentVersionCode` pointed at the newest submitted release.
- `subdir: app`
- `gradle: [yes]`
- `AutoUpdateMode: Version`
- `UpdateCheckMode: Tags`

Keep `gradle: [yes]` as a YAML list. YAML boolean `true` makes fdroidserver try
to run the wrong Gradle task.

Useful commands:

```bash
cd /tmp/fdroiddata-ewoc-submission
fdroid lint io.github.ewoc2026.ewoc
fdroid build -v -t --no-tarball io.github.ewoc2026.ewoc:<versionCode>
git status --short --branch
git log --oneline --decorate --max-count=5
```

Push updates to the existing MR branch:

```bash
git push fork add-ewoc
```

Check the current MR status:

```bash
./scripts/fdroid-mr-status.sh
```

If the local fdroidserver version cannot download Gradle because the
distribution hash is missing, use a workstation-only helper patch. Do not carry
that helper patch into fdroiddata metadata unless F-Droid reviewers request it.

## Post-Release

After a release or F-Droid follow-up:

- Verify the GitHub commit, tag, release asset, and Actions result.
- Verify the fdroiddata MR, pipeline, and reviewer comments.
- When F-Droid publishes the build, install from F-Droid and confirm app id,
  version name, version code, trainer connection, and a simple ride path.
- Update only the docs whose source truth changed.
- Refresh private handoff notes when migration or release state changed.

## Recovery Notes

- If a GitLab token is needed, read it from an ignored local secret file into a
  temporary variable and never print it.
- If a GitHub Release asset must be replaced, prefer a new deterministic file
  name when reproducibility or CDN cache behavior is uncertain.
- If an fdroiddata reproducible build diff appears, sign the fdroidserver-built
  unsigned artifact again before investigating source differences.
- If the public snapshot unexpectedly contains private markers, fix the source
  or snapshot script before pushing.

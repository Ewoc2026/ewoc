# F-Droid Preparation

Updated: 2026-06-28

Ewoc targets the main F-Droid repository for its first public Android
distribution path. This note tracks readiness and review follow-up for the
fdroiddata metadata merge request and follow-up updates.

## Current App Identity

- Application ID: `io.github.ewoc2026.ewoc`
- License: `GPL-3.0-or-later`
- Source: `https://github.com/Ewoc2026/ewoc`
- Issue tracker: `https://github.com/Ewoc2026/ewoc/issues`
- Current prepared update: `versionName = "1.0.3"`, `versionCode = 7`
- Current published F-Droid version: `versionName = "1.0.1"`,
  `versionCode = 5`
- First F-Droid source tag: `v1.0.0`; current F-Droid review update tag:
  `v1.0.1`; prepared update tag: `v1.0.3`

## Initial Readiness Audit

- Public source repository exists and has a GPL license file.
- Android app dependencies are pulled from Google Maven or Maven Central.
- No Firebase, Google Play Services, Crashlytics, analytics, advertising SDK,
  Google Play Billing, Health Connect, or backend upload dependency is active
  in the Android app dependency graph.
- The stale Play Billing version-catalog alias has been removed so dependency
  scans do not report a retired library as part of the current source.
- The Android app does not declare `INTERNET` or `ACCESS_NETWORK_STATE`.
  Documentation, changelog, privacy, and issue links open in the user's browser;
  there is no in-app network client or app backend.
- Release signing is configured outside the repository. GitHub Releases host
  reproducible-build reference APKs for published F-Droid versions.
- `:app:assembleRelease` succeeds without private signing environment
  variables and produces `app-release-unsigned.apk`.

## Update Work Remaining

- Monitor fdroiddata MR `!41492` for `versionCode=7`; local fdroiddata commit
  `9c3fb18032` (`Update Ewoc to 1.0.3`) is pushed to branch
  `Jyriwee/fdroiddata:update-ewoc-1.0.3`. It addresses reviewer feedback by
  keeping the existing `1.0.1` build block, adding a new `1.0.3` build block,
  and updating `CurrentVersion*`.
- Verify the resulting F-Droid build after the update lands in fdroiddata.

## GitLab Access

Authenticated GitLab access may be needed for fdroiddata pushes or reviewer
follow-up. Keep any token in an ignored local secret file or password manager,
read it only into a temporary shell variable when needed, and never print it,
commit it, paste it into chat, or leave it embedded in git remotes.

## Validation

- `./scripts/gradle-safe.sh :app:compileDebugKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false`
- `env -u ERGOMETER_RELEASE_STORE_FILE -u ERGOMETER_RELEASE_STORE_PASSWORD -u ERGOMETER_RELEASE_KEY_ALIAS -u ERGOMETER_RELEASE_KEY_PASSWORD ./scripts/gradle-safe.sh :app:assembleRelease --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false`
- `wc -c fastlane/metadata/android/en-US/short_description.txt fastlane/metadata/android/en-US/changelogs/4.txt`
- Captured `fastlane/metadata/android/en-US/images/tenInchScreenshots/`
  from Samsung tablet `SM-X210` with app locale `en-US`. The selected images
  cover setup flow, rider profile, workout file import/editor entry, and the
  Android workout editor. A live-session mock-trainer capture was intentionally
  excluded because it contained debug-only copy.
- Added a fifth `tenInchScreenshots/5.png` capture from a real trainer-backed
  live session. The original device screenshot is retained locally under
  `.local/fdroid-screenshots/originals/` and the metadata copy is cropped to
  remove the Android status bar.

## Known F-Droid Review Notes

- The app uses Bluetooth permissions for FTMS trainer and heart-rate sensor
  communication.
- The app stores workout/session/export state locally. It has no Android
  network permission and only opens external links when the user chooses
  documentation, changelog, privacy, or issue actions.
- Health Connect and Play Billing were intentionally removed from the first
  public build to simplify F-Droid and privacy review.

## fdroiddata Draft

The local draft lives at `docs/fdroiddata-metadata-draft.yml`. It assumes:

- the prepared F-Droid update tag is `v1.0.3`
- `versionName = "1.0.3"` and `versionCode = 7`
- source commit
  `0f2b36cb35b676de8ccb70a2eb6e4996f4fed81f`
- F-Droid builds from subdirectory `app`
- future updates can use tag-based auto-update metadata
- F-Droid's metadata lint expects file links against the default branch to use
  `/HEAD/`, so the draft changelog URL intentionally avoids `/main/`

The fdroiddata merge request is open at
`https://gitlab.com/fdroid/fdroiddata/-/merge_requests/39065`. Local testing
uses a temporary fdroiddata-style workspace under `/tmp` with the current
fdroiddata category config copied in for lint parity.

The review follow-up for maintainer questions about the `INTERNET` permission is
to move the MR to `v1.0.1` / `versionCode=5`, whose release manifest removes
both `INTERNET` and WorkManager's inherited `ACCESS_NETWORK_STATE` permission.
Public GitHub commit `071377379c510b8a3fef3ffe4f2e3ff2975f19cd` is tagged as
`v1.0.1`, and GitHub Release `v1.0.1` hosts `ewoc-1.0.1-rb.apk`.

Initial reviewer feedback from `linsui` is addressed in fdroiddata commit
`690e778b`
(`Address Ewoc review feedback`) on branch `add-ewoc`:

- category `Workout`
- source commit `8776ff55a72cd5caa7efb465c7770e9f2a358a32`
- `AutoUpdateMode: Version`
- `Binaries: https://github.com/Ewoc2026/ewoc/releases/download/v%v/ewoc-%v-rb.apk`
- `AllowedAPKSigningKeys: 4c916c9c69984f8aa9313838ca8cd7f8938af62500b6e85afb5e1afba5451e63`

GitHub Release `v1.0.0` now hosts `ewoc-1.0.0-rb.apk`, which was produced by
signing the F-Droid-built unsigned APK with the external release key. This keeps
the reference binary aligned with F-Droid's cleaned build environment instead of
the local Gradle worktree build.

Validated locally:

- `fdroid readmeta`
- `fdroid lint io.github.ewoc2026.ewoc`
- `yamllint metadata/io.github.ewoc2026.ewoc.yml`
- `fdroid build -v -t --no-tarball io.github.ewoc2026.ewoc:4` passed from a
  committed local fdroiddata branch after adding a local Gradle 9.3.1 hash to
  the workstation `gradlew-fdroid` helper.
- The same `fdroid build` path also passed reproducible-build comparison after
  adding `Binaries` and `AllowedAPKSigningKeys`; F-Droid downloaded
  `ewoc-1.0.0-rb.apk`, copied its signature to the locally built APK, verified
  it, and reported
  `compared built binary to supplied reference binary successfully`.
- `fdroid build -v -t --no-tarball io.github.ewoc2026.ewoc:5` passed after the
  `v1.0.1` update; F-Droid downloaded `ewoc-1.0.1-rb.apk`, copied its signature
  to the locally built APK, verified it, and reported
  `compared built binary to supplied reference binary successfully`.
- The `v1.0.1` reference APK must be signed with
  `apksigner --alignment-preserved true`; without that option, recent
  `apksigner` versions can create a normally valid APK whose signature block
  fails F-Droid's signature-copy reproducible-build comparison.
- The initial `v1.0.2` FIT export follow-up was superseded before F-Droid
  publication after rechecking Garmin FIT profile field numbers.
- The `v1.0.3` FIT export fix is prepared in public commit
  `0f2b36cb35b676de8ccb70a2eb6e4996f4fed81f`, tag `v1.0.3`, and GitHub
  Release `v1.0.3`. The release asset `ewoc-1.0.3-rb.apk` has SHA-256
  `95bc13047ef9942246c6bb80607627529aa5d1474222b79c6095df213fdfd84e`.
  Local fdroidserver validation passed for `io.github.ewoc2026.ewoc:7`,
  including reproducible-build comparison against the uploaded reference APK.
  fdroiddata MR `!41492` is open at
  `https://gitlab.com/fdroid/fdroiddata/-/merge_requests/41492`; refreshed
  pipeline `2634905827` completed successfully after the metadata was updated
  to add the new build block instead of replacing the old one.

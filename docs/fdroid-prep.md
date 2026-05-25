# F-Droid Preparation

Updated: 2026-05-25

Ewoc targets the main F-Droid repository for its first public Android
distribution path. This note tracks local readiness before opening the
fdroiddata metadata merge request.

## Current App Identity

- Application ID: `io.github.ewoc2026.ewoc`
- License: `GPL-3.0-or-later`
- Source: `https://github.com/Ewoc2026/ewoc`
- Issue tracker: `https://github.com/Ewoc2026/ewoc/issues`
- Current version: `versionName = "1.0.0"`, `versionCode = 4`
- First F-Droid source tag: `v1.0.0`

## Initial Readiness Audit

- Public source repository exists and has a GPL license file.
- Android app dependencies are pulled from Google Maven or Maven Central.
- No Firebase, Google Play Services, Crashlytics, analytics, advertising SDK,
  Google Play Billing, Health Connect, or backend upload dependency is active
  in the Android app dependency graph.
- The stale Play Billing version-catalog alias has been removed so dependency
  scans do not report a retired library as part of the current source.
- The Android app still declares `INTERNET` and `ACCESS_NETWORK_STATE`.
  Current intended use is opening documentation, changelog, privacy, and issue
  links in a browser; there is no app backend.
- Release signing is configured outside the repository, but F-Droid can build
  and sign its own APK unless reproducible builds are pursued for the first
  submission.
- `:app:assembleRelease` succeeds without private signing environment
  variables and produces `app-release-unsigned.apk`.

## Submission Work Remaining

- Run a fresh public snapshot private-marker check before submission.
- Open the fdroiddata merge request with `v1.0.0` as the first build commit.
- Prefer auto-update metadata once the first F-Droid build works, so future
  releases only need a version bump and tag.
- Attempt reproducible-build setup after the basic F-Droid build path is proven.

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
- The app stores workout/session/export state locally and opens external links
  only when the user chooses documentation, changelog, privacy, or issue
  actions.
- Health Connect and Play Billing were intentionally removed from the first
  public build to simplify F-Droid and privacy review.

## fdroiddata Draft

The local draft lives at `docs/fdroiddata-metadata-draft.yml`. It assumes:

- the first public F-Droid tag is `v1.0.0`
- `versionName = "1.0.0"` and `versionCode = 4`
- F-Droid builds from subdirectory `app`
- future updates can use tag-based auto-update metadata
- F-Droid's metadata lint expects file links against the default branch to use
  `/HEAD/`, so the draft changelog URL intentionally avoids `/main/`

The draft is not yet submitted to fdroiddata. Local testing uses a temporary
fdroiddata-style workspace under `/tmp` with the current fdroiddata category
config copied in for lint parity.

Validated locally:

- `fdroid readmeta`
- `fdroid lint io.github.ewoc2026.ewoc`
- `yamllint metadata/io.github.ewoc2026.ewoc.yml`
- `fdroid build -v -t --no-tarball io.github.ewoc2026.ewoc:4` passed from a
  committed local fdroiddata branch after adding a local Gradle 9.3.1 hash to
  the workstation `gradlew-fdroid` helper.

# F-Droid Status

Updated: 2026-07-09

Purpose:
- Record current F-Droid-facing source truth.
- Keep repeatable release procedure in `docs/release-checklist.md`.
- Leave completed review chronology in Git and fdroiddata history.

## Current Package

- Application ID: `io.github.ewoc2026.ewoc`
- License: `GPL-3.0-or-later`
- Source: `https://github.com/Ewoc2026/ewoc`
- Issues: `https://github.com/Ewoc2026/ewoc/issues`
- Published version: `1.0.3`
- Published version code: `7`
- Published package:
  `https://f-droid.org/packages/io.github.ewoc2026.ewoc/`
- Public source tag: `v1.0.3`
- Public source commit:
  `0f2b36cb35b676de8ccb70a2eb6e4996f4fed81f`

## Current Readiness

- The Android app has no active Google Play Billing, Health Connect, Firebase,
  Crashlytics, analytics, advertising, or backend-upload dependency.
- The final Android manifest does not declare `INTERNET` or
  `ACCESS_NETWORK_STATE`.
- Documentation and issue links open in the user's browser only after an
  explicit user action.
- Bluetooth permissions support FTMS trainer and heart-rate sensor
  communication.
- Workout, session, settings, and FIT export data stay local to the device
  unless the user explicitly shares a file.
- Unsigned release APK assembly works without private signing variables.
- GitHub Releases host developer-signed reproducible-build reference APKs.

## Release Metadata Contract

- Add new fdroiddata versions as additional `Builds:` entries. Do not replace
  already published build blocks.
- Keep `CurrentVersion` and `CurrentVersionCode` on the newest submitted
  release.
- Use the full public source commit hash for each build.
- Keep `gradle: [yes]` as a YAML list.
- Keep `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`.
- Reference APKs must be produced from the fdroidserver-built unsigned APK and
  signed with `apksigner --alignment-preserved true`.
- Expected signing certificate SHA-256:
  `4c916c9c69984f8aa9313838ca8cd7f8938af62500b6e85afb5e1afba5451e63`.

## Current Draft

`docs/fdroiddata-metadata-draft.yml` mirrors the latest known Ewoc metadata for
local rehearsal. The authoritative published metadata is in fdroiddata.

Before the next release:

1. Update app version, changelog, and Fastlane changelog together.
2. Generate and validate the public source snapshot.
3. Tag the exact public source commit.
4. Build and sign the reference APK through the repeatable helper.
5. Validate fdroiddata lint, build, and reproducible comparison.
6. Confirm publication, then run a real-device trainer smoke.

See `docs/release-checklist.md` for commands and recovery notes.

## Latest Field Evidence

- F-Droid `1.0.3` is publicly available.
- A July 9, 2026 Samsung `SM-X210` ride with a real trainer and external HR
  strap passed trainer control, telemetry, session stop/summary, and FIT export
  readability.
- The workout-reselection runner-state finding from that smoke is fixed
  locally but is not yet a new published F-Droid version.

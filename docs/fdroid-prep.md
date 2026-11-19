# F-Droid Status

Updated: 2026-11-19

Codex reconciled this release record with the public GitHub release and
fdroiddata metadata during local preparation of the next Android version.

## Published Package

- Application ID: `io.github.ewoc2026.ewoc`.
- License: `GPL-3.0-or-later`.
- Source: <https://github.com/Ewoc2026/ewoc>.
- Issues: <https://github.com/Ewoc2026/ewoc/issues>.
- Published Android version: `1.0.4` / code `8`.
- Package: <https://f-droid.org/packages/io.github.ewoc2026.ewoc/>.
- Source tag: `v1.0.4`.
- Source commit: `71c109571f43c66a200afac70a54581ea0187ae1`.
- Reference APK SHA-256:
  `97a6bcaf97036b3660d04377721ebc8a0c91d2c014b059490d7834d72baecf16`.

## Local Candidate

Android `1.0.5` / code `9` prepares recovery from malformed workout-format
fields, FIT destination-opening failures, and inaccessible stored workout
documents during startup. It also includes the About feedback changes already
merged into public main. No desktop release or trainer-control change is part
of this candidate.

The candidate has not been published, tagged or submitted to F-Droid. Its app
version, changelog and Fastlane changelog are aligned. Existing screenshots
remain applicable to the unchanged main riding experience.

`docs/fdroiddata-metadata-draft.yml` is a source-tree rehearsal template. It
retains all currently published build entries and adds the proposed version.
Resolve its candidate tag to the exact public source commit in the external
fdroiddata checkout before submission; do not make this file refer to its own
future commit hash. Normal tag-based auto-update remains the intended route.

Publication, merging, tagging and release-asset upload require a separate
operator decision. Keep the signed reference APK tied to the exact source
commit selected for release; a changed commit requires a matching APK.

## Packaging Contract

- The Android app has no active Google Play Billing, Health Connect, Firebase,
  Crashlytics, analytics, advertising or backend-upload dependency.
- The release manifest does not declare `INTERNET` or `ACCESS_NETWORK_STATE`.
- Documentation and feedback destinations open only after a user action.
- Bluetooth permissions support FTMS trainers and heart-rate sensors.
- Workout, session, settings and FIT files remain local unless shared.
- Unsigned release assembly works without private signing variables.
- Append new fdroiddata build entries; preserve published history.
- Use `subdir: app`, `gradle: [yes]`, `AutoUpdateMode: Version`, and
  `UpdateCheckMode: Tags`.
- Developer-signed reference APKs must come from the fdroidserver-built
  unsigned output and use `apksigner --alignment-preserved true`.
- Verify signature-copy reproducibility against a separately built output.
- Expected signing certificate SHA-256:
  `4c916c9c69984f8aa9313838ca8cd7f8938af62500b6e85afb5e1afba5451e63`.

The [release checklist](release-checklist.md) owns commands and recovery notes.
Historical releases and device validation remain in Git and fdroiddata history.

# F-Droid Status

Updated: 2026-11-19

Codex published the operator-approved Android release and verified its source,
download, signature and local F-Droid build. This record distinguishes the
GitHub release from the pending F-Droid distribution step.

## Published Android APK

- Application ID: `io.github.ewoc2026.ewoc`.
- License: `GPL-3.0-or-later`.
- Source: <https://github.com/Ewoc2026/ewoc>.
- Issues: <https://github.com/Ewoc2026/ewoc/issues>.
- GitHub Android version: `1.0.5` / code `9`.
- Release: <https://github.com/Ewoc2026/ewoc/releases/tag/v1.0.5>.
- Reference APK: <https://github.com/Ewoc2026/ewoc/releases/download/v1.0.5/ewoc-1.0.5-rb.apk>.
- Source tag: `v1.0.5`.
- Source commit: `d7369f8d176bd3949da554f65900807a166a87a9`.
- Reference APK SHA-256:
  `5fd70df728bdaedccee23ac3fe80330c0cf1ad3719c531a7e21c7ce6c40a8baa`.

## Release Verification and F-Droid Progress

Android `1.0.5` / code `9` adds recovery from malformed workout-format
fields, FIT destination-opening failures, and inaccessible stored workout
documents during startup. It also includes the About feedback changes already
merged into public main. No desktop release or trainer-control change is part
of this release. Existing screenshots remain applicable to the unchanged main
riding experience.

The published download matches the prepared APK byte-for-byte and retains the
existing release certificate. The [tag checks](https://github.com/Ewoc2026/ewoc/actions/runs/44517084367)
passed. A local fdroidserver 2.4.5 build on Python 3.11.17 used the public source
and published `Binaries` URL, passed source scanning, reproduced the prepared
unsigned APK, and passed the signed-reference comparison and allowed-signer
check. This is local build evidence, not an F-Droid production-server result.

F-Droid's [package catalogue](https://f-droid.org/packages/io.github.ewoc2026.ewoc/)
and upstream metadata still list `1.0.4` / code `8` at the 19 November check.
The existing `AutoUpdateMode: Version` / `UpdateCheckMode: Tags` configuration
detected 1.0.5 and generated the exact expected version 9 recipe locally.
No manual metadata correction or duplicate merge request is needed. F-Droid's
own update, build and publication remain pending confirmation.

`docs/fdroiddata-metadata-draft.yml` records that expected recipe while preserving
the published build history. It does not submit metadata. The immutable release
tag stays on the verified APK source; later documentation changes do not retag
or rebuild the already published package.

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

# Changelog

All notable public-release changes to this project are documented in this
file.

## [Unreleased]

### Fixed

- Main menu Quit now removes the app task from Android recents instead of
  leaving it available to resume.

### Changed

- Android CI change detection now treats the Android-consumed shared EWO/editor
  modules as Android-impacting inputs.
- The Android Build workflow now declares the scheduled triggers used by its
  instrumentation-smoke and weekly flake-metrics jobs.
- Instrumentation-smoke change detection now covers critical startup,
  lifecycle, BLE/FTMS, setup, workout import/runner, baseline, UI assembly, and
  shared EWO/editor model paths.

## [1.0.3] - 2026-06-28

### Fixed

- FIT export writes lap and session HR, cadence, and power summaries to the
  correct FIT profile field numbers, using the exported record stream as the
  summary source when timeline samples are available.

### Validation

- The FIT export regression test parses the produced FIT bytes back and checks
  field numbers, base types, and record-derived summary values.

## [1.0.2] - 2026-06-28

### Changed

- Superseded by `1.0.3` before F-Droid publication. Use `1.0.3` for the
  corrected FIT lap/session summary field mapping.

## [1.0.1] - 2026-06-22

### Changed

- F-Droid review follow-up release.

### Removed

- Android `INTERNET` and `ACCESS_NETWORK_STATE` permissions were removed from
  the release manifest while keeping external documentation and issue links
  opening in the user's browser.

## [1.0.0] - 2026-05-26

### Changed

- Ewoc was published as a free public-source Android and desktop project.
- The public app repository license is `GPL-3.0-or-later`.
- Android uses the public application id `io.github.ewoc2026.ewoc`.
- Public-facing docs describe the free app, desktop editor, build flow,
  contribution path, and privacy posture.
- Public snapshot rehearsal tooling renders a history-free candidate tree from
  `HEAD`, excludes migration-only handoff/planning files, checks for private
  identity/backend/signing markers, and builds successfully from the generated
  snapshot.
- Android build metadata falls back cleanly when a source snapshot does not
  contain a `.git` directory.
- The first public snapshot was published at
  `https://github.com/Ewoc2026/ewoc`.
- The first public GitHub Actions Android Build validates debug unit tests,
  JaCoCo, lint, and release bundle generation.
- F-Droid preparation started with upstream Fastlane-style metadata, icon,
  changelog, and tablet screenshots.
- Release workflow documentation records the repeatable GitHub/F-Droid path,
  reproducible-build reference APK process, and fdroiddata follow-up checks.

### Removed

- Google Play Billing integration, paywall UI, paywall analytics, and paid
  feature gating.
- Android AI workout generation backend/client/coordinator wiring, quota/debug
  UI, setup step, generated-workout handoff, and generation endpoint config.
- Support-bundle export/upload surfaces, backend ingest upload scheduling, and
  install-scoped backend auth from the Android app.
- Health Connect dependency, permissions, rationale activity, Android client,
  adapter wiring, and permission callback path.
- Former private-brand domains, contacts, package identifiers, and desktop
  packaging metadata from app and packaging surfaces.
- Private validation artifacts, store-listing material, backend rollout notes,
  support-bundle examples, AI-generation planning packs, internal reports,
  superseded historical notes, and stale draft docs were removed from the
  public snapshot.

### Validation

- Android debug Kotlin compilation passed after the removal slices.
- Android debug unit-test compilation passed after support-bundle and
  Health Connect removal.
- Focused Android unit tests passed for billing/menu/debug automation,
  AI-generation removal, support-bundle removal, Health Connect removal, and
  public identity/link changes.
- Desktop Kotlin compilation passed after the public identity change.
- Documentation whitespace validation passed with `git diff --check`.
- The generated `/tmp/ewoc-public-snapshot` rehearsal passed Android debug
  Kotlin/unit-test compilation, desktop Kotlin compilation, and
  `:modules:ewo-core:jvmTest`.

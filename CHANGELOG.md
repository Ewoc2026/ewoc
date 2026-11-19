# Changelog

All notable public-release changes to this project are documented in this
file.

## [1.0.5] - Unreleased

### Added

- About now shows `ewoc@oikumene.works`, with actions to write an email or
  copy the address, and a clearly named GitHub feedback alternative. The new
  text is available in all seven supported languages.
- About keeps its content, including the title, scrollable so long labels and
  large text remain reachable in short windows. The Close action stays visible.

### Fixed

- JSON workout import now returns a failure result when the `format` field
  contains an array or object, instead of letting a type exception escape.
- FIT export now includes destination-opening exceptions in its existing
  failure result handling.
- Startup now handles document-metadata access failures as import errors,
  keeping the menu available when a previously selected workout was deleted.
- Android instrumentation CI now enables and requires KVM acceleration. A
  focused AOSP reproduction showed a System UI error dialog taking window
  focus during software CPU emulation; the app and test assertions are unchanged.
- Instrumentation smoke tests now follow the current menu navigation state
  and use the stable menu anchor instead of an unversioned title.
- The formerly quarantined screen-anchor test is back in the default smoke
  selection. It checks the fixed header's Quit action instead of a list item
  position. Application behavior is unchanged.

Codex implemented the feedback UI, translations and error-handling corrections,
updated regression coverage, and performed the recorded JVM and Android
validation. This version is prepared locally and has not been released.

## [1.0.4] - 2026-07-10

### Fixed

- Main menu Quit now removes the app task from Android recents instead of
  leaving it available to resume.
- Selecting a workout after a completed session now clears the previous
  runner's terminal elapsed time, so the same workout no longer opens as
  already completed before the new session starts.
- A short side-by-side session window now uses compact header controls and
  adaptive chart labels, keeping the workout profile readable in tablet split
  screen.
- Short phone split-screen session windows now keep the telemetry histogram or
  workout chart visible, avoid a duplicate Quit action in the tight portrait
  layout, and label telemetry-only cadence-zero pauses as Paused.

### Changed

- Android Gradle namespace, Kotlin package declarations, CI smoke selectors,
  and adb helper activity components now align with the public app identity
  `io.github.ewoc2026.ewoc`.
- Removed the temporary no-op billing shim left from the free/public migration.
- Android CI change detection now treats the Android-consumed shared EWO/editor
  modules as Android-impacting inputs.
- The Android Build workflow now declares the scheduled triggers used by its
  instrumentation-smoke and weekly flake-metrics jobs.
- Instrumentation-smoke change detection now covers critical startup,
  lifecycle, BLE/FTMS, setup, workout import/runner, baseline, UI assembly, and
  shared EWO/editor model paths.

### Validation

- Package migration validation passed with debug Kotlin, unit-test Kotlin, and
  androidTest Kotlin compilation, full debug unit tests, debug lint, debug APK
  assembly, Samsung A36 install/startup build proof, and a short A36 trainer
  smoke including the first 23 seconds of a bundled workout.
- Workout-reselection validation passed with the full
  `SessionOrchestratorFlowTest` class, debug main/unit-test Kotlin compilation,
  debug APK assembly, and Samsung `SM-X210` install/startup proof. The restored
  bundled workout reported `workoutElapsedSec=none` and remained startable.
- Split-screen session validation passed on Samsung `SM-X210` at a
  `952 x 1104 px` side-by-side window, full-screen landscape, and full-screen
  portrait. Focused layout and chart-label unit tests plus debug APK assembly
  passed.
- Short phone split-screen regression coverage passed with focused layout unit
  tests, Samsung A36 instrumentation tests for `360 x 520 dp` and
  `520 x 360 dp` constrained session viewports, debug APK assembly, and
  Samsung A36 install/startup build proof.

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

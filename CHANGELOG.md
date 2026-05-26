# Changelog

All notable public-release changes to this project are documented in this
file.

## [Unreleased]

### Changed

- Ewoc is published as a free public-source Android and desktop project.
- The public app repository license is `GPL-3.0-or-later`.
- Android now uses the public application id `io.github.ewoc2026.ewoc`.
- Public-facing docs now describe the free app, desktop editor, build flow,
  contribution path, and privacy posture.
- The first public snapshot pruning pass removed private validation artifacts,
  store-listing material, backend rollout notes, support-bundle examples,
  AI-generation planning packs, internal reports, superseded historical notes,
  and stale draft docs.
- Public-candidate docs now avoid describing retired AI workout generation,
  Play Billing, support-bundle export/upload, Health Connect ingestion, and
  private backend flows as active product features.
- Public snapshot rehearsal tooling now renders a history-free candidate tree
  from `HEAD`, excludes migration-only handoff/planning files, checks for
  private identity/backend/signing markers, and builds successfully from the
  generated snapshot.
- Android build metadata now falls back cleanly when a source snapshot does not
  contain a `.git` directory.
- Public docs are trimmed further to remove internal collaboration/operator,
  release-process, beta-readiness, local-hardware, EWO extraction rehearsal,
  and obsolete onboarding/planning notes.
- The regenerated public snapshot keeps only the focused public docs set and
  still passes the private-marker check after the documentation trim.
- The first public snapshot is published at
  `https://github.com/Ewoc2026/ewoc`.
- The first public GitHub Actions Android Build is fixed and now validates
  debug unit tests, JaCoCo, lint, and release bundle generation.
- F-Droid preparation is started with an initial readiness note and the stale
  Play Billing version-catalog alias removed.
- Upstream Fastlane-style F-Droid metadata is started with title, short
  description, full description, icon, and `versionCode=4` changelog.
- F-Droid tablet screenshots are added from a real Samsung tablet capture.
- F-Droid screenshot metadata now includes a real trainer-backed live-session
  capture.
- A local fdroiddata metadata draft is added for the first F-Droid submission.
- The first F-Droid source tag is selected as `v1.0.0` for `versionCode=4`.
- The fdroiddata metadata draft now uses F-Droid's expected Gradle marker and
  has a successful local fdroiddata build proof.
- Release workflow documentation now records the repeatable GitHub/F-Droid
  path, reproducible-build reference APK process, and fdroiddata follow-up
  checks.

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

### Validation

- Android debug Kotlin compilation passed after the removal slices.
- Android debug unit-test compilation passed after support-bundle and
  Health Connect removal.
- Focused Android unit tests passed for billing/menu/debug automation,
  AI-generation removal, support-bundle removal, Health Connect removal, and
  public identity/link changes.
- Desktop Kotlin compilation passed after the public identity change.
- Documentation whitespace validation passes with `git diff --check`.
- The generated `/tmp/ewoc-public-snapshot` rehearsal passes Android debug
  Kotlin/unit-test compilation, desktop Kotlin compilation, and
  `:modules:ewo-core:jvmTest`.

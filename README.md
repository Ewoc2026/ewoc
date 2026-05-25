# Ewoc

[![Android Build](https://github.com/Ewoc2026/ewoc/actions/workflows/android-build.yml/badge.svg)](https://github.com/Ewoc2026/ewoc/actions/workflows/android-build.yml)

Ewoc is a free, public-source indoor cycling app for Android, plus a desktop
workout editor. It focuses on structured workouts, FTMS-compatible trainers,
local workout files, practical ride telemetry, and a local-first workflow
without paid feature gates.

## Status

Ewoc is now published as a public repository at
[`Ewoc2026/ewoc`](https://github.com/Ewoc2026/ewoc). The GitHub Actions
Android Build workflow is the current shared validation gate.

The first public app release, APK distribution path, and F-Droid packaging are
still pending. Trainer-sensitive behavior should still be validated on real
hardware before a release is tagged.

## Features

- Android ride app for FTMS-compatible indoor trainers
- Optional Bluetooth heart-rate sensor support
- Structured workout riding from bundled, imported, or edited workouts
- Workout import from `.zwo`, legacy XML, and Ewoc `.ewo` files
- Built-in Android workout editor
- Compose Desktop workout editor
- Live session metrics and post-session summary
- FIT export and local save/share flows
- Local debug/mock-trainer tooling for development

## Not Included

- No paid feature gating
- No Google Play Billing dependency
- No AI workout generation backend
- No support-bundle upload/export flow
- No Health Connect permissions or data reads

## Requirements

- JDK 21
- Android SDK with API 36 platform installed
- Android 13 or newer device for real app testing (`minSdk = 33`)
- FTMS-compatible trainer for trainer-sensitive validation

Android Studio is convenient for app development, but the Gradle wrapper is the
canonical build entrypoint.

## Quick Start

```bash
git clone https://github.com/Ewoc2026/ewoc.git
cd ewoc
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :apps:desktop:compileKotlin --no-daemon
```

More detailed setup and validation commands are in [docs/building.md](docs/building.md).

## Repository Layout

- `app/` - Android application
- `apps/desktop/` - Compose Desktop workout editor
- `modules/ewo-core/` - shared EWO parsing and validation
- `modules/ewo-editor-model/` - shared editor document model
- `modules/ewo-editor-commands/` - shared editor command layer
- `spec/ewo/` - EWO workout-format specification, schema, and fixtures
- `docs/` - public docs for building, architecture, privacy, protocols, and
  user guidance

## Validation

Run Gradle lanes sequentially in this repository. Parallel Android/desktop
builds can contend on Kotlin caches and produce misleading failures.

```bash
./scripts/gradle-safe.sh :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
./scripts/gradle-safe.sh :apps:desktop:compileKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
./scripts/gradle-safe.sh :app:testDebugUnitTest --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
git diff --check
```

Final trainer-sensitive behavior should be validated on real hardware, not only
with the debug mock trainer.

## Documentation

- Build and validation: [docs/building.md](docs/building.md)
- Privacy: [docs/privacy.md](docs/privacy.md)
- User guide: [docs/user-guide/](docs/user-guide/)
- EWO format spec: [spec/ewo/](spec/ewo/)
- Changelog: [CHANGELOG.md](CHANGELOG.md)

## License

Ewoc is licensed under `GPL-3.0-or-later`. See [LICENSE](LICENSE).

# Building Ewoc

This page describes the supported local build path for the public Ewoc
repository.

## Requirements

- JDK 21
- Git
- Android SDK command-line tools or Android Studio
- Android SDK Platform 36
- Android SDK Build-Tools compatible with the installed Android Gradle Plugin

For Android device testing:

- Android 13 or newer device
- USB debugging enabled
- Bluetooth permissions granted at runtime
- FTMS-compatible trainer for trainer-sensitive checks

## First Build

Clone the repository and run the Android and desktop compile checks:

```bash
git clone https://github.com/Ewoc2026/ewoc.git
cd ewoc
./scripts/gradle-safe.sh :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
./scripts/gradle-safe.sh :apps:desktop:compileKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
```

Use `./gradlew` directly if you prefer, but keep Gradle invocations sequential
when building Android and desktop targets from the same checkout.

## Android App

Compile the debug app:

```bash
./scripts/gradle-safe.sh :app:compileDebugKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
```

Build a debug APK:

```bash
./scripts/gradle-safe.sh :app:assembleDebug --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
```

Install a debug APK on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The public Android application id is `io.github.ewoc2026.ewoc`.

## Desktop Editor

Compile the desktop editor:

```bash
./scripts/gradle-safe.sh :apps:desktop:compileKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
```

Run the desktop editor:

```bash
./gradlew :apps:desktop:run --no-daemon --no-configuration-cache
```

Package native desktop artifacts for the current operating system:

```bash
./scripts/gradle-safe.sh :apps:desktop:packageDistributionForCurrentOS --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
```

Linux package output is under `apps/desktop/build/compose/binaries/main/deb/`.
Windows MSI packaging must be validated on Windows with WiX available.

## Tests

Run all app unit tests:

```bash
./scripts/gradle-safe.sh :app:testDebugUnitTest --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
```

Run shared EWO core tests:

```bash
./scripts/gradle-safe.sh :modules:ewo-core:jvmTest --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
```

Run desktop tests:

```bash
./scripts/gradle-safe.sh :apps:desktop:test --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
```

## Release Signing

Unsigned debug builds are enough for development.

Release signing is configured outside the repository through these environment
variables:

- `ERGOMETER_RELEASE_STORE_FILE`
- `ERGOMETER_RELEASE_STORE_PASSWORD`
- `ERGOMETER_RELEASE_KEY_ALIAS`
- `ERGOMETER_RELEASE_KEY_PASSWORD`

All four values must be provided together.

## Troubleshooting

If Kotlin or Gradle reports cache registration, missing cache files, or daemon
contention after interrupted builds, stop Gradle and retry:

```bash
./gradlew --stop
```

If the failure persists, remove generated `build/` directories and run one
Gradle command at a time.

# ADB Cheatsheet (Ewoc)

This file contains practical `adb` commands for daily development and debugging on a USB-connected Android device.

## Prerequisites
- USB debugging enabled on device.
- Device is authorized for this host.
- Check connection:

```bash
adb devices -l
```

Expected: device listed with state `device`.

## Project Constants
- App package: `io.github.ewoc2026.ewoc`
- Main activity component: `io.github.ewoc2026.ewoc/io.github.ewoc2026.ewoc.MainActivity`

## Build + Install

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

Install (replace existing):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Install preserving app data (usually same as `-r`):

```bash
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```

## Launch / Stop App

Start app:

```bash
adb shell am start -n io.github.ewoc2026.ewoc/io.github.ewoc2026.ewoc.MainActivity
```

Force-stop app:

```bash
adb shell am force-stop io.github.ewoc2026.ewoc
```

Clear app data (fresh state):

```bash
adb shell pm clear io.github.ewoc2026.ewoc
```

## Logs (Logcat)

Clear old logs:

```bash
adb logcat -c
```

Show live logs:

```bash
adb logcat
```

Show app process logs only:

```bash
adb logcat --pid "$(adb shell pidof -s io.github.ewoc2026.ewoc)"
```

Show useful BLE tags (example):

```bash
adb logcat | rg "FTMS|BluetoothGatt|BluetoothLeScanner|SESSION|WORKOUT"
```

## Files and Exported FIT Artifacts

List app files (debuggable build context-dependent):

```bash
adb shell run-as io.github.ewoc2026.ewoc ls -la files
```

Pull one internal app file:

```bash
adb exec-out run-as io.github.ewoc2026.ewoc cat files/<filename> > ./<filename>
```

Pull from public storage path (if export target is public):

```bash
adb pull /sdcard/Download/<filename> .
```

## Screenshots and Screen Recording

Take screenshot:

```bash
adb exec-out screencap -p > screenshot.png
```

Record screen (30 sec max in this example):

```bash
adb shell screenrecord --time-limit 30 /sdcard/Download/ergometer-record.mp4
adb pull /sdcard/Download/ergometer-record.mp4 .
```

Use the helper script to capture screenshot + recording in one command:

```bash
./scripts/adb/capture.sh --serial R92Y40YAZPB --seconds 10
```

## Input Automation (Quick Smoke Actions)

Tap at coordinates:

```bash
adb shell input tap 500 1200
```

Swipe:

```bash
adb shell input swipe 500 1600 500 600 300
```

Type text:

```bash
adb shell input text "140"
```

Back key:

```bash
adb shell input keyevent 4
```

## Instrumentation Tests

Run all androidTests:

```bash
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Run the two local smoke test classes:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.ewoc2026.ewoc.ui.MainActivityContentFlowTest,io.github.ewoc2026.ewoc.MainActivityRecreationRotationTest \
  --no-daemon
```

Run the 21-test CI smoke scope on an already prepared
Android 13 emulator. Replace the serial with the intended target from
`adb devices -l`; `ANDROID_SERIAL` keeps other connected devices out of the run.

```bash
ANDROID_SERIAL=emulator-5560 ./scripts/gradle-safe.sh --no-fallback :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.ewoc2026.ewoc.ui.MainActivityContentFlowTest,io.github.ewoc2026.ewoc.MainActivityRecreationRotationTest,io.github.ewoc2026.ewoc.MainActivityLifecycleContinuityTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.FlakyTest \
  --no-configuration-cache -Dkotlin.incremental=false --console=plain
```

The default selection now includes `criticalFlowScreensRenderExpectedAnchors`:
its obsolete selectors were repaired and its flaky annotation was removed.
The annotation filter remains available for any future quarantined tests.
Three About contact tests in `MainActivityContentFlowTest` cover clipboard
content and dismissal, email/GitHub destinations, and missing-email-app recovery.
They run automatically in the same selection, bringing its total to 21.
Record the emulator image and version with the results: a local Google Play
image is not identical to CI's AOSP image.
Keep Gradle runs sequential. The XML results are under
`app/build/outputs/androidTest-results/connected/` and the HTML report is under
`app/build/reports/androidTests/connected/`.
On 2026-11-19, Codex verified ten consecutive runs of the repaired anchor test,
then the original 18-test selection on a local Android 13 Google Play emulator.
GitHub Actions also passed all 18 in
[PR #2](https://github.com/Ewoc2026/ewoc/pull/2); Codex verified the downloaded
XML and retry summary. Neither final run used automatic retries. Those results
precede the three About tests added to the current selection.
Codex then verified the expanded 21-test selection on the local Android 13
Google Play emulator, with no failures, errors, skips, or automatic retries.

## Device Diagnostics

Battery status:

```bash
adb shell dumpsys battery
```

Current foreground activity:

```bash
adb shell dumpsys activity activities | rg "mResumedActivity|topResumedActivity"
```

Bluetooth manager state:

```bash
adb shell dumpsys bluetooth_manager
```

## Multi-Device Tip
If multiple devices are connected, target one serial explicitly:

```bash
adb -s <serial> <command>
```

Example:

```bash
adb -s R92Y40YAZPB shell getprop ro.product.model
```

## Project Helper Script (Recommended)

Use the included helper for filtered Ewoc logs:

```bash
./scripts/adb/logcat-ergometer.sh --clear
```

Use app-process-only logging (best signal/noise during active app testing):

```bash
./scripts/adb/logcat-ergometer.sh --pid-only --clear
```

Dump current filtered logs to file and exit:

```bash
./scripts/adb/logcat-ergometer.sh --dump
```

Use specific device serial and custom output path:

```bash
./scripts/adb/logcat-ergometer.sh \
  --serial R92Y40YAZPB \
  --clear \
  --output .local/logs/ergometer-session.log
```

## One-command Device Smoke Pipeline

Run install + clean-state + connected tests + log capture + artifact collection:

```bash
./scripts/adb/device-smoke.sh
```

Run all instrumentation tests (not only smoke class):

```bash
./scripts/adb/device-smoke.sh --all-tests
```

Run with explicit device and optional short screen recording:

```bash
./scripts/adb/device-smoke.sh \
  --serial R92Y40YAZPB \
  --record-seconds 20
```

Artifacts are written under `.local/device-test-runs/run-<timestamp>/`:
- `logcat.log`
- `run-summary.txt`
- `androidTest-results/*.xml`
- `reports/androidTests-connected-debug/`
- optional `final-screen.png` and `screenrecord.mp4`

## One-command Emulator Smoke Pipeline (Fast Regression)

Use the local emulator helper when you want fast UI/state regression checks without using the USB tablet:

```bash
./scripts/adb/emulator-smoke.sh
```

Create/verify the default AVD only (no boot, no tests):

```bash
./scripts/adb/emulator-smoke.sh --create-only
```

Run full instrumentation suite on emulator:

```bash
./scripts/adb/emulator-smoke.sh --all-tests
```

Run emulator smoke including tests marked `@FlakyTest`:

```bash
./scripts/adb/emulator-smoke.sh --include-flaky
```

Keep emulator process alive after the run (useful when iterating repeatedly):

```bash
./scripts/adb/emulator-smoke.sh --keep-running
```

Artifacts are written under `.local/emulator-test-runs/run-<timestamp>/`:
- `emulator.log`
- `logcat.log`
- `run-summary.txt`
- `androidTest-results/*.xml`
- `reports/androidTests-connected-debug/`
- `final-screen.png`

Notes:
- This emulator pipeline is for UI/Compose/instrumentation regressions.
- By default, local `scripts/adb/emulator-smoke.sh` scope runs `MainActivityContentFlowTest` and `MainActivityRecreationRotationTest` (unless `--all-tests` is used).
- By default, emulator smoke excludes tests annotated with `@FlakyTest`; use `--include-flaky` when needed.
- On November 19, 2026, `MainActivityContentFlowTest#criticalFlowScreensRenderExpectedAnchors` returned to the default selection after selector repairs and ten consecutive local passes; it no longer needs include-flaky mode.
- Local emulator smoke run artifacts include flaky policy fields in `run-summary.txt` (`exclude_flaky`, `include_flaky`).
- BLE trainer behavior (FTMS/Tunturi) must still be validated on real hardware with `./scripts/adb/device-smoke.sh`.
- If both USB device and emulator are connected, emulator runs stay deterministic because the script pins test execution via `ANDROID_SERIAL=emulator-<port>`.
- If you see `avdmanager` or `sdkmanager` missing errors, install **Android SDK Command-line Tools** from Android Studio: `Settings > Android SDK > SDK Tools`.

GitHub smoke policy:
- PR checks run `android-instrumentation-smoke` when Android-impacting UI/session/test/workflow files change; the lane stays non-blocking on `pull_request`.
- Nightly smoke (`04:30 UTC`) and manual smoke dispatch run the CI smoke class scope:
  - `MainActivityContentFlowTest`
  - `MainActivityRecreationRotationTest`
  - `MainActivityLifecycleContinuityTest`
- Deterministic reconnect/export additions run in default PR smoke scope:
  - `MainActivityLifecycleContinuityTest#reconnectMidSessionReturnsToSessionWithoutLosingRunningState`
  - `MainActivityContentFlowTest#summaryFitExportFailureKeepsErrorVisibleAndAllowsRetryAction`
- Flaky inclusion remains configurable via workflow input; `include_flaky_tests=true` includes tests that still carry `@FlakyTest`. The restored anchor test runs in either mode.
- On pull requests, `android-instrumentation-smoke` is non-blocking and retries once automatically when failure signatures match emulator/ADB infra instability.
- GitHub smoke uploads `smoke-policy.txt` plus instrumentation reports as run artifacts (`smoke-policy.txt` now includes `smoke_test_classes`).

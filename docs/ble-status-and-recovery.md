# BLE Status and Recovery

## Purpose
This document explains what the MENU device status indicators mean and how recovery behavior works.

## Device Status Dots on MENU

### Trainer Dot
- Pulsing green:
  - Active FTMS session link is ready, or
  - Selected trainer is currently reachable in passive probe scan.
- Amber:
  - Selected trainer is known unreachable (probe result says not found), or
  - Session-start connection issue prompt is active.
- Gray:
  - No trainer selected, or
  - Reachability not yet known (for example immediately after selection/app start).

### HR Dot
- Pulsing green:
  - Active HR GATT link is connected, or
  - Selected HR device is currently reachable in passive probe scan.
- Amber:
  - Selected HR is known unreachable after stabilization logic.
- Gray:
  - No HR selected, or
  - Reachability not yet known.

## Probe Model

### Trainer Probe
- Interval: every 10 seconds.
- Probe duration: 1.5 seconds.
- Probe type: passive BLE advertisement scan filtered by FTMS service UUID.
- Important: no GATT connection is opened from probe logic.

### HR Probe
- Interval: every 30 seconds.
- Probe duration: 1.5 seconds.
- Probe type: passive BLE advertisement scan filtered by HR service UUID.
- Important: no GATT connection is opened from probe logic.

## HR Stabilization (Anti-Flicker)
To avoid false amber transitions from short advertisement gaps:
- A single missed HR probe does not immediately mark HR unreachable.
- HR requires repeated misses and/or stale timeout before turning amber.
- Current thresholds:
  - `hrStatusMissThreshold = 2`
  - `hrStatusStaleTimeoutMs = 75_000`

## Scan Contention Guard
Menu probes pause when:
- Device picker scan is active.
- User starts a session.

This prevents probe scans from interfering with explicit user-initiated scans and session start flow.

## Typical Real-World Scenarios

### Trainer sleeps while app stays on MENU
Expected:
- Trainer dot transitions from green to amber after probe updates.
- Dot returns green after trainer wakes and advertisements are visible again.

### HR strap advertisements are irregular
Expected:
- Dot may remain green across short gaps.
- Dot turns amber only after stabilization thresholds are crossed.

### Immediate app startup on MENU
Expected:
- Dot may be gray briefly until first probe result is available.

## Manual Recovery Steps
If a device remains amber unexpectedly:
1. Confirm device power state.
2. Confirm Bluetooth is enabled on phone/tablet.
3. Use `Search trainer` / `Search HR` and reselect device.
4. If still failing, restart device BLE (toggle Bluetooth) and retry.

## Debugging Notes
For connection/start issues, capture:
- Logcat entries with tags related to `FTMS`, `HR`, and session transitions.
- Device state timeline: when powered on/off, when app screen changed.

## Related Files
- `app/src/main/java/com/example/ergometerapp/MainViewModel.kt`
- `app/src/main/java/com/example/ergometerapp/ui/Screens.kt`
- `app/src/main/java/com/example/ergometerapp/session/SessionOrchestrator.kt`

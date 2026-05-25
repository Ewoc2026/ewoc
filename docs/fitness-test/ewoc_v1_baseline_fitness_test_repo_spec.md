# Ewoc V1 Baseline Fitness Test Repo Spec

Status: proposed implementation slice

This document is the repo-native implementation spec for the first shippable baseline fitness test. It replaces any conflicting V1 decisions in:

- `docs/fitness-test/baseline_fitness_test.pdf`
- `docs/fitness-test/ewoc_v1_baseline_fitness_test_engineering_spec.pdf`

The goal is not to preserve every earlier idea. The goal is to define the smallest slice that fits the current Ewoc codebase and can be implemented without inventing a second app architecture around it.

## 1. Scope

In scope:

- one canonical power-based ramp test
- optional FTMS ERG control when trainer control is available and granted
- advisory-target fallback when power telemetry exists but control grant fails and the user explicitly chooses to continue
- optional HR capture when present
- automatic FTP update on a valid completed result
- minimal latest-test metadata persistence
- one repo-realistic entry point in the existing menu/profile flow

Out of scope:

- onboarding integration
- manual fitness estimation fallback
- test history UI or multi-attempt history management
- plan-system rebuild
- adaptive biofeedback coaching
- HR-only estimation
- custom protocols
- medically framed safety logic

## 2. Current Repo Anchors

This slice must fit the current app structure instead of assuming a separate settings or onboarding system already exists.

- Navigation is currently limited to `MENU`, `EWO_EDITOR`, `CONNECTING`, `STOPPING`, `SESSION`, and `SUMMARY` in `app/src/main/java/com/example/ergometerapp/AppScreen.kt`.
- FTP is currently the only persisted training anchor. It is stored in `FtpSettingsStorage`, mirrored in `ProfileSettingsUiState`, and saved through `ProfileSettingsCoordinator`.
- `MainViewModel` already propagates FTP changes through `sessionOrchestrator.onFtpWattsChanged()`, and workout mapping/preview logic already reads the active FTP from the same source.
- FTMS control infrastructure already exists through `SessionOrchestrator`, `FtmsController`, and the compatibility gateway.
- FTMS telemetry already uses `IndoorBikeData.instantaneousPowerW` and `IndoorBikeData.instantaneousCadenceRpm`.

Implementation consequence:

- do not build a second persisted power-profile model for V1
- do not assume a real `Settings` destination already exists
- do not overload the existing workout session state machine with baseline-test-specific result logic

## 3. Canonical V1 Decisions

### 3.1 Product decisions

- The test is power-based only.
- If no power telemetry is available, the feature is unavailable in this slice.
- No manual estimation fallback is included in this work.
- No onboarding integration is included in this work.
- No baseline-history feature is included in this work.
- Valid completed results overwrite the active FTP immediately.
- Derived power zones are shown from the active FTP, not persisted as a second source of truth.

### 3.2 Control-mode decisions

- If FTMS control is available and request-control succeeds, the test uses ERG control.
- If control grant fails but live power telemetry is available, the app must ask whether to continue in advisory-target mode or cancel the test.
- If control is lost after an ERG-mode test has already started, the attempt is cancelled.
- Measured power remains the authoritative scoring signal in both ERG and advisory modes.

### 3.3 Status model

Only these attempt outcomes exist in V1:

- `completed`
- `invalid`
- `cancelled`

`partial` is intentionally removed. Interruption semantics are carried by `stop_reason` and `control_mode`.
`unavailable` is a precheck/feature-availability state, not a persisted attempt result.

### 3.4 Entry point decision

The earlier product wording "Settings -> Recalibrate" does not match the current repo. There is no dedicated settings destination today.

Canonical V1 entry point:

- add a new recalibration affordance to the existing `MENU -> PROFILE` flow, or another menu-owned entry that does not require rebuilding navigation first

Implementation note:

- because existing UI layout changes require explicit approval, this slice should add only new UI affordances and new screens; it must not silently repurpose the current session or menu layouts

## 4. Protocol Constants

Test version: `baseline_fitness_test_v1`

Constants:

- warmup duration: `300 s`
- ramp step duration: `60 s`
- ramp increment: `20 W`
- minimum valid ramp time after warmup: `6 min`
- medium-confidence threshold: `8 min`
- high-confidence threshold: `10 min`
- cadence auto-stop threshold: `30 rpm`
- cadence auto-stop hold: `10 s`
- power-signal-loss threshold during ramp: `8 s`
- cooldown duration: `120 s`
- FTP factor: `0.75`

Start wattage rule:

```text
if prior_ftp_watts exists:
    start_watts = round_to_5(max(0.45 * prior_ftp_watts, 75))
else:
    start_watts = 100
```

Notes:

- Section 3.1 from the ChatGPT PDF is the only authoritative start-wattage rule.
- The old `user_experience` branch is removed from V1.
- FTMS cadence is a valid cadence source for cadence-based auto-stop.

## 5. Protocol

1. Precheck the selected trainer and required signals.
2. Determine `control_mode`:
   - `ERG` if FTMS control is available and granted
   - `ADVISORY` if power telemetry exists and the user accepts fallback after failed control grant
3. Warm up for `300 s` at `start_watts`.
4. Start the ramp:
   - minute 1 target = `start_watts`
   - each new minute target = previous target + `20 W`
5. End the ramp when one of the stop conditions fires.
6. Offer a short cooldown when the attempt is still eligible for scoring.
7. Compute the result.
8. If the outcome is `completed`, promote FTP immediately.

In advisory mode the target watts are UI guidance only. In ERG mode the target watts are both UI guidance and the requested trainer target. In both modes the measured power stream is the scoring input.

## 6. State Machine

Recommended state set:

- `IDLE`
- `PRECHECK`
- `REQUESTING_CONTROL`
- `ADVISORY_FALLBACK_PROMPT`
- `WARMUP`
- `RAMP_ACTIVE`
- `STOPPING`
- `COOLDOWN`
- `RESULT_COMPUTE`
- `RESULT_READY`
- `UNAVAILABLE`
- `INVALID`
- `CANCELLED`

Required transitions:

- `IDLE -> PRECHECK` on user start
- `PRECHECK -> UNAVAILABLE` when no live power telemetry can be established
- `PRECHECK -> REQUESTING_CONTROL` when the trainer exposes controllable FTMS behavior
- `PRECHECK -> ADVISORY_FALLBACK_PROMPT` when power telemetry exists but the test cannot start in ERG mode
- `REQUESTING_CONTROL -> WARMUP` on successful control grant
- `REQUESTING_CONTROL -> ADVISORY_FALLBACK_PROMPT` when control grant fails but power telemetry still exists
- `ADVISORY_FALLBACK_PROMPT -> WARMUP` when the user accepts advisory mode
- `ADVISORY_FALLBACK_PROMPT -> CANCELLED` when the user declines advisory mode
- `WARMUP -> RAMP_ACTIVE` after `300 s`
- `WARMUP -> CANCELLED` on user cancel
- `RAMP_ACTIVE -> STOPPING` on `manual_stop`, `cadence_drop`, `power_signal_lost`, or `device_disconnect`
- `RAMP_ACTIVE -> CANCELLED` on `control_lost_mid_test`
- `STOPPING -> COOLDOWN` when cooldown is offered and not skipped
- `STOPPING -> RESULT_COMPUTE` when cooldown is skipped or not allowed
- `COOLDOWN -> RESULT_COMPUTE` after `120 s` or explicit skip
- `RESULT_COMPUTE -> RESULT_READY` when a valid completed result exists
- `RESULT_COMPUTE -> INVALID` when the attempt cannot produce a valid FTP estimate

Implementation note:

- the baseline-test state machine should live in its own coordinator/domain package
- do not merge this logic into `SessionOrchestrator`'s workout runner branches

## 7. Stop Conditions and Outcome Rules

Stop reasons:

- `manual_stop`
- `cadence_drop`
- `power_signal_lost`
- `device_disconnect`
- `control_grant_declined`
- `control_lost_mid_test`
- `user_cancel`

Outcome rules:

- `completed` when:
  - at least `6` full ramp minutes were completed after warmup
  - `last_full_step_watts` is known
  - the attempt did not end with `control_lost_mid_test`
- `invalid` when:
  - warmup completed but fewer than `6` valid ramp minutes were completed
  - power data is too incomplete to identify the last full step
- `cancelled` when:
  - the user cancels before a valid result exists
  - the user declines advisory fallback after failed control grant
  - FTMS control is lost after an ERG-mode attempt has started

Specific rules:

- Power-signal loss after a valid ramp can still produce `completed` with `stop_reason = power_signal_lost`.
- Device disconnect in advisory mode follows the same scoring rule as power-signal loss.
- Device disconnect in ERG mode is treated as `control_lost_mid_test` and cancels the attempt.

## 8. Result Computation

Definitions:

- `valid_ramp_minutes`: count of fully completed `60 s` ramp steps after warmup
- `last_full_step_watts`: highest ramp target completed for the full `60 s`
- `peak_1m_power_watts`: same as `last_full_step_watts`
- `ftp_estimate_watts`: `round_to_5(last_full_step_watts * 0.75)`

HR rule:

- if HR coverage is at least `0.80` during active test time, store `threshold_hr_estimate_bpm` as the max 30-second smoothed HR observed during the final completed step
- otherwise store `null`

Confidence algorithm:

```text
confidence = LOW
if valid_ramp_minutes >= 8:
    confidence = MEDIUM
if valid_ramp_minutes >= 10 and max_power_gap_sec <= 2:
    confidence = HIGH
if max_power_gap_sec > 8:
    confidence = LOW
```

Confidence notes:

- missing HR never lowers FTP confidence
- manual stop at a clean step boundary is neutral, not a special success bonus
- a completed result with power loss is still capped by the algorithm above

## 9. Persistence Model

V1 stores only the active result and minimal latest-test metadata.

### 9.1 Reuse existing FTP storage

Keep using `FtpSettingsStorage` as the active FTP source of truth:

- `ftp_watts`

### 9.2 Add baseline-test metadata storage

Add a dedicated storage object for baseline metadata instead of widening `FtpSettingsStorage` into a generic blob store. Suggested name:

- `BaselineFitnessTestSettingsStorage`

Suggested persisted fields:

- `ftp_source = baseline_fitness_test_v1`
- `ftp_last_tested_at`
- `last_baseline_test` latest-only summary payload

Suggested latest-only payload:

```json
{
  "testVersion": "baseline_fitness_test_v1",
  "status": "completed",
  "stopReason": "manual_stop",
  "controlMode": "ERG",
  "startedAt": "2026-03-15T10:00:00Z",
  "completedAt": "2026-03-15T10:16:24Z",
  "startWatts": 100,
  "validRampMinutes": 9,
  "lastFullStepWatts": 260,
  "ftpEstimateWatts": 195,
  "peak1mPowerWatts": 260,
  "thresholdHrEstimateBpm": 171,
  "confidence": "MEDIUM",
  "maxPowerGapSec": 1.3,
  "hrCoverageRatio": 0.91,
  "sensorProfile": {
    "power": true,
    "heartRate": true,
    "cadence": true
  }
}
```

Deliberate simplification:

- do not persist `power_zones` as separate stored data in V1
- compute zones from active FTP whenever UI needs them

Reason:

- the current repo is FTP-centric
- storing zones separately would create redundant state and drift risk without a real consumer

## 10. Integration Plan

### 10.1 New domain seam

Create a dedicated baseline-test package instead of stretching session-workout code:

- `BaselineFitnessTestModels`
- `BaselineFitnessTestProtocol`
- `BaselineFitnessTestStateMachine`
- `BaselineFitnessTestResultCalculator`
- `BaselineFitnessZoneCalculator`
- `BaselineFitnessTestSettingsStorage`
- `BaselineFitnessTestCoordinator`

### 10.2 ViewModel integration

`MainViewModel` should own the baseline-test coordinator and expose immutable UI state for:

- availability
- current state
- timers
- target watts
- measured power/cadence/HR
- control mode
- fallback prompt visibility
- result summary

Promotion path on successful completion:

1. save new FTP through `FtpSettingsStorage.saveFtpWatts(...)`
2. save metadata through `BaselineFitnessTestSettingsStorage`
3. notify `sessionOrchestrator.onFtpWattsChanged()`
4. refresh any UI that already derives workout readiness or AI context from active FTP

### 10.3 FTMS integration

Reuse existing FTMS seams, but keep baseline logic separate from workout-session logic:

- request control through the existing FTMS control stack
- set ERG targets through the existing FTMS target-writing path
- observe power/cadence from the same telemetry parser already used by sessions

Baseline-specific control behavior:

- failed initial control grant with live power telemetry -> show advisory fallback prompt
- lost control after ERG start -> cancel attempt
- advisory mode never attempts ERG writes after fallback begins

### 10.4 UI integration

Recommended V1 UI surfaces:

- new entry affordance in the existing menu/profile flow
- new dedicated baseline-test screen
- new result screen or result state inside the dedicated baseline-test screen

Do not:

- repurpose the existing workout session screen as the baseline runner without separate approval
- modify the existing menu/profile layout structure beyond adding new UI elements

## 11. Validation and Test Plan

Required pure JVM coverage:

- start-wattage calculation
- state-machine transition coverage
- stop-reason resolution
- completed vs invalid vs cancelled outcome resolution
- FTP estimate computation
- confidence computation
- power-gap handling
- latest-only metadata serialization

Required integration coverage:

- `MainViewModel` promotes completed baseline result into `FtpSettingsStorage`
- promotion path triggers `sessionOrchestrator.onFtpWattsChanged()`
- failed control grant exposes advisory fallback prompt only when power telemetry remains available
- ERG control loss mid-test cancels instead of promoting FTP

Required manual validation:

- mock-trainer run with ERG control path
- mock-trainer run with advisory fallback path
- mock-trainer run that drops cadence below threshold long enough to auto-stop
- real trainer run on a connected Samsung device before release

## 12. Definition of Done

This slice is done when:

- a user can start the test from the current menu-owned profile flow
- the app blocks the feature cleanly when power telemetry is unavailable
- ERG mode works when FTMS control is granted
- failed initial control grant offers advisory fallback instead of hard-failing
- control loss after ERG start cancels the attempt
- a valid completed result updates the active FTP immediately
- the result screen shows estimated FTP, confidence, and simple zone summary
- no baseline-history UI or onboarding dependency is required

## 13. Explicit Cuts for This Slice

Cut these even if they seem adjacent:

- onboarding hooks
- manual-level estimate flow
- user-experience profiling
- baseline history
- separate approval dialog before FTP overwrite
- persisted zone tables
- custom trainer-control heuristics beyond ERG/advisory fallback

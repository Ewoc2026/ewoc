# HR Workouts V1 Implementation Checklist

## Purpose

This checklist translates `docs/hr-workouts-v1-plan.md` into the concrete
implementation slices implied by the current codebase.

It is intentionally code-facing. The goal is to show where the existing seams
already exist, what still blocks executable HR workouts, and what order reduces
rework risk.

## Current Code Baseline

The repository already has these HR-related building blocks:

- canonical `.ewo` parsing and validation for HR-targeted segments
- import-time preservation of workout-level control metadata
- `ImportedHrExecutionPolicyV1` policy resolution in
  `ImportedErgoWorkoutExecutionPolicy`
- imported-HR start preflight in `ImportedHrRuntimePreflightAdapter`
- imported-HR fail-safe state machine coverage in
  `ImportedHrRuntimeStateMachineV1Test`
- readiness messaging and diagnostics in `SessionWorkoutReadinessEvaluator`

The main blocking fact is:

- imported HR steps still fail mapping in
  `ImportedErgoWorkoutExecutionMapper`
- `ExecutionWorkout` / `ExecutionSegment` currently describe only power/ramp
  / free-ride execution
- `SessionOrchestrator` still builds runners only through the current
  `ExecutionWorkout` -> `WorkoutStepper` path

In other words, policy vocabulary exists, but executable HR runtime support is
not wired into the real runner/session path yet.

## Recommended Delivery Order

### 1. Lock the runner-facing execution shape

Decide the concrete runner contract before changing the session flow.

Current fork in the road:

- extend `ExecutionSegment` so it can represent HR-controlled steady segments,
  or
- keep HR execution as a parallel runtime path beside the current
  `ExecutionWorkout` model

Recommended option:

- prefer extending the execution model carefully rather than creating a second
  unrelated runner stack, unless a quick code read proves that the current
  `WorkoutStepper` abstraction cannot stay coherent after adding HR-owned
  segments

Why this matters:

- `SessionOrchestrator` currently assumes one runner-creation path
- profile-chart and duration/TSS surfaces also assume one mapped execution view
- a parallel stack would likely duplicate more session wiring than it saves

### 2. Replace “unsupported HR target” with a real mapping result

Current blocker:

- `ImportedErgoWorkoutExecutionMapper` intentionally emits
  `UNSUPPORTED_HEART_RATE_TARGET` for `HeartRateSteady`

Implementation target:

- map supported imported HR steady steps into a runner-facing execution shape
- preserve the existing explicit failure path for unsupported HR forms that
  still exceed V1 scope

Do not do:

- silent degradation into power-only execution
- broad support for ramps, intervals, or fast alternation under HR control

### 3. Move start-preflight from diagnostics-only into the actual start path

Current state:

- `SessionWorkoutReadinessEvaluator` already computes imported-HR capability
  readiness and emits helpful diagnostics
- this is still used only to explain why execution is blocked

Implementation target:

- reuse the same preflight result to gate real HR segment start
- keep one shared vocabulary for:
  - readiness evaluation
  - user-facing failure messages
  - diagnostics
  - runner start behavior

Definition of done for this slice:

- missing HR signal at start and missing trainer control at start are blocked
  by the actual runtime path, not only by explanatory messaging

### 4. Extend the runner/stepper for one conservative HR steady path

Current state:

- `WorkoutRunner` assumes `StepperOutput.targetPowerWatts`
- `WorkoutStepper` currently advances deterministic duration-owned execution
  segments

Implementation target:

- support one conservative HR-owned segment type with:
  - authored initial power
  - bounded increases
  - quicker decreases
  - hard-cap throttle behavior
  - signal-loss fallback
  - safety outputs derived relative to the segment's `initialPowerWatts`
  - one code-owned global safety max watt ceiling instead of trusting authored
    absolute emergency watts directly
  - no auto-resume after failure

This slice should stay narrow:

- no broad physiological state engine
- no cloud AI
- no coach personality logic
- no new protocol DSL

### 5. Wire fail-safe transitions into the real session flow

Current state:

- `ImportedHrRuntimeStateMachineV1` already defines:
  - start failure
  - signal-loss fallback then stop
  - trainer-control-loss stop
  - safety-cap throttle then stop

Implementation target:

- connect those transitions to the real session orchestration path
- ensure the resulting commands still respect FTMS write serialization through
  the existing controller/writer path

Important invariant:

- no HR-owned command path may bypass the existing FTMS control serialization

### 6. Surface explicit HR runtime outcomes in UI/state

Current state:

- session UI already has a generic `workoutExecutionModeMessage`
- readiness diagnostics can already describe blocked imported-HR starts

Implementation target:

- surface the actual runtime outcomes with explicit and stable wording:
  - HR unavailable at start
  - trainer control unavailable at start
  - HR signal lost
  - HR safety cap exceeded
  - target unreachable at max power
  - target unreachable at min power

Do not do:

- hide HR-specific failures behind generic “workout unsupported” messaging once
  runner support exists

### 7. Add tests in the same order as the runtime slices

Minimum recommended sequence:

1. mapper tests for supported imported HR steady mapping
2. readiness/start-path tests reusing the same preflight semantics
3. runner/stepper tests for bounded HR behavior
4. orchestrator flow tests for signal loss, cap breach, and control loss
5. manual real-device validation with real trainer + real HR sensor

## Concrete Code Hotspots

These are the first files to revisit when implementation starts:

- `app/src/main/java/com/example/ergometerapp/workout/ExecutionWorkoutModel.kt`
- `app/src/main/java/com/example/ergometerapp/workout/ImportedErgoWorkoutExecutionMapper.kt`
- `app/src/main/java/com/example/ergometerapp/workout/ImportedErgoWorkoutExecutionPolicy.kt`
- `app/src/main/java/com/example/ergometerapp/workout/runner/WorkoutStepper.kt`
- `app/src/main/java/com/example/ergometerapp/workout/runner/WorkoutRunner.kt`
- `app/src/main/java/com/example/ergometerapp/workout/runner/ImportedHrRuntimeStateMachineV1.kt`
- `app/src/main/java/com/example/ergometerapp/session/SessionWorkoutReadinessEvaluator.kt`
- `app/src/main/java/com/example/ergometerapp/session/SessionOrchestrator.kt`

## Suggested First Implementation Branch

When coding starts, cut a new branch from the current docs checkpoint:

- `feat/hr-workouts-v1`

That keeps the planning/history branch intact while giving the runtime slice a
clean implementation branch with one explicit goal.

## Working Rule

Prefer one end-to-end supported HR steady path over partial support in several
layers.

If a choice would widen scope but reduce clarity, choose clarity.

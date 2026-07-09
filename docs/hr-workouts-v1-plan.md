# HR Workouts V1 Plan

## Purpose

This document locks the intended first public-test scope for `HR-based
workouts` in Ewoc.

It exists to answer one practical question before implementation continues:

What exactly should the app support in V1, and what must remain out of scope so
the feature can ship safely?

This is a scope-and-semantics plan, not a full implementation spec.

## Relationship To Existing HR Docs

- `docs/HR_CONTROL_RULES.md` remains the safety doctrine and invariant source
- this document records the current product scope, implementation status, and
  remaining follow-ups for the V1 imported-HR path

If there is a conflict:

1. safety constraints from `docs/HR_CONTROL_RULES.md` win
2. this document defines the intended V1 product scope
3. lower-level runtime details can be refined later as long as they stay
   within those two boundaries

## Current Implementation Status

| Area | Status | Notes |
| --- | --- | --- |
| Canonical HR targets and control metadata | Implemented | Canonical parsing/import preserves HR-targeted workouts and workout-level control metadata. |
| `heart_rate_relative` profile resolution | Implemented | Android import and editor preview resolve HR% targets through the rider-profile compile context. |
| Imported steady HR runtime path | Implemented | Imported HR steady segments run through explicit preflight, bounded power adjustment, fallback, cap throttle, and stop behavior. |
| Public app availability | Enabled through imported workouts | The current app supports the imported-HR path; this document does not expand the authored-workout surface beyond the implemented flows. |
| Mock/unit validation | Implemented | Focused tests cover the imported-HR controller and session orchestration paths; see continuity docs for the latest exact commands. |
| Documented live validation | Partially complete | Existing continuity notes record `SM-X210` imported-HR live validation for duplicate-callback removal, hard-cap behavior, chart target display, and later ordinary decrease pacing. |
| Real HR sensor coverage | Documented through existing imported-HR validation only | Do not infer broader sensor-matrix coverage beyond the recorded validation notes. |
| Minimum-power recovery | Open product decision | Current behavior remains one-way conservative until HR drops below the target band again. |
| Fast interval or aggressive ramp HR-primary control | Out of scope | These remain explicitly outside V1 HR-primary execution. |

## V1 Product Decision

V1 should support:

- authored and imported HR-targeted workouts as a real executable feature
- conservative bounded HR control for long steady HR blocks
- explicit user-visible failures when the required HR-control capabilities are
  missing
- hard-cap safety behavior and signal-loss fallback behavior

V1 should not require:

- advanced adaptive coaching
- aggressive closed-loop optimization
- interval-grade HR control
- automatic recovery after fallback or signal loss
- Health Connect

## Supported V1 Workout Semantics

### Supported target types

V1 should support these HR-oriented workout forms:

- `heart_rate` steady ranges
- `heart_rate_relative` steady ranges after they are resolved to concrete BPM
  through the existing compile path
- workout-level control metadata with a mandatory `hr_upper_cap_bpm`

### Supported segment shapes

V1 should support:

- steady HR-controlled blocks
- long aerobic HR-guided blocks
- recovery-oriented HR guidance

V1 should not support as primary HR-controlled execution:

- short intervals under 2 minutes
- aggressive ramps
- HIIT-style rapid alternation
- any workout shape that depends on rapid physiological response

Those shapes may still exist in workouts, but V1 should treat them as
power-based execution plus HR safety overlay only if that path is implemented
explicitly later. They should not silently reuse the steady HR controller.

## Core Runtime Behavior

### Start requirements

An HR-controlled segment may start only when all of the following are true:

- trainer control is granted
- live HR signal is available
- canonical control metadata resolves to a complete runtime policy

If any of these are missing at segment start:

- fail start explicitly
- show a user-visible reason
- do not silently degrade into a power-only segment

### Control style

V1 control should be deliberately conservative:

- start from authored `initialPowerWatts`
- adjust power in small bounded steps
- wait after increases before allowing another increase
- allow decreases sooner than increases
- clamp every command to authored min/max power bounds

The intended V1 behavior is:

- hold power while filtered HR remains in range
- increase slowly only when HR is below range and the signal is trustworthy
- reduce sooner when HR is above range
- block all increases immediately when uncertainty or safety concerns appear

### Hard-cap behavior

`hrUpperCapBpm` is a hard safety ceiling, not part of the target band.

When filtered HR reaches or exceeds the cap:

- block increases immediately
- reduce power immediately using a conservative safety target derived relative
  to the segment's `initialPowerWatts`
- clamp that safety target to one code-owned global ceiling so hard-cap
  behavior can never command unexpectedly high watts across different riders
- enter a safety-throttle state

If the cap breach persists:

- stop HR execution
- require explicit user acknowledgement before the workout is treated as safely
  resumable or complete

### Signal-loss behavior

If HR signal is lost during an HR-controlled segment:

- enter fallback immediately
- command a conservative safety fallback target derived relative to the
  segment's `initialPowerWatts`
- clamp that fallback target to the same code-owned global safety ceiling
- block all upward adjustment
- stop HR execution after the fallback transition

V1 should not auto-resume HR control when signal returns.

### Trainer-control loss behavior

If trainer control is lost during an HR-controlled segment:

- stop HR execution immediately
- surface an explicit failure reason

V1 should not pretend HR control can continue in read-only or degraded trainer
 states.

### Unreachable-target behavior

Unreachable targets are runtime outcomes, not import failures.

If the rider remains below target at `maxPowerWatts`:

- hold at `maxPowerWatts`
- surface an explicit “below target at max power” state

If the rider remains above target at `minPowerWatts`:

- hold at `minPowerWatts`
- surface an explicit “above target at min power” state
- continue hard-cap supervision

## Explicit Non-Goals For V1

The following should remain out of scope for the first public-test HR slice:

- PI/PID-style closed-loop control
- silent auto-recovery after signal loss
- automatic continuation after trainer-control loss
- broad Health Connect dependency or integration gating
- workout-shape expansion into fast ramps or short interval HR control
- hiding capability failures behind generic start errors

## Future-Proofing Constraints

The current V1 slice should stay narrow, but it should not paint the future
runtime into a corner. Keep these constraints in place while implementing V1:

- Keep signal processing, state estimation, decision logic, safety gating, and
  coaching presentation as separate seams even if V1 initially implements only
  the deterministic HR path.
- Preserve explicit signal-quality concepts such as confidence, freshness, and
  sensor-loss handling instead of collapsing everything into a nullable heart
  rate value.
- Keep AI advisory-only for any future in-session usage. AI may eventually
  suggest wording or emphasis, but it must stay behind deterministic fallback
  and safety filtering.
- Do not introduce a new training protocol DSL for this direction. Canonical
  EWO plus existing workout-import paths are already the right data boundary.
- Keep any future “coach personality” or message-style layer separate from the
  decision engine so presentation can evolve without changing safety behavior.
- Design for replayability and shadow-mode validation even if those tools are
  not fully implemented in the first execution slice.

These are future-enabling constraints, not new requirements for the current
implementation scope.

## Remaining Delivery Shape

### Phase A: Keep semantics aligned

Before feature implementation expands further, keep these surfaces aligned:

- `ImportedErgoWorkoutExecutionPolicy`
- `ImportedHrRuntimeStateMachineV1`
- readiness and UI failure messaging
- tests and diagnostics wording

around the same explicit V1 behavior.

### Phase B: Preserve one complete HR execution path

The V1 goal remains one coherent path, not all HR ideas:

- authored/imported HR steady segment
- explicit start preflight
- bounded runner behavior
- visible fallback and stop behavior
- summary and diagnostics that explain what happened

### Phase C: Real-device validation

Before broadening the feature or changing safety policy, keep validating on:

- real trainer
- real HR sensor
- at least one representative phone form factor
- at least one representative tablet form factor when available

Cover at least:

- successful start
- HR missing at start
- trainer control missing at start
- temporary HR signal loss
- persistent cap breach
- unreachable high / unreachable low behavior
- manual stop and summary handling

## Implementation Notes From Current Repo State

The current repository already has useful seams in place:

- canonical parsing and validation for HR-targeted workouts
- compile-time resolution for `heart_rate_relative`
- preserved workout-level HR control metadata
- explicit `ImportedHrExecutionPolicyV1`
- explicit imported-HR start preflight
- explicit HR runtime state-machine tests for start, fallback, cap, and stop

The reviewed March 15, 2026 v4 PDF mostly reinforced this direction rather than
changing it. The most valuable carry-forwards were:

- keep the deterministic safety path local-first
- keep degraded behavior explicit instead of silent
- avoid coupling future AI wording/style layers to control decisions
- leave room for replay and shadow-mode validation

The main missing piece is no longer vocabulary or initial runner/session
hookup. The remaining risk is policy confidence: longer real rides must decide
whether the one-way minimum-power behavior remains acceptable or needs a
cautious, explicitly approved recovery rule.

## Working Rule

When implementation choices are ambiguous, prefer:

- explicit failure over silent degradation
- conservative bounded behavior over smarter-looking control
- one fully testable steady-HR path over broader but partially reliable scope

Use the current implementation status above and focused runtime findings to
choose future work. The completed implementation checklist now lives in Git
history rather than in the active documentation tree.

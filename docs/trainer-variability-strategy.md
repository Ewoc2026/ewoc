# Trainer Variability Strategy

## Purpose

This note captures the main product and engineering lessons from the
post-workout `Continue ride` investigation, but the guidance is intended to
apply more broadly to FTMS-controlled trainer features.

The core takeaway is simple:

- We do not integrate with one clean abstract "FTMS trainer".
- We integrate with many partially observable trainer state machines.
- Some devices will follow the expected control flow closely.
- Some will expose the same capability through different timing, callback
  order, disconnect behavior, or control-release semantics.

That variability is not an exception to engineer around once. It is a durable
constraint of the product.

## What Makes This Risky

The biggest future risk is not that trainers differ.

The biggest risk is that app logic silently assumes one specific success path:
for example, one exact ordering of control acknowledgement, disconnect,
settle delay, reconnect, and telemetry resumption. Once production code starts
depending on a single timing model, every new trainer becomes a hidden
compatibility bet.

In practice, trainer differences usually appear in three layers:

1. Timing differences
   One trainer responds immediately, another needs a visible dwell window.
2. Event-order differences
   One trainer disconnects before control is truly free, another keeps the
   link alive longer and only releases control later.
3. Behavioral differences
   One trainer supports a same-session recovery path, another only behaves
   correctly after an explicit exit and fresh session start.

The first class is manageable with good sequencing.
The second requires explicit state-machine design.
The third may require trainer-specific capability or fallback policy.

## Design Principles

### 1. Keep trainer protocol state explicit

Trainer-sensitive flows should be represented as explicit phases instead of
distributed callback soup.

Example:

- `workout_complete`
- `exit_prep_requested`
- `disconnect_observed`
- `settle_window_open`
- `fresh_session_starting`
- `fresh_session_ready`

This keeps timing and recovery rules visible, testable, and adaptable when one
trainer behaves differently from another.

### 2. Separate logical ride continuity from trainer transport behavior

Session continuity, summary totals, FIT export, and cumulative live metrics
must remain app-owned concepts.

Trainer counters can reset.
Trainer control can drop.
Trainer restart behavior can vary.

The app should still preserve one logical ride when the product intends one
logical ride.

This separation reduces the blast radius of trainer-specific protocol changes.

### 3. Prefer conservative fallback over ambiguous "maybe success"

If the app cannot prove that the trainer has really exited the previous mode
and is ready for the next one, it should prefer the safer path.

Examples:

- wait for explicit disconnect plus settle window before reconnect
- fall back from same-session reuse to hard cutover
- keep unknown trainers on the conservative path until evidence says
  otherwise

Reliable fallback is usually more valuable than a fragile "fast path".

### 4. Treat trainer behavior profiles as a first-class extension point

Not every difference needs a large compatibility database, but the codebase
should be structured so we can express trainer-specific behavior if needed.

Useful profile dimensions may include:

- requires explicit disconnect window before reconnect
- supports same-session continuation
- needs extended settle delay
- requires control reacquisition before visible session resume
- should default to hard cutover for post-workout continuation

Even if most trainers share defaults, the architecture should leave room for
targeted overrides.

Current concrete candidate:
the `explicitTrainerReconnectSettleDelayMs` gate in
`SessionOrchestrator.continueRideRestartWindowOpen()` is the single timing seam
between observed disconnect and safe reconnect. It is hardcoded today because
we only have one real-trainer profile, but that predicate is already the right
place to promote the delay into a per-trainer profile or replace it with an
explicit trainer-ready signal later.

### 5. Build observability into the protocol boundary

Trainer integration bugs are much easier to solve when the app emits stable,
human-readable lifecycle markers at the feature boundary.

Good observability for trainer-sensitive flows includes:

- stable `TEST_MARKER` events for key milestones
- status dumps that describe ownership, readiness, and active protocol phase
- build identification that proves which APK is actually running
- a repeatable adb validation routine for install, relaunch, and log capture

Without that, every new device investigation becomes guesswork.

### 6. Decide product policy early

Engineering strategy depends on product expectations.

We should be explicit about questions like:

- Must every supported trainer provide the same seamless continuation UX?
- Is a slower but safe fallback acceptable on some devices?
- Do we optimize unknown trainers for speed or safety?

Clear product policy prevents endless optimization toward an unrealistic
uniform behavior target.

## Recommended Engineering Posture

For future trainer-sensitive features, prefer this default posture:

- optimistic architecture
  Build room for a fast path.
- conservative runtime behavior
  Use it only when readiness is proven.
- app-owned continuity
  Keep user-visible ride continuity separate from trainer resets.
- explicit fallback
  Degrade to a safe path deliberately, not accidentally.
- live-device evidence
  Trust real hardware behavior over assumptions from protocol reading alone.

## Practical Readiness Checklist

Before shipping a new trainer-sensitive flow, ask:

- Is the state machine explicit?
- Is success based on observed readiness instead of assumed timing?
- Does a safe fallback exist?
- Are user-visible totals independent from trainer-side resets?
- Can live validation prove which step failed?
- Can we support trainer-specific policy later without rewriting the flow?

If several answers are "no", the implementation is likely to become a future
compatibility swamp.

## What This Means For Ewoc

The `Continue ride` work already moved the app in a healthier direction:

- logical ride continuity is preserved separately from trainer reset behavior
- post-workout handoff is expressed through more explicit phases
- validation now uses stable markers and repeatable adb helpers
- real-device behavior is treated as the source of truth

That approach should become the default for other trainer-sensitive areas too,
including session start/restart, control reacquisition, baseline workflows,
and any future feature that expects a trainer to move cleanly between protocol
modes.

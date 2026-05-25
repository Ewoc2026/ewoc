# HR_CONTROL_RULES

## Purpose

This document translates the sports-medicine and safety review into software rules for an HR-controlled cycle ergometer application. It is intended to be implementation-facing, conservative, and suitable for a minimum safe v1 consumer product.

Source basis: HR-controlled cycle ergometer safety review. Key findings include delayed HR dynamics, significant sensor-quality risk, the need for an independent safety supervisor, the importance of an HR upper cap, and the limited suitability of HR as a primary controller for short intervals and aggressive ramps. fileciteturn0file0

## Design principles

1. HR is a slow control signal.
   - Do not treat second-to-second HR changes as reliable evidence of exercise-state change.
   - Assume meaningful HR response to a power change may take tens of seconds. fileciteturn0file0

2. Sensor uncertainty is a first-class safety problem.
   - If HR data is unreliable, incomplete, stale, implausible, or disconnected, the system must not increase power.
   - “No increase on uncertainty” is mandatory. fileciteturn0file0

3. Safety is independent of coaching logic.
   - Split runtime logic into:
     - PrimaryController
     - SafetySupervisor
   - SafetySupervisor always has final authority to block increases, reduce power, enter fallback, or stop the workout. fileciteturn0file0

4. HR upper cap is mandatory.
   - Every HR-guided workout must define or derive a hard cap enforced by SafetySupervisor.
   - Hard cap overrides all workout logic. fileciteturn0file0

5. Avoid HR as primary control where physiology makes it unsuitable.
   - Do not use HR as the primary controller for short intervals, aggressive ramps, or HIIT-style short work bouts.
   - Use power-based execution plus HR safety limits for those cases. fileciteturn0file0

## Runtime architecture

## PrimaryController responsibilities

PrimaryController may:
- read workout targets
- read validated HR input
- estimate target power for the current workout phase
- make slow, bounded suggestions for power changes

PrimaryController may not:
- bypass HR upper cap
- raise power when signal quality is below threshold
- continue normal control while in FALLBACK or STOPPED
- override SafetySupervisor decisions

## SafetySupervisor responsibilities

SafetySupervisor must:
- validate HR signal plausibility and freshness
- track HR cap, trend, and persistence above thresholds
- enforce no-increase-on-uncertainty
- clamp or reduce power when risk conditions are met
- transition runtime state between NORMAL, CAUTION, SAFETY_THROTTLE, FALLBACK, STOPPED
- trigger workout stop when stop criteria are met fileciteturn0file0

## Recommended state model

- NORMAL
- CAUTION
- SAFETY_THROTTLE
- FALLBACK
- STOPPED

### NORMAL
Conditions:
- HR signal quality good
- HR inside target zone or behaving normally
- no safety threshold exceeded

Allowed actions:
- hold power
- make small, infrequent power adjustments

### CAUTION
Conditions:
- HR rising toward limits
- HR slightly above target
- uncertain transient after a recent power increase
- emerging but not critical signal-quality concerns

Allowed actions:
- hold power
- reduce power slightly
- extend waiting time before any next increase

Forbidden actions:
- aggressive power increases

### SAFETY_THROTTLE
Conditions:
- HR exceeds the soft safety envelope
- HR trend remains concerning
- overshoot is likely or already happening

Allowed actions:
- reduce power immediately
- suppress all power increases for a defined holdoff period

### FALLBACK
Conditions:
- HR signal quality bad
- HR signal stale or disconnected
- implausible HR behavior
- controller cannot safely trust HR

Required actions:
- block all power increases
- move power to conservative fallback level
- notify user
- require signal recovery before returning to NORMAL or CAUTION fileciteturn0file0

### STOPPED
Conditions:
- hard cap violation persists
- symptoms reported
- safety-critical condition detected
- fallback failure persists

Required actions:
- end workout control
- set power to minimum-safe level
- prompt user to cool down or seek help depending on symptoms
- do not auto-resume without explicit user action fileciteturn0file0

## HR signal rules

## Source preference

Preferred source order:
1. Chest strap / electrode-based HR
2. Other validated external HR source
3. Wrist PPG only as lower-trust source

Implementation rule:
- If using lower-trust HR sources, safety thresholds must be more conservative.
- If signal quality cannot be assessed, treat the source as uncertain. fileciteturn0file0

## Signal validity checks

Reject or distrust HR samples when any of the following applies:
- sample missing
- source disconnected
- sample older than freshness threshold
- HR outside plausible physiological range configured by app
- implausible jump compared with recent signal history
- repeated oscillation pattern strongly suggesting artifact
- source status explicitly reports poor contact or low quality

Implementation consequence:
- invalid or uncertain HR must never justify a power increase. fileciteturn0file0

## Signal handling rules

1. Smooth HR enough to reduce noise, but do not over-smooth.
2. Use a short robust filter appropriate for consumer sensors.
3. Keep raw and filtered HR available for diagnostics.
4. Base control decisions on filtered HR plus trend and signal quality.
5. Base stop/fallback decisions on both filtered behavior and raw-signal plausibility. fileciteturn0file0

## Workout-type rules

## Allowed as primary HR-controlled modes in v1

1. Steady HR zone
2. Long aerobic blocks
3. Recovery riding
4. HR upper cap overlay on any workout

These are the safest initial modes because they tolerate slow control and wider deadbands. fileciteturn0file0

## Not allowed as primary HR-controlled modes in v1

1. Short intervals under 2 minutes
2. Aggressive HR ramps
3. HIIT with HR as the main control target
4. Any workout requiring rapid load alternation

These must be implemented as power-based workouts with HR used only as safety overlay. fileciteturn0file0

## Power-adjustment rules

## General rules

1. Power increases must be slow and infrequent.
2. After increasing power, wait long enough for HR response before considering another increase.
3. Power decreases may happen sooner than increases.
4. Power increases must be smaller and rarer than power decreases.
5. SafetySupervisor may always reduce power regardless of PrimaryController intent. fileciteturn0file0

## When power may increase

Power may increase only if all are true:
- current state is NORMAL
- HR signal quality is good
- HR is below target zone or lower edge
- HR trend is not already clearly rising toward target
- sufficient waiting time has passed since last power increase
- HR is comfortably below hard cap
- no symptom flag is active
- user is not in fallback recovery holdoff window fileciteturn0file0

## When power must not increase

Power must not increase if any are true:
- signal uncertain
- state is CAUTION, SAFETY_THROTTLE, FALLBACK, or STOPPED
- HR near hard cap
- HR trend rising rapidly
- recent increase still within response waiting window
- user-reported symptom active
- source indicates poor contact
- app detects implausible HR behavior fileciteturn0file0

## When power should decrease slightly

Decrease slightly when:
- HR is modestly above target zone
- HR trend continues upward after prior increase
- drift is pushing HR upward during a long steady block
- control needs stabilization without immediate danger

Goal:
- prevent overshoot without causing abrupt workout disruption fileciteturn0file0

## When power must decrease sharply

Decrease sharply when:
- HR exceeds hard cap
- HR continues rising despite a prior reduction
- user reports warning symptoms
- signal confidence drops during active HR control
- safety envelope is violated in a way that may lead to syncope, severe overshoot, or heat-related deterioration fileciteturn0file0

## Hard cap rules

## Requirement

Every HR-controlled workout must have a hard cap enforced by SafetySupervisor.

## Behavior

If filtered HR crosses hard cap:
1. immediately reduce power
2. suppress all power increases
3. monitor whether HR begins to fall
4. if HR remains above cap or continues to rise, transition toward STOPPED

If cap violation persists beyond configured tolerance:
- stop workout
- move to minimum-safe power
- prompt user appropriately fileciteturn0file0

## Symptoms and manual safety

## User-reported stop symptoms

Immediate stop criteria:
- chest pain or pressure
- dizziness
- near-syncope or faint feeling
- nausea or vomiting
- confusion or unusual disorientation
- severe shortness of breath beyond expected effort
- any “something is wrong” emergency input

On symptom stop:
- terminate workout control
- reduce power to minimum-safe level
- instruct user to stop or cool down depending on severity
- show escalation advice for urgent symptoms fileciteturn0file0

## Manual user control

App must provide:
- obvious stop control in UI
- immediate pause/stop path
- clear override priority over workout logic
- compatibility with physical ergometer emergency stop when available fileciteturn0file0

## Warm-up and cool-down rules

1. HR-controlled workouts must include a warm-up phase.
2. HR-controlled workouts must include a cool-down phase.
3. Normal stop behavior is not abrupt zeroing of effort.
4. Default safety stop behavior should reduce to minimum-safe power and encourage gentle pedaling unless severe symptoms require immediate cessation. fileciteturn0file0

## MaxHR and age rules

1. Age and user-entered maxHR may be used as reference inputs.
2. They must not be the sole safety basis.
3. App should cross-check user-entered maxHR against age-predicted expectation and warn on large mismatch.
4. When uncertain, safety logic should be conservative.
5. Overestimated maxHR is more dangerous than underestimated maxHR. fileciteturn0file0

## Medication and exclusion rules

HR-guided control should not be primary when:
- user reports beta-blocker use
- user reports major cardiovascular symptoms
- user reports relevant medical restrictions
- HR response appears blunted or inconsistent with workload
- HR source remains unreliable

In these cases:
- disable HR-primary control
- allow manual or power-based workouts with HR safety overlay only fileciteturn0file0

## Minimum safe v1 scope

Implement in v1:
1. Steady HR zone control
2. HR upper cap
3. Signal quality gate
4. No-increase-on-uncertainty
5. SafetySupervisor state machine
6. Warm-up and cool-down
7. Symptom-based stop flow
8. Fallback on signal loss
9. Power-based workouts with HR safety overlay

Do not implement in v1:
1. HR-primary short intervals
2. HR-primary aggressive ramps
3. HR-primary HIIT
4. Safety logic based only on age formula or user maxHR
5. Automatic diagnosis of arrhythmia from HR alone fileciteturn0file0

## Implementation checklist

Before enabling HR-primary control, app must have:
- strict input validation
- HR source quality model
- filtered and raw HR pathways
- independent SafetySupervisor
- hard cap enforcement
- fallback behavior
- symptom stop UI
- workout stop state
- cool-down handling
- test coverage for signal loss, cap crossing, symptom stop, and overshoot-prevention behavior

## Notes

This file is intentionally conservative. It favors consumer safety over aggressive training optimization because the source analysis identifies HR delay, signal uncertainty, and heat/symptom risk as the main failure paths in a non-clinical consumer environment. fileciteturn0file0

# Desktop Editor

The Ewoc desktop editor is for creating, previewing, and exporting structured workouts with more space and control than the in-app mobile editing path. This guide explains the current editor at a practical level: how to build a workout, what the main screen areas mean, and what to do when export is blocked.

## What the Desktop Editor Is For

Use the desktop editor when:
- you want to build a workout from scratch
- you want to inspect or revise an existing structured workout
- you want more room to manage steps, preview output, and validation feedback

The editor is best treated as a structured authoring tool, not a free-form notebook. It is designed around rideable workout steps, preview context, and export validation.

## What a Workout Is Made Of

At the simplest level, a workout is made of:
- a title
- one or more segments
- optional preview/profile context
- optional rider-facing messages

The main structured segment types are:
- `Steady`
- `Ramp`
- `Repeat`

Important current limitation:
- `Free Ride` may still exist in legacy content, but it is not the normal authoring path now

If you are new to structured workouts, think in terms of:
- how long each step lasts
- what target the rider should hold
- how the whole session warms up, works, recovers, and cools down

## Main Areas of the Editor

The exact layout may evolve, but the current mental model should stay roughly the same.

### Document and Title

This is the basic identity of the workout.

Check here first:
- title is present
- document is the one you intended to edit

If the title is blank, export can be blocked.

### Segments

This is the core workout structure.

You use it to:
- add steps
- reorder steps
- duplicate steps
- delete steps
- inspect repeat blocks and their children

If you are creating your first workout:
- start with one warmup-like segment
- add one main work block
- finish with one cooldown-like segment

### Properties

This is where you edit the selected segment.

Typical fields include:
- duration
- target power
- FTP-based target mode
- repeat count
- optional labels or notes

Change one thing at a time when learning the editor. It is easier to understand the result when you are not changing several fields at once.

### Athlete Profile and Preview Context

Preview context helps the editor estimate things such as workout profile, IF, and TSS.

Important:
- athlete profile preview values are not the same as saving those values into the exported workout file
- they are there to make preview and interpretation more useful

If preview values look wrong:
- check FTP first
- then check any other rider-profile fields used by the current target type

### Preview and Validation

The editor can show:
- workout profile preview
- compiled steps
- validation issues
- compile notes or sanity warnings

This area answers two different questions:
- what this workout will roughly look like
- whether the workout is valid enough to export

## Create a Simple First Workout

If you want the shortest possible successful path:

1. Create a new workout.
2. Add a title.
3. Add a first steady or ramp segment as a warmup.
4. Add one main work block.
5. Add one easier final segment as a cooldown.
6. Review preview and validation.
7. Export only after the document is marked ready.

Good first patterns:
- one ramp warmup, one steady block, one steady cooldown
- one ramp warmup, one repeat interval block, one ramp cooldown

Avoid trying to model an advanced training idea in your very first draft.

## Edit Segments

### Steady

Use steady segments when:
- the target should stay constant for the whole step
- you want simple work or recovery blocks

### Ramp

Use ramp segments when:
- effort should rise or fall gradually
- you are building a warmup, cooldown, or transition

### Repeat

Use repeat blocks when:
- you want a repeating interval pattern
- you want cleaner structure instead of manually duplicating the same pair many times

Repeat blocks are usually the clearest way to build interval sessions.

## Messages and Rider-Facing Cues

Some workouts benefit from short rider-facing messages.

Use messages when:
- a block change needs a cue
- you want a short reminder before or after a key transition

Keep messages short and practical. The rider should understand them quickly during the ride.

## Validate and Export

Treat preview and export as separate checks.

Preview tells you:
- whether the structure looks sensible
- whether estimated workout shape and totals look roughly right

Validation tells you:
- whether export is currently allowed

If export is blocked:
- read the validation issues first
- fix blocking errors before changing other optional details

Typical reasons export may be blocked:
- blank workout title
- invalid segment values
- unsupported or incomplete structure

## Legacy Free Ride Content

You may still encounter older workouts that contain `Free Ride`.

Current expectation:
- legacy content may still be visible
- it is not the normal authoring direction now
- if you want a modern structured workout, replace those sections with explicit structured steps

Do not build new workouts around Free Ride if the goal is a predictable structured session.

## Common Editing Errors

### Export Is Blocked

Start with:
- title present
- no visible validation errors
- all required segment fields filled in

### Preview Looks Wrong

Start with:
- FTP value
- selected segment target values
- repeat count or child structure

### Workout Feels More Complicated Than Expected

Simplify it:
- fewer segment types
- fewer repeated patterns
- one clear main interval idea

The editor gets easier once the workout idea itself is simple.

## Best Practices

- Build the simplest version first.
- Use repeat blocks for interval structure.
- Treat warmup and cooldown as explicit parts of the workout.
- Fix export blockers before polishing details.
- Use preview context to understand the workout, not as a substitute for clear structure.

## If You Already Know Structured Workouts

If you are coming from another workout format or editor:
- map your existing workout to `Steady`, `Ramp`, and `Repeat`
- use the editor preview to verify the shape
- keep the first import or rebuild conservative

You do not need to learn every field before producing a usable workout.

## What This Guide Does Not Cover Yet

This first draft does not try to be a full reference for:
- every field in the editor
- every edge case in preview math
- future onboarding UI inside the editor itself

It is meant to help you get to a first successful result without guessing the core workflow.

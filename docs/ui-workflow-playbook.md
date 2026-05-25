# UI Workflow Playbook (Beginner-Friendly)

## Purpose
Use this process for UI work to reduce rework and keep progress measurable.

## Scope First
Define the usage context before visual details:
- primary device orientation (portrait/landscape)
- typical posture and viewing distance
- top 3 signals the user must read in 1-2 seconds
- primary action and failure action

Do not start with spacing/font tuning before this is written.

## State Matrix Before Layout
List all UI states first, then decide content per state.

Template:
- state name
- trigger into state
- must-show information
- primary action
- waiting/error indicator

For session flows, include at minimum:
- menu
- connecting
- waiting_start
- running
- paused
- stopping
- summary

## Wireframe Before Styling
Create a block-level layout first (paper or simple mock):
- what is top/middle/bottom
- what is always visible
- what can be collapsed

No color/font decisions yet.

## Build Static UI First
Implement structure with static/mock values before live BLE/runtime wiring.

Exit criteria:
- information hierarchy is readable on target tablet
- no critical clipping/wrapping
- controls are discoverable

## Layout Invariants Before Structural Changes
Before changing an existing layout structure, write the invariants that must
stay true.

Typical invariants:
- primary metrics remain readable in 1-2 seconds
- primary action stays visible without hunting
- critical controls are not pushed off-screen
- charts or secondary panels do not hide session-critical content
- the layout still works in both phone/tablet and portrait/landscape modes
  that the screen actually supports

Do not rely on "looked fine in one screenshot" as proof for a structural
layout change.

## Lock One Baseline Early
Pick one baseline mode first and finish it:
- recommended: landscape-first if real usage is mostly landscape
- add secondary orientation after baseline is accepted

This avoids solving two layout problems at the same time.

## Visual Rules Up Front
Set stable visual rules before iterative tweaks:
- spacing scale
- card padding and minimum sizes
- typography sizes for primary/secondary metrics
- dark-mode background and contrast rules
- waiting-state feedback pattern (for example animated dots)

## Validation Loop
Each iteration must produce:
- what changed
- commands run
- screenshot path
- PASS/FAIL result
- next decision

Use short loops and make one decision per loop.

## Validation Matrix For Existing Layout Changes
If a UI change can affect layout behavior, validate the affected matrix and
say explicitly what was checked.

Default matrix:
- phone portrait
- phone landscape
- tablet portrait
- tablet landscape

If one of these is not relevant for the screen, say why.

If the whole affected matrix cannot be checked in the current slice, do not
present the layout as fully validated. Either narrow the change or leave a
clear follow-up note.

## Presets and Advanced Options
Add presets only after baseline hierarchy is accepted.

Reason:
- presets multiply test cases
- early presets hide unresolved hierarchy issues

## Drift Signals (When to Pause and Reset)
If two or more happen, pause and reset to this playbook:
- repeated small visual tweaks without a stable baseline
- same screen re-ordered many times without state-matrix update
- frequent subjective debate without screenshot-based checkpoints
- regression fixes appear faster than new feature progress

## Team Split
Recommended ownership:
- Assistant:
  - branch/PR flow
  - implementation batches
  - ADB screenshots/log capture
  - validation command execution
  - release notes / handoff docs
- User:
  - physical-device readiness (trainer wake, environment state)
  - real riding ergonomics judgement
  - final product priority and go/no-go decisions

## Reminder Rule
When workflow drifts, the assistant should explicitly say:
"We are drifting from the UI workflow playbook. I recommend resetting to: context -> state matrix -> baseline layout -> validation loop."

For structural existing-layout changes, also remember:
"Trust is the default, but structural layout changes must still name their invariants and validation matrix."

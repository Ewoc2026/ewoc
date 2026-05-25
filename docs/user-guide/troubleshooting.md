# Troubleshooting

This page covers the most common issues users are likely to hit during setup, trainer connection, guided rides, and workout-file import. Start with the shortest checks first.

## Trainer Not Found

If the trainer does not appear in the scan list:

- Make sure Bluetooth is enabled on the phone or tablet.
- Wake the trainer by pedaling or powering it on again.
- Move the device closer to the trainer.
- Close other apps that may already be connected to the trainer.
- Try scanning again after a short pause.

If the trainer still does not appear:
- restart Bluetooth on the device
- restart the trainer if possible
- run the compatibility path if available

## Trainer Connects but Start Is Still Blocked

If the app connects to the trainer but you still cannot start:

- return to the setup summary
- check which setup step is still incomplete
- confirm that the rider profile has a valid FTP value
- confirm that the selected mode has a real workout or a valid telemetry-only path

The setup summary is usually the fastest place to see what still needs attention.

## Trainer Connects but the Ride Does Not Behave as Expected

Examples:
- no trainer resistance change
- targets look wrong
- session feels like telemetry only when you expected guided control

Check these first:
- confirm the selected mode is a guided workout, not telemetry only
- confirm the correct workout was selected
- confirm FTP is set to a realistic value
- confirm no other app is still controlling the trainer

If the problem continues:
- include trainer brand and model in any support message

## Workout File Will Not Import

If workout import fails, the most likely causes are:
- unsupported content inside the file
- unsupported file type
- a damaged or empty file

Check these first:
- confirm the file extension is one Ewoc supports in that import path
- try opening a different known-good workout file
- confirm the file is not empty

Important current limitation:
- files that contain `free_ride` segments are intentionally not accepted on the app import path

If you see an import error:
- read the action-oriented message first
- keep the technical detail if you plan to report the problem

## Workout Looks Close but Not Quite Right

Use the editor when:
- the workout idea is good but one block should change
- the duration is close but you want exact tuning
- you want a different title or a clearer structure

Do not treat every near miss as a failure. In many cases, editing the workout is faster than finding another file.

## Session Ends Unexpectedly or Connection Drops

First steps:
- keep the phone or tablet near the trainer
- confirm battery/power state on the trainer
- avoid other Bluetooth-heavy apps during the ride

If the issue looks repeatable:
- include the trainer model, host device model, and what happened right before the failure

## What To Send to Support

If you contact support, include:
- what you were trying to do
- device model
- trainer model
- whether the issue happened during setup, import, editing, or the live ride
- screenshots if they help

Short support reports are fine if they contain the important facts.

Good example:
- `Android tablet, trainer model, trainer connected but guided workout would not start after importing a .zwo file`

## When To Stop Troubleshooting and Just Ride

Choose a simpler path for the current session if needed:
- use one of the built-in workouts
- use telemetry-only mode
- use a known-good existing workout

It is better to get a usable ride today and debug the perfect setup afterward than to lose the whole session to troubleshooting.

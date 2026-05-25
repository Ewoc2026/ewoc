# Getting Started

Ewoc helps you ride an FTMS-compatible smart trainer with a clear path from setup to session summary. This guide is for the first time you want to connect a trainer, choose a workout mode, and start riding.

## What You Need

- An Android phone or tablet with Bluetooth enabled.
- An FTMS-compatible smart trainer or exercise bike.
- Optional: a BLE heart-rate monitor.

Before you start:
- Wake the trainer and keep it nearby.
- Make sure no other app is trying to control the trainer at the same time.
- If you use a heart-rate strap, wake it before scanning.

## Follow the Setup Flow

Ewoc uses a step-by-step setup flow before each session. The exact UI may evolve, but the core path stays the same:

1. Set up the rider profile.
2. Connect the trainer and any optional sensors.
3. Choose the workout mode.
4. Review the summary and start the session.

## Set Up the Rider Profile

The most important profile value is FTP. Ewoc uses FTP to scale workout targets and chart previews.

If you already know your FTP:
- Enter your current value in watts.

If you do not know your FTP yet:
- Start with a reasonable estimate.
- Update it later after more riding or after completing the baseline FTP ramp test.

Optional profile details such as age and sex help when heart-rate-based behavior or guidance needs them. They are less important than getting FTP roughly right.

## Connect Your Trainer

In the device step, search for the trainer and choose it from the list.

If the trainer does not appear:
- Check that Bluetooth is enabled.
- Make sure the trainer is powered on and awake.
- Move the phone or tablet closer.
- Close other apps that might already be connected to the trainer.

If you want extra confidence before your first real ride:
- Run the compatibility check when that option is available.

## Choose a Workout Mode

Ewoc supports more than one way to ride. Pick the mode that matches what you are trying to do today.

### Built-In Workout

Use this if:
- You want the simplest possible first guided ride.
- You want to feel how app-controlled trainer riding works before exploring other features.

### FTP Ramp Test

Use this if:
- You want a first estimate of your current FTP.
- You want to refresh an older FTP value with a structured baseline test.

### Import Workout File

Use this if:
- You already have a workout file you want to ride.
- You want to open an existing `.zwo`, `.xml`, or `.ewo` file through Ewoc.

### Telemetry-Only Ride

Use this if:
- You want to ride without a structured workout.
- You only need live telemetry and a session record.

## Review the Summary

Before you start, check:
- trainer connection status
- selected workout mode
- estimated workout duration
- rider profile basics, especially FTP

If something looks wrong, go back one step and fix it before starting.

## Start Your First Ride

When everything looks ready:
- press the start action
- begin pedaling when prompted
- follow the on-screen targets and messages during the session

At the end of the ride, Ewoc takes you to a summary view with key session metrics such as duration, power, cadence, and other session totals.

## Good First-Ride Choices

If you are not sure what to do first, start here:

- easiest first guided session: one of the built-in workouts
- best first FTP estimate path: `FTP Ramp Test`
- lowest-friction fallback: `Telemetry-only ride`

## If Something Does Not Work

Use these first checks:

- Trainer not found: re-check Bluetooth, wake the trainer, and close other trainer-control apps.
- Start is blocked: return to the summary and look for the step that is still incomplete.
- File import failed: confirm the file format and see the import error guidance in the app.

For deeper help, see:
- [Troubleshooting](troubleshooting.md)

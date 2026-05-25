# Ewoc Privacy

Updated: 2026-05-25

Ewoc is a free public-source app. This public version does not include Play
Billing, AI workout generation, support-bundle upload/export flows, or Health
Connect.

## Data Stored On The Device

Ewoc stores app settings on your device, including rider profile values such as
FTP and heart-rate profile settings, selected Bluetooth devices, workout-folder
permissions, imported or edited workout files, and local session/export state.

Session diagnostics can still exist as local runtime/debug state inside the app,
but this branch no longer exposes a user-visible support-bundle export or any
backend upload path.

## Network Use

The public-release app does not use a backend for AI workout generation,
subscriptions, Health Connect data, or support-bundle uploads.

Opening documentation, changelog, privacy, or issue links leaves the app and
opens GitHub in your browser.

## Bluetooth And Workout Data

Ewoc uses Bluetooth to communicate with a compatible indoor trainer and,
optionally, a heart-rate sensor during a ride. Workout files and FIT exports are
created or selected through Android storage flows chosen by the user.

## Health Connect

Health Connect is not part of this public version. Ewoc does not request Health
Connect permissions or read Health Connect records.

## Contact And Issues

For the public-source version, use the project issue tracker:

- `https://github.com/Ewoc2026/ewoc/issues`

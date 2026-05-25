# ZWO Format Reference (Offline)

This document is an offline reference for the Zwift workout file format (`.zwo`).
Its purpose is to keep daily development independent from online lookups.

## Source Snapshot
- Primary field inventory source:
  - <https://github.com/h4l/zwift-workout-file-reference>
  - <https://raw.githubusercontent.com/h4l/zwift-workout-file-reference/master/zwift_workout_file_tag_reference.md>
- Snapshot date used for this document: `2026-02-18`
- Observed identifiers in the source structure index:
  - `46` element names
  - `57` attribute names

## 1. File Identity
- A `.zwo` file is an XML document.
- A `.xml` file can contain the same structure.
- Ewoc accepts both `.zwo` and `.xml` on import when content matches workout XML.

## 2. Canonical Skeleton
```xml
<?xml version="1.0" encoding="UTF-8"?>
<workout_file>
  <author>Author Name</author>
  <name>Workout Name</name>
  <description>Optional description</description>
  <sportType>bike</sportType>
  <durationType>time</durationType>
  <tags>
    <tag name="tag1"/>
    <tag>tag2</tag>
  </tags>
  <workout>
    <Warmup Duration="300" PowerLow="0.50" PowerHigh="0.75" Cadence="85"/>
    <SteadyState Duration="600" Power="0.80"/>
    <IntervalsT Repeat="4" OnDuration="60" OffDuration="60" OnPower="1.00" OffPower="0.60"/>
    <Cooldown Duration="300" PowerLow="0.60" PowerHigh="0.40"/>
  </workout>
</workout_file>
```

## 3. Root-Level Elements Observed
All element names below were observed in Zwift workouts or related metadata:

- `workout_file`
- `activitySaveName`
- `author`
- `author_alias`
- `authorIcon`
- `category`
- `categoryIndex`
- `description`
- `durationType`
- `entid`
- `ftpFemaleOverride`
- `ftpMaleOverride`
- `ftpOverride`
- `name`
- `nameImperial`
- `nameMetric`
- `overrideHash`
- `painIndex`
- `setFtpAtPercentage`
- `ShowCP20`
- `Skippable`
- `sportType`
- `subcategory`
- `tags`
- `tag`
- `test_details`
- `Tutorial`
- `visibleAfterTime`
- `visibleOutsidePlan`
- `workout`
- `workoutLength`
- `WorkoutPlan`

## 4. `<workout>` Child Elements Observed

### Executable Step-Like Nodes
- `Warmup`
- `Cooldown`
- `SteadyState`
- `SolidState`
- `Ramp`
- `IntervalsT`
- `FreeRide`
- `Freeride`
- `MaxEffort`
- `RestDay`

### Event/Annotation Nodes
- `TextEvent`
- `textevent`
- `TextNotification`
- `gameplayevent`

## 5. Attribute Inventory by Element (Observed)

### `Warmup`
- `Cadence`
- `CadenceHigh`
- `CadenceLow`
- `CadenceResting`
- `Duration`
- `pace`
- `Power`
- `PowerHigh`
- `PowerLow`
- `Quantize`
- `replacement_prescription`
- `replacement_verb`
- `Text`
- `units`
- `Zone`

### `Cooldown`
- `Cadence`
- `CadenceHigh`
- `CadenceLow`
- `CadenceResting`
- `Duration`
- `EndAtRoadTime`
- `Pace`
- `pace`
- `Power`
- `PowerHigh`
- `PowerLow`
- `replacement_prescription`
- `replacement_verb`
- `units`
- `Zone`

### `SteadyState`
- `Cadence`
- `CadenceHigh`
- `CadenceLow`
- `CadenceResting`
- `Duration`
- `FailThresholdDuration`
- `Forced_Performance_Test`
- `forced_performance_test`
- `NeverFails`
- `OffPower`
- `pace`
- `Power`
- `PowerHigh`
- `PowerLow`
- `ramptest`
- `replacement_prescription`
- `replacement_verb`
- `show_avg`
- `Target`
- `Text`
- `units`
- `Zone`

### `SolidState`
- `Duration`
- `Power`

### `Ramp`
- `Cadence`
- `CadenceResting`
- `Duration`
- `pace`
- `Power`
- `PowerHigh`
- `PowerLow`
- `show_avg`

### `IntervalsT`
- `Cadence`
- `CadenceHigh`
- `CadenceLow`
- `CadenceResting`
- `FlatRoad`
- `OffDuration`
- `OffPower`
- `OnDuration`
- `OnPower`
- `OverUnder`
- `pace`
- `PowerOffHigh`
- `PowerOffLow`
- `PowerOffZone`
- `PowerOnHigh`
- `PowerOnLow`
- `PowerOnZone`
- `Repeat`
- `units`

### `FreeRide`
- `Cadence`
- `CadenceHigh`
- `CadenceLow`
- `Duration`
- `FailThresholdDuration`
- `FlatRoad`
- `ftptest`
- `Power`
- `ramptest`
- `show_avg`

### `Freeride`
- `Duration`
- `FlatRoad`
- `ftptest`

### `MaxEffort`
- `Duration`

### `RestDay`
- No attributes observed in structure index.

### `TextEvent`
- `Duration`
- `message`
- `TimeOffset`
- `timeoffset`

### `textevent`
- `distoffset`
- `duration`
- `message`
- `mssage`
- `textscale`
- `timeoffset`
- `y`

### `TextNotification`
- `duration`
- `font_size`
- `text`
- `timeOffset`
- `x`
- `y`

### `gameplayevent`
- `camera`
- `duration`
- `timeoffset`
- `type`

### `tag`
- `name`

### `test_details`
- `name`
- `paceid`
- `tracking_text_paceid`
- `tracking_text_post`
- `tracking_text_pre`

## 6. Field Semantics Used in Practice
- `Duration`, `OnDuration`, `OffDuration`: seconds.
- `Cadence` fields: RPM target hints.
- `Power`-family fields: fraction of FTP (for example `0.80` means 80% FTP).
- Case variants are present in real files (`Pace` and `pace`, `TimeOffset` and `timeoffset`).

## 7. Ewoc Import Mapping Rules

### Metadata
- Parsed directly:
  - `name`
  - `description`
  - `author`
  - `tags/tag` (both `name` attribute and text-content form)

### Step Mapping
- `Warmup` -> `Step.Warmup`
- `Cooldown` -> `Step.Cooldown`
- `SteadyState` -> `Step.SteadyState`
- `SolidState` -> `Step.SteadyState`
- `Ramp` -> `Step.Ramp`
- `IntervalsT` -> `Step.IntervalsT`
- `FreeRide` -> `Step.FreeRide`
- `Freeride` -> `Step.FreeRide`
- `MaxEffort` -> `Step.FreeRide`

### Power Fallbacks
- `Warmup` / `Cooldown` / `Ramp`:
  - Prefer `PowerLow` + `PowerHigh`
  - Fallback from `Power` when needed
- `SteadyState` / `SolidState`:
  - Prefer `Power`
  - Fallback to average of `PowerLow` and `PowerHigh`
- `IntervalsT`:
  - Prefer `OnPower` / `OffPower`
  - Fallback to averages of `PowerOnLow/PowerOnHigh` and `PowerOffLow/PowerOffHigh`

### Unknown Step Preservation
- Unknown step tags are preserved as `Unknown` when they look executable:
  - contain `Duration` or `OnDuration`
  - tag name starts with an uppercase letter

## 8. Ewoc Export Profile
Workout editor export currently writes a strict subset:
- Root container: `workout_file`
- Metadata written when provided:
  - `author`
  - `name`
  - `description`
  - plus fixed:
    - `sportType = bike`
    - `durationType = time`
- Steps:
  - `SteadyState`
  - `Ramp`
  - `IntervalsT`
- Editor power UI is percentage-based, converted to FTP fractions in output.

## 9. Complete Observed Identifier Lists

### Elements (46)
- `Cooldown`
- `FreeRide`
- `Freeride`
- `IntervalsT`
- `MaxEffort`
- `Ramp`
- `RestDay`
- `ShowCP20`
- `Skippable`
- `SolidState`
- `SteadyState`
- `TextEvent`
- `TextNotification`
- `Tutorial`
- `Warmup`
- `WorkoutPlan`
- `activitySaveName`
- `author`
- `authorIcon`
- `author_alias`
- `category`
- `categoryIndex`
- `description`
- `durationType`
- `entid`
- `ftpFemaleOverride`
- `ftpMaleOverride`
- `ftpOverride`
- `gameplayevent`
- `name`
- `nameImperial`
- `nameMetric`
- `overrideHash`
- `painIndex`
- `setFtpAtPercentage`
- `sportType`
- `subcategory`
- `tag`
- `tags`
- `test_details`
- `textevent`
- `visibleAfterTime`
- `visibleOutsidePlan`
- `workout`
- `workoutLength`
- `workout_file`

### Attributes (57)
- `Cadence`
- `CadenceHigh`
- `CadenceLow`
- `CadenceResting`
- `Duration`
- `EndAtRoadTime`
- `FailThresholdDuration`
- `FlatRoad`
- `Forced_Performance_Test`
- `NeverFails`
- `OffDuration`
- `OffPower`
- `OnDuration`
- `OnPower`
- `OverUnder`
- `Pace`
- `Power`
- `PowerHigh`
- `PowerLow`
- `PowerOffHigh`
- `PowerOffLow`
- `PowerOffZone`
- `PowerOnHigh`
- `PowerOnLow`
- `PowerOnZone`
- `Quantize`
- `Repeat`
- `Target`
- `Text`
- `TimeOffset`
- `Zone`
- `camera`
- `distoffset`
- `duration`
- `font_size`
- `forced_performance_test`
- `ftptest`
- `message`
- `mssage`
- `name`
- `pace`
- `paceid`
- `ramptest`
- `replacement_prescription`
- `replacement_verb`
- `show_avg`
- `text`
- `textscale`
- `timeOffset`
- `timeoffset`
- `tracking_text_paceid`
- `tracking_text_post`
- `tracking_text_pre`
- `type`
- `units`
- `x`
- `y`

## 10. Offline Maintenance
If the external reference changes, refresh this document with:
```bash
wget -qO- https://raw.githubusercontent.com/h4l/zwift-workout-file-reference/master/zwift_workout_file_tag_reference.md > /tmp/zwift_workout_file_tag_reference.md
sed -n '/^## Structure Index/,/^## Elements/p' /tmp/zwift_workout_file_tag_reference.md | rg -o '#element-[A-Za-z0-9_]+' | sed 's/#element-//' | sort -u
sed -n '/^## Structure Index/,/^## Elements/p' /tmp/zwift_workout_file_tag_reference.md | rg -o '#attribute-[A-Za-z0-9_]+' | sed 's/#attribute-//' | sort -u
```

## Current Compatibility Status

### Import Support (Parser)

#### Root-level metadata
- Parsed and used:
  - `<name>`
  - `<description>`
  - `<author>`
  - `<tags><tag .../></tags>` (attribute/text variants)
- Ignored safely:
  - Other optional metadata fields from Zwift built-in files (for example `category`, `sportType`, `durationType`, `workoutLength`, and similar).

#### Workout step tags
- Parsed as first-class step types:
  - `<Warmup>`
  - `<Cooldown>`
  - `<SteadyState>`
  - `<Ramp>`
  - `<IntervalsT>`
  - `<FreeRide>`
- Parsed aliases/fallbacks:
  - `<Freeride>` -> `FreeRide`
  - `<SolidState>` -> `SteadyState`
  - `<MaxEffort>` -> `FreeRide`

#### Attribute compatibility
- Warmup/Cooldown/Ramp:
  - Uses `PowerLow`/`PowerHigh` when present.
  - Falls back from `Power` when low/high are missing.
- SteadyState/SolidState:
  - Uses `Power` directly when present.
  - Falls back to average of `PowerLow` + `PowerHigh` when `Power` is missing.
- IntervalsT:
  - Uses `OnPower`/`OffPower` when present.
  - Falls back to averaged range values from `PowerOnLow`/`PowerOnHigh` and `PowerOffLow`/`PowerOffHigh`.

#### Unknown ZWO step tags
- Unknown step tags are preserved as `Unknown` steps when they look executable
  (for example they include `Duration` or `OnDuration` and have an uppercase step-like name).

### Export Support (Workout Editor)
- Editor export always writes XML content with `.zwo`-compatible structure:
  - Root: `<workout_file>`
  - Metadata: `<author>`, `<name>`, `<description>`, `<sportType>bike</sportType>`, `<durationType>time</durationType>`
  - Steps: `<SteadyState>`, `<Ramp>`, `<IntervalsT>`
- Editor power input is percent-based in UI (for example `80`) and converted to ZWO fraction values on export (`0.80`).

### Known Scope Limits
- The app intentionally does not execute every optional Zwift-specific element/sub-element
  (for example `TextEvent`, `TextNotification`, `gameplayevent`, plan metadata fields).
- Unsupported/unknown executable-looking steps are preserved during parse for forward compatibility,
  but strict execution mapping may reject them for ERG execution.

# Workout Profile Chart Mini-Spec (MVP)

## Goal
Add a clear visual workout profile so users can understand interval structure at a glance before and during a session.

The reference style is a standard "power-over-time" profile:
- X-axis: elapsed time.
- Y-axis: target power (relative to FTP for readability).
- Colored blocks/ramps per step.

## Constraints
- No workout execution behavior changes.
- No refactor required for this increment.
- Reuse existing parsed workout data (`WorkoutFile` + `Step`).
- Keep rendering deterministic and lightweight (Compose `Canvas`, no chart library dependency).

## Non-Goals (MVP)
- Zoom/pan.
- Drag interactions.
- Editable steps.
- New BLE/session protocol behavior.
- Replacing existing textual workout info.

## UX Summary
Show a compact chart that communicates:
- Warmup/cooldown ramps as sloped segments.
- Steady intervals as flat segments.
- Intervals blocks with obvious on/off alternation.
- Approximate effort zones with color coding.

Recommended first placement:
- `MenuScreen`: static preview of selected workout.
- `SessionScreen`: same profile as context panel (static in MVP).

## Proposed UI API
Create a reusable composable:

```kotlin
@Composable
internal fun WorkoutProfileChart(
    workout: WorkoutFile,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
    elapsedSec: Int? = null, // optional, for future live cursor
)
```

Notes:
- `elapsedSec` stays optional; MVP can ignore it.
- `ftpWatts` can use existing app default (`100`) for now.

## Internal Chart Model
Map source steps into render segments:

```kotlin
internal data class WorkoutProfileSegment(
    val startSec: Int,
    val durationSec: Int,
    val startPowerRelFtp: Double?,
    val endPowerRelFtp: Double?,
    val kind: SegmentKind,
)

internal enum class SegmentKind {
    RAMP,
    STEADY,
    FREERIDE,
}
```

Mapping rules from `Step`:
- `Warmup`, `Cooldown`, `Ramp`: `RAMP` (`powerLow -> powerHigh`).
- `SteadyState`: `STEADY` (`power -> power`).
- `IntervalsT`: expand to repeated `STEADY` on/off segments.
- `FreeRide`: `FREERIDE` (null power profile, draw baseline area).
- `Unknown`: skip.

Validation rules:
- Missing/invalid duration (`null` or `<= 0`) -> skip segment.
- Missing power for power-based step -> skip segment.
- Clamp render power to `[0.0, 2.0]` relative FTP for stable chart scale.
- If no valid segments, render "No chart data" fallback text.

## Rendering Spec
Use `Canvas` inside a fixed-height card.

Geometry:
- `totalDurationSec = sum(segment.durationSec)`.
- `x = (timeSec / totalDurationSec) * width`.
- `y = height - ((powerRel / maxRel) * height)`, `maxRel = 2.0`.

Shapes:
- `STEADY`: rectangle.
- `RAMP`: trapezoid from start Y to end Y.
- `FREERIDE`: low-contrast band near baseline.

Grid:
- Horizontal guide lines at `0.5`, `1.0`, `1.5`, `2.0` FTP.
- Right-side labels optional in MVP; can be added in V2.

Color zones (based on average segment power relative FTP):
- `<= 0.55`: gray.
- `<= 0.75`: blue.
- `<= 0.90`: green.
- `<= 1.05`: yellow.
- `<= 1.20`: orange.
- `> 1.20`: red.

Accessibility:
- Add semantics description with total duration and segment count.
- Keep contrast sufficient in light theme.

## Integration Plan (Smallest Safe Sequence)

### Commit 1: Chart component + mapping
- Add `app/src/main/java/com/example/ergometerapp/ui/components/WorkoutProfileChart.kt`.
- Implement step-to-segment mapper in same file (internal scope).
- Add unit tests for mapping edge cases:
  - cooldown ramp direction preserved.
  - intervals expansion duration math.
  - invalid step inputs skipped.

### Commit 2: Wire into screens (read-only)
- Extend `MenuScreen` and `SessionScreen` params with optional `selectedWorkout: WorkoutFile?`.
- Pass `importedWorkoutForRunner` from `MainActivity`.
- Render chart card only when workout exists.
- Keep existing labels/buttons unchanged.

## Acceptance Criteria
- Selecting a valid workout shows a chart preview in `MENU`.
- Entering session shows same workout profile in `SESSION`.
- No changes to runner timing, BLE control flow, or start/pause/resume logic.
- Existing tests still pass.
- `:app:compileDebugKotlin` and `:app:testDebugUnitTest` pass.

## V2 (After MVP)
- Live vertical cursor using runner elapsed seconds.
- Completed/remaining shading.
- Tap-to-tooltip (step name, duration, watts/%FTP).
- Optional dual labels (`%FTP` + watts).

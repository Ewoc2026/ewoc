# HR Workouts V1 Execution Decision

## Decision

For HR Workouts V1, prefer extending the existing execution path instead of
building a separate parallel HR runner stack.

This means the intended runtime path remains:

- import / canonical policy resolution
- execution-model mapping
- `WorkoutStepper`
- `WorkoutRunner`
- `SessionOrchestrator`

The first code change should therefore establish HR-owned segment vocabulary in
the shared execution model before imported-HR mapping is unblocked.

## Why This Is The Better Option

- `SessionOrchestrator` already assumes one runner-creation path.
- Menu/session/summary derived metrics already read from the mapped execution
  workout.
- Profile-chart rendering already uses `ExecutionWorkout`.
- A parallel runner stack would duplicate more orchestration and presentation
  wiring than it would save.

## Scope Guard

This decision does **not** mean “enable HR execution immediately.”

The initial execution-model change should stay intentionally narrow:

- add runner-facing HR steady segment vocabulary
- update derived surfaces so they can represent it safely
- keep imported-HR mapper blocked until the runner/session wiring is ready

## Immediate Consequence

The next implementation slices should proceed in this order:

1. Add `ExecutionSegment.HeartRateSteady`.
2. Make chart/TSS/stepper code handle that segment safely.
3. Replace imported-HR mapper blocking with real mapping only after the runner
   path is ready to honor preflight, fallback, and hard-cap behavior.

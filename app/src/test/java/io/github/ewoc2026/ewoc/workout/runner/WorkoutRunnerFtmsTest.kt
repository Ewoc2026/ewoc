package io.github.ewoc2026.ewoc.workout.runner

import io.github.ewoc2026.ewoc.ftms.RecordingFtmsTargetWriter
import io.github.ewoc2026.ewoc.workout.CadenceTarget
import io.github.ewoc2026.ewoc.workout.ExecutionSegment
import io.github.ewoc2026.ewoc.workout.ExecutionWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRunnerFtmsTest {
    @Test
    fun runnerWritesTargetsAndClearsOnDone() {
        val workout = ExecutionWorkout(
            name = "Steady",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 3,
                    targetWatts = 200,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 3,
        )
        val clock = FakeClock()
        val handler = ImmediateHandler(clock)
        val writer = RecordingFtmsTargetWriter()
        val runner = WorkoutRunner(
            stepper = WorkoutStepper.fromExecutionWorkout(workout),
            targetWriter = writer,
            tickIntervalMs = 1000L,
            nowUptimeMs = { clock.nowMs },
            handler = handler,
        )

        runner.start()
        handler.runCurrent()
        handler.advanceTo(1000L)
        handler.advanceTo(2000L)
        handler.advanceTo(3000L)

        assertEquals(listOf(200, 200, 200, null), writer.writes)
    }

    @Test
    fun runnerPublishesDoneStateBeforeTerminalTargetClear() {
        val workout = ExecutionWorkout(
            name = "Steady",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 1,
                    targetWatts = 200,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 1,
        )
        val clock = FakeClock()
        val handler = ImmediateHandler(clock)
        val events = mutableListOf<String>()
        val runner = WorkoutRunner(
            stepper = WorkoutStepper.fromExecutionWorkout(workout),
            targetWriter = { watts -> events += "writer($watts)" },
            onStateChanged = { state -> events += "state(done=${state.done},target=${state.targetPowerWatts})" },
            tickIntervalMs = 1000L,
            nowUptimeMs = { clock.nowMs },
            handler = handler,
        )

        runner.start()
        handler.runCurrent()
        handler.advanceTo(1000L)

        val terminalStateIndex = events.indexOfLast { it == "state(done=true,target=null)" }
        val terminalClearIndex = events.indexOfLast { it == "writer(null)" }
        assertTrue(terminalStateIndex >= 0)
        assertTrue(terminalClearIndex >= 0)
        assertTrue(terminalStateIndex < terminalClearIndex)
    }

    @Test
    fun runnerDoesNotWriteTargetsWhilePaused() {
        val workout = ExecutionWorkout(
            name = "Steady",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 10,
                    targetWatts = 180,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 10,
        )
        val clock = FakeClock()
        val handler = ImmediateHandler(clock)
        val writer = RecordingFtmsTargetWriter()
        val runner = WorkoutRunner(
            stepper = WorkoutStepper.fromExecutionWorkout(workout),
            targetWriter = writer,
            tickIntervalMs = 1000L,
            nowUptimeMs = { clock.nowMs },
            handler = handler,
        )

        runner.start()
        handler.runCurrent()
        runner.pause()
        handler.advanceTo(5000L)

        assertEquals(listOf(180), writer.writes)

        runner.resume()
        handler.runCurrent()

        assertEquals(listOf(180, 180), writer.writes)
    }

    @Test
    fun runnerDoesNotWriteTargetsAfterStop() {
        val workout = ExecutionWorkout(
            name = "Steady",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 10,
                    targetWatts = 210,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 10,
        )
        val clock = FakeClock()
        val handler = ImmediateHandler(clock)
        val writer = RecordingFtmsTargetWriter()
        val runner = WorkoutRunner(
            stepper = WorkoutStepper.fromExecutionWorkout(workout),
            targetWriter = writer,
            tickIntervalMs = 1000L,
            nowUptimeMs = { clock.nowMs },
            handler = handler,
        )

        runner.start()
        handler.runCurrent()
        runner.stop()
        handler.advanceTo(5000L)

        assertEquals(listOf(210, null), writer.writes)
    }

    @Test
    fun runnerClearsTargetWhenWorkoutTransitionsToFreeRide() {
        val workout = ExecutionWorkout(
            name = "Steady to FreeRide",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 2,
                    targetWatts = 200,
                    cadence = CadenceTarget.AnyCadence,
                ),
                ExecutionSegment.FreeRide(
                    sourceStepIndex = 1,
                    durationSec = 2,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 4,
        )
        val clock = FakeClock()
        val handler = ImmediateHandler(clock)
        val writer = RecordingFtmsTargetWriter()
        val runner = WorkoutRunner(
            stepper = WorkoutStepper.fromExecutionWorkout(workout),
            targetWriter = writer,
            tickIntervalMs = 1000L,
            nowUptimeMs = { clock.nowMs },
            handler = handler,
        )

        runner.start()
        handler.runCurrent()
        handler.advanceTo(1000L)
        handler.advanceTo(2000L)

        assertEquals(listOf(200, 200, null), writer.writes)
    }

    @Test
    fun runnerPublishesElapsedProgressForWorkoutCursor() {
        val workout = ExecutionWorkout(
            name = "Steady",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 3,
                    targetWatts = 200,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 3,
        )
        val clock = FakeClock()
        val handler = ImmediateHandler(clock)
        val writer = RecordingFtmsTargetWriter()
        val states = mutableListOf<RunnerState>()
        val runner = WorkoutRunner(
            stepper = WorkoutStepper.fromExecutionWorkout(workout),
            targetWriter = writer,
            onStateChanged = { states += it },
            tickIntervalMs = 1000L,
            nowUptimeMs = { clock.nowMs },
            handler = handler,
        )

        runner.start()
        handler.runCurrent()
        handler.advanceTo(1000L)
        handler.advanceTo(2000L)
        handler.advanceTo(3000L)

        val elapsedValues = states.mapNotNull { it.workoutElapsedSec }.distinct()
        assertEquals(listOf(0, 1, 2, 3), elapsedValues)
        assertTrue(states.last().done)
        assertEquals(3, states.last().workoutElapsedSec)
    }

    private data class FakeClock(
        var nowMs: Long = 0L,
    )

    private class ImmediateHandler(
        private val clock: FakeClock,
    ) : android.os.Handler(android.os.Looper.getMainLooper()) {
        private data class ScheduledRunnable(
            val runnable: Runnable,
            val runAtMs: Long,
            val order: Long,
        )

        private val queue = mutableListOf<ScheduledRunnable>()
        private var nextOrder = 0L

        override fun post(runnable: Runnable): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = clock.nowMs,
                order = nextOrder++,
            )
            return true
        }

        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = clock.nowMs + delayMillis.coerceAtLeast(0L),
                order = nextOrder++,
            )
            return true
        }

        override fun removeCallbacks(runnable: Runnable) {
            queue.removeAll { it.runnable === runnable }
        }

        fun runCurrent() {
            advanceTo(clock.nowMs)
        }

        fun advanceTo(targetUptimeMs: Long) {
            require(targetUptimeMs >= clock.nowMs) {
                "Time cannot move backwards in the manual handler."
            }
            while (true) {
                val next = queue
                    .filter { it.runAtMs <= targetUptimeMs }
                    .minWithOrNull(compareBy<ScheduledRunnable> { it.runAtMs }.thenBy { it.order })
                    ?: break
                queue.remove(next)
                clock.nowMs = next.runAtMs
                next.runnable.run()
            }
            clock.nowMs = targetUptimeMs
        }
    }
}

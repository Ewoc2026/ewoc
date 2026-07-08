package io.github.ewoc2026.ewoc.workout.runner

import io.github.ewoc2026.ewoc.ftms.RecordingFtmsTargetWriter
import io.github.ewoc2026.ewoc.workout.CadenceTarget
import io.github.ewoc2026.ewoc.workout.ExecutionSegment
import io.github.ewoc2026.ewoc.workout.ExecutionWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRunnerStateMachineTest {

    @Test
    fun startEmitsRunningState() {
        val (runner, handler, states) = buildRunner(steadyWorkout(5, 200))

        runner.start()
        handler.runCurrent()

        assertTrue(states.any { it.running && !it.paused && !it.done })
    }

    @Test
    fun pauseEmitsPausedState() {
        val (runner, handler, states) = buildRunner(steadyWorkout(10, 200))

        runner.start()
        handler.runCurrent()
        runner.pause()

        val lastState = states.last()
        assertTrue(lastState.running)
        assertTrue(lastState.paused)
        assertFalse(lastState.done)
    }

    @Test
    fun resumeEmitsUnpausedState() {
        val (runner, handler, states) = buildRunner(steadyWorkout(10, 200))

        runner.start()
        handler.runCurrent()
        runner.pause()
        runner.resume()
        handler.runCurrent()

        val lastState = states.last()
        assertTrue(lastState.running)
        assertFalse(lastState.paused)
    }

    @Test
    fun stopEmitsDoneState() {
        val (runner, handler, states) = buildRunner(steadyWorkout(10, 200))

        runner.start()
        handler.runCurrent()
        runner.stop()

        val lastState = states.last()
        assertFalse(lastState.running)
        assertTrue(lastState.done)
    }

    @Test
    fun pauseDoesNotAdvanceElapsed() {
        val (runner, handler, states) = buildRunner(steadyWorkout(20, 200))

        runner.start()
        handler.runCurrent()
        handler.advanceTo(2000L)
        val elapsedBeforePause = states.last().workoutElapsedSec

        runner.pause()
        handler.advanceTo(12000L)

        runner.resume()
        handler.runCurrent()
        val elapsedAfterResume = states.last().workoutElapsedSec

        assertEquals(elapsedBeforePause, elapsedAfterResume)
    }

    @Test
    fun multipleResumesWithoutPauseIgnored() {
        val (runner, handler, states) = buildRunner(steadyWorkout(10, 200))

        runner.start()
        handler.runCurrent()
        val stateCountAfterStart = states.size

        runner.resume()
        runner.resume()

        assertEquals(stateCountAfterStart, states.size)
    }

    @Test
    fun pauseOnStoppedRunnerIgnored() {
        val (runner, handler, states) = buildRunner(steadyWorkout(10, 200))

        runner.start()
        handler.runCurrent()
        runner.stop()
        val stateCountAfterStop = states.size

        runner.pause()

        assertEquals(stateCountAfterStop, states.size)
    }

    @Test
    fun restoreResumesWorkoutFromSavedPosition() {
        val workout = steadyWorkout(10, 200)
        val (runner1, handler1, _) = buildRunner(workout)

        runner1.start()
        handler1.runCurrent()
        handler1.advanceTo(3000L)
        val savedState = runner1.getState()

        val (runner2, handler2, states2, writer2) = buildRunner(workout)
        runner2.restore(savedState)

        val restoreState = states2.last()
        assertFalse(restoreState.running)

        runner2.start()
        handler2.runCurrent()
        assertTrue(writer2.writes.isNotEmpty())
    }

    @Test
    fun naturalCompletionClearsTarget() {
        val (runner, handler, states, writer) = buildRunner(steadyWorkout(2, 200))

        runner.start()
        handler.runCurrent()
        handler.advanceTo(1000L)
        handler.advanceTo(2000L)

        assertTrue(states.last().done)
        assertEquals(null, writer.writes.last())
    }

    @Test
    fun noTicksAfterCompletion() {
        val (runner, handler, _, writer) = buildRunner(steadyWorkout(2, 200))

        runner.start()
        handler.runCurrent()
        handler.advanceTo(1000L)
        handler.advanceTo(2000L)
        val writesAtCompletion = writer.writes.size

        handler.advanceTo(5000L)

        assertEquals(writesAtCompletion, writer.writes.size)
    }

    @Test
    fun targetPowerChangesAcrossSegments() {
        val workout = ExecutionWorkout(
            name = "TwoPhase",
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
                ExecutionSegment.Steady(
                    sourceStepIndex = 1,
                    durationSec = 2,
                    targetWatts = 150,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 4,
        )
        val (runner, handler, _, writer) = buildRunner(workout)

        runner.start()
        handler.runCurrent()
        handler.advanceTo(1000L)
        handler.advanceTo(2000L)
        handler.advanceTo(3000L)

        assertTrue(writer.writes.contains(200))
        assertTrue(writer.writes.contains(150))
    }

    private data class RunnerHarness(
        val runner: WorkoutRunner,
        val handler: ImmediateHandler,
        val states: MutableList<RunnerState>,
        val writer: RecordingFtmsTargetWriter,
    )

    private fun buildRunner(workout: ExecutionWorkout): RunnerHarness {
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
        return RunnerHarness(runner, handler, states, writer)
    }

    private fun steadyWorkout(durationSec: Int, watts: Int): ExecutionWorkout {
        return ExecutionWorkout(
            name = "Steady",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = durationSec,
                    targetWatts = watts,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = durationSec,
        )
    }

    private data class FakeClock(var nowMs: Long = 0L)

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

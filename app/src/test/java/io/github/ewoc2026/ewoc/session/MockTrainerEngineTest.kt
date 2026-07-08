package io.github.ewoc2026.ewoc.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockTrainerEngineTest {

    @Test
    fun targetPowerUpdatesDriveTelemetryPowerProgression() {
        val clock = FakeClock()
        val handler = ManualHandler(clock)
        val samples = mutableListOf<io.github.ewoc2026.ewoc.ftms.IndoorBikeData>()
        val engine = MockTrainerEngine(
            tickIntervalMs = 1_000L,
            handler = handler,
            nowElapsedMs = { clock.nowMs },
        )

        engine.start { samples += it }
        handler.advanceBy(0L)
        val baselinePower = requireNotNull(samples.last().instantaneousPowerW)

        engine.setTargetPowerWatts(300)
        handler.advanceBy(1_000L)
        handler.advanceBy(1_000L)
        handler.advanceBy(1_000L)

        val latest = samples.last()
        val latestPower = requireNotNull(latest.instantaneousPowerW)
        assertTrue(latestPower > baselinePower)
        assertEquals(3, latest.elapsedTimeSeconds)
        assertNotNull(latest.instantaneousCadenceRpm)
        assertNotNull(latest.instantaneousSpeedKmh)
    }

    @Test
    fun stopCancelsCallbacksAndRestartResetsElapsed() {
        val clock = FakeClock()
        val handler = ManualHandler(clock)
        val samples = mutableListOf<io.github.ewoc2026.ewoc.ftms.IndoorBikeData>()
        val engine = MockTrainerEngine(
            tickIntervalMs = 1_000L,
            handler = handler,
            nowElapsedMs = { clock.nowMs },
        )

        engine.start { samples += it }
        handler.advanceBy(2_000L)
        val countBeforeStop = samples.size

        engine.stop()
        handler.advanceBy(5_000L)
        assertEquals(countBeforeStop, samples.size)

        val restartedSamples = mutableListOf<io.github.ewoc2026.ewoc.ftms.IndoorBikeData>()
        engine.start { restartedSamples += it }
        handler.advanceBy(0L)
        assertEquals(0, restartedSamples.last().elapsedTimeSeconds)
    }

    @Test
    fun waitingStartAndPauseScenarioOverridesCadenceDuringCaptureWindows() {
        val clock = FakeClock()
        val handler = ManualHandler(clock)
        val samples = mutableListOf<io.github.ewoc2026.ewoc.ftms.IndoorBikeData>()
        val engine = MockTrainerEngine(
            tickIntervalMs = 1_000L,
            handler = handler,
            nowElapsedMs = { clock.nowMs },
        )
        engine.armDebugScenario(MockTrainerDebugScenario.WAITING_START_AND_PAUSE_CAPTURE)

        engine.start { samples += it }
        handler.advanceBy(0L)
        assertEquals(0.0, requireNotNull(samples.last().instantaneousCadenceRpm), 0.0)
        assertEquals(0, samples.last().instantaneousPowerW)
        assertEquals(0.0, requireNotNull(samples.last().instantaneousSpeedKmh), 0.0)

        handler.advanceBy(8_000L)
        assertTrue(requireNotNull(samples.last().instantaneousCadenceRpm) > 0.0)
        assertTrue(requireNotNull(samples.last().instantaneousSpeedKmh) > 0.0)
        assertTrue(requireNotNull(samples.last().instantaneousPowerW) > 0)

        handler.advanceBy(8_000L)
        assertEquals(0.0, requireNotNull(samples.last().instantaneousCadenceRpm), 0.0)
        assertEquals(0, samples.last().instantaneousPowerW)
        assertEquals(0.0, requireNotNull(samples.last().instantaneousSpeedKmh), 0.0)

        handler.advanceBy(5_000L)
        assertTrue(requireNotNull(samples.last().instantaneousCadenceRpm) > 0.0)
        assertFalse(requireNotNull(samples.last().instantaneousSpeedKmh) <= 0.0)
    }

    private data class FakeClock(
        var nowMs: Long = 0L,
    )

    private class ManualHandler(
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

        fun advanceBy(deltaMs: Long) {
            require(deltaMs >= 0L) { "Delta must be non-negative." }
            val targetMs = clock.nowMs + deltaMs
            while (true) {
                val next = queue
                    .filter { it.runAtMs <= targetMs }
                    .minWithOrNull(compareBy<ScheduledRunnable> { it.runAtMs }.thenBy { it.order })
                    ?: break
                queue.remove(next)
                clock.nowMs = next.runAtMs
                next.runnable.run()
            }
            clock.nowMs = targetMs
        }
    }
}

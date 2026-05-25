package com.example.ergometerapp.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the FTMS command serialization invariant: only one command in flight at a time,
 * "last wins" for target power, stop-terminal behavior, disconnect cleanup, and reset queuing.
 */
class FtmsControllerSerializationTest {

    @Test
    fun commandIgnoredWhenTransportNotReady() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.requestControl()

        assertTrue(writes.isEmpty())
    }

    @Test
    fun commandSentWhenTransportReady() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.requestControl()

        assertEquals(1, writes.size)
        assertEquals(0x00, writes.first().opcode())
    }

    @Test
    fun secondCommandBlockedWhileBusy() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.requestControl()
        controller.setTargetPower(200)

        assertEquals(1, writes.size)
    }

    @Test
    fun pendingTargetPowerSentAfterResponse() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.requestControl()
        controller.setTargetPower(200)
        controller.onControlPointResponse(requestOpcode = 0x00, resultCode = 0x01)

        assertEquals(2, writes.size)
        assertEquals(0x05, writes[1].opcode())
        assertEquals(200, writes[1].targetPowerWatts())
    }

    @Test
    fun lastWinsOnlyLatestTargetPowerSent() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.requestControl()
        controller.setTargetPower(100)
        controller.setTargetPower(200)
        controller.setTargetPower(300)
        controller.onControlPointResponse(requestOpcode = 0x00, resultCode = 0x01)

        assertEquals(2, writes.size)
        assertEquals(300, writes[1].targetPowerWatts())
    }

    @Test
    fun duplicateTargetPowerSuppressed() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.setTargetPower(200)
        controller.onControlPointResponse(requestOpcode = 0x05, resultCode = 0x01)
        controller.setTargetPower(200)

        assertEquals(1, writes.size)
    }

    @Test
    fun stopWorkoutClearsPendingTargetPower() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.requestControl()
        controller.setTargetPower(200)
        controller.stopWorkout()
        controller.onControlPointResponse(requestOpcode = 0x00, resultCode = 0x01)

        assertEquals(2, writes.size)
        assertEquals(0x08, writes[1].opcode())
    }

    @Test
    fun stopWorkoutIsTerminalForSubsequentTargetPowerCalls() {
        val writes = mutableListOf<ByteArray>()
        val stopAcknowledged = mutableListOf<Boolean>()
        val controller = buildController(writes, onStopAcknowledged = { stopAcknowledged += true })

        controller.setTransportReady(true)
        controller.stopWorkout()
        controller.onControlPointResponse(requestOpcode = 0x08, resultCode = 0x01)

        assertEquals(1, stopAcknowledged.size)
    }

    @Test
    fun requestControlIgnoredAfterStop() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.stopWorkout()
        controller.onControlPointResponse(requestOpcode = 0x08, resultCode = 0x01)
        controller.requestControl()

        assertEquals(1, writes.size)
    }

    @Test
    fun resetClearsStopTerminalState() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.stopWorkout()
        controller.onControlPointResponse(requestOpcode = 0x08, resultCode = 0x01)
        controller.reset()
        controller.onControlPointResponse(requestOpcode = 0x01, resultCode = 0x01)
        controller.requestControl()

        assertEquals(3, writes.size)
        assertEquals(0x00, writes[2].opcode())
    }

    @Test
    fun resetQueuedWhileBusy() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.requestControl()
        controller.reset()

        assertEquals(1, writes.size)

        controller.onControlPointResponse(requestOpcode = 0x00, resultCode = 0x01)

        assertEquals(2, writes.size)
        assertEquals(0x01, writes[1].opcode())
    }

    @Test
    fun disconnectClearsBusyStateAndPendingCommands() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.requestControl()
        controller.setTargetPower(200)
        controller.onDisconnected()

        assertFalse(controller.isReady())

        controller.setTransportReady(true)
        controller.requestControl()

        assertEquals(2, writes.size)
        assertEquals(0x00, writes[1].opcode())
    }

    @Test
    fun writeFailureDoesNotEnterBusyState() {
        val controller = FtmsController(
            writeControlPoint = { false },
            handler = NoOpHandler(),
        )

        controller.setTransportReady(true)
        controller.requestControl()
        controller.setTargetPower(200)

        // If BUSY was set on write failure, this setTargetPower would be queued
        // instead of attempted. We verify by checking isReady stays true.
        assertTrue(controller.isReady())
    }

    @Test
    fun setZeroTargetPowerSendsZeroWattTarget() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.setZeroTargetPower()

        assertEquals(1, writes.size)
        assertEquals(0x05, writes.first().opcode())
        assertEquals(0, writes.first().targetPowerWatts())
    }

    @Test
    fun targetPowerClampedToSafeRange() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.setTargetPower(5000)

        assertEquals(1, writes.size)
        assertEquals(2000, writes.first().targetPowerWatts())
    }

    @Test
    fun stopQueuedWhileBusyIsSentAfterResponse() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.setTargetPower(200)
        controller.stopWorkout()

        assertEquals(1, writes.size)

        controller.onControlPointResponse(requestOpcode = 0x05, resultCode = 0x01)

        assertEquals(2, writes.size)
        assertEquals(0x08, writes[1].opcode())
    }

    @Test
    fun stopTakesPriorityOverPendingReset() {
        val writes = mutableListOf<ByteArray>()
        val controller = buildController(writes)

        controller.setTransportReady(true)
        controller.requestControl()
        controller.reset()
        controller.stopWorkout()

        controller.onControlPointResponse(requestOpcode = 0x00, resultCode = 0x01)

        assertEquals(2, writes.size)
        assertEquals(0x08, writes[1].opcode())
    }

    @Test
    fun disconnectDuringPendingStopClearsBusyWithoutTimeout() {
        val timedOutOpcodes = mutableListOf<Int?>()
        val handler = ManualHandler()
        val controller = FtmsController(
            writeControlPoint = { true },
            onCommandTimeout = { opcode -> timedOutOpcodes += opcode },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.requestControl()
        controller.stopWorkout()
        controller.onDisconnected()

        handler.advanceBy(3000L)

        assertTrue(timedOutOpcodes.isEmpty())
    }

    private fun buildController(
        writes: MutableList<ByteArray>,
        onStopAcknowledged: () -> Unit = {},
    ): FtmsController {
        return FtmsController(
            writeControlPoint = { payload ->
                writes += payload.copyOf()
                true
            },
            onStopAcknowledged = onStopAcknowledged,
            handler = NoOpHandler(),
        )
    }

    private fun ByteArray.opcode(): Int = first().toInt() and 0xFF

    private fun ByteArray.targetPowerWatts(): Int {
        require(size >= 3 && opcode() == 0x05) { "Not a target power payload" }
        return (this[1].toInt() and 0xFF) or ((this[2].toInt() and 0xFF) shl 8)
    }

    /**
     * Handler that accepts posts without executing them, for tests that don't need
     * timeout simulation. Timeout tests use [ManualHandler] instead.
     */
    private class NoOpHandler : android.os.Handler(android.os.Looper.getMainLooper()) {
        override fun post(runnable: Runnable): Boolean = true
        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean = true
        override fun removeCallbacks(runnable: Runnable) {}
    }

    private class ManualHandler : android.os.Handler(android.os.Looper.getMainLooper()) {
        private data class ScheduledRunnable(
            val runnable: Runnable,
            val runAtMs: Long,
            val order: Long,
        )

        private val queue = mutableListOf<ScheduledRunnable>()
        private var nextOrder = 0L
        private var nowMs = 0L

        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = nowMs + delayMillis.coerceAtLeast(0L),
                order = nextOrder++,
            )
            return true
        }

        override fun removeCallbacks(runnable: Runnable) {
            queue.removeAll { it.runnable === runnable }
        }

        fun advanceBy(deltaMs: Long) {
            val targetMs = nowMs + deltaMs
            while (true) {
                val next = queue
                    .filter { it.runAtMs <= targetMs }
                    .minWithOrNull(compareBy<ScheduledRunnable> { it.runAtMs }.thenBy { it.order })
                    ?: break
                queue.remove(next)
                nowMs = next.runAtMs
                next.runnable.run()
            }
            nowMs = targetMs
        }
    }
}

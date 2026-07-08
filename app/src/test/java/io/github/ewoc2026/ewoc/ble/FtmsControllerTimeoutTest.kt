package io.github.ewoc2026.ewoc.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtmsControllerTimeoutTest {
    @Test
    fun requestControlTimeoutReportsRequestOpcode() {
        val timedOutOpcodes = mutableListOf<Int?>()
        val handler = ManualHandler()
        val controller = FtmsController(
            writeControlPoint = { true },
            onCommandTimeout = { opcode -> timedOutOpcodes += opcode },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.requestControl()
        assertTrue(timedOutOpcodes.isEmpty())
        handler.advanceBy(2500L)

        assertEquals(listOf(0x00), timedOutOpcodes)
    }

    @Test
    fun requestControlRejectedResponseResolvesWithoutTimeout() {
        val timedOutOpcodes = mutableListOf<Int?>()
        val handler = ManualHandler()
        val controller = FtmsController(
            writeControlPoint = { true },
            onCommandTimeout = { opcode -> timedOutOpcodes += opcode },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.requestControl()
        val matched = controller.onControlPointResponse(
            requestOpcode = 0x00,
            resultCode = 0x05,
        )

        assertTrue(matched)
        handler.advanceBy(2500L)
        assertTrue(timedOutOpcodes.isEmpty())
    }

    @Test
    fun commandLifecycleReportsStableCorrelationFromSendToTimeout() {
        val handler = ManualHandler()
        val lifecycleEvents = mutableListOf<FtmsCommandLifecycleEvent>()
        val controller = FtmsController(
            writeControlPoint = { true },
            onCommandLifecycleEvent = { event -> lifecycleEvents += event },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.requestControl()
        handler.advanceBy(2500L)

        val sent = lifecycleEvents.single { it.stage == FtmsCommandLifecycleStage.SENT }
        val timeout = lifecycleEvents.single { it.stage == FtmsCommandLifecycleStage.TIMEOUT }

        assertNotNull(sent.correlationId)
        assertEquals(sent.correlationId, timeout.correlationId)
        assertEquals(0x00, sent.requestOpcode)
        assertEquals(0x00, timeout.requestOpcode)
    }

    @Test
    fun commandLifecycleReportsStableCorrelationFromSendToResponse() {
        val handler = ManualHandler()
        val lifecycleEvents = mutableListOf<FtmsCommandLifecycleEvent>()
        val controller = FtmsController(
            writeControlPoint = { true },
            onCommandLifecycleEvent = { event -> lifecycleEvents += event },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.requestControl()
        val matched = controller.onControlPointResponse(requestOpcode = 0x00, resultCode = 0x01)
        assertTrue(matched)

        val sent = lifecycleEvents.single { it.stage == FtmsCommandLifecycleStage.SENT }
        val response = lifecycleEvents.single { it.stage == FtmsCommandLifecycleStage.RESPONSE }

        assertNotNull(sent.correlationId)
        assertEquals(sent.correlationId, response.correlationId)
        assertEquals(0x00, response.requestOpcode)
        assertEquals(0x01, response.resultCode)
    }

    @Test
    fun writeStartFailureDoesNotTriggerTimeoutCallback() {
        val timedOutOpcodes = mutableListOf<Int?>()
        val handler = ManualHandler()
        val controller = FtmsController(
            writeControlPoint = { false },
            onCommandTimeout = { opcode -> timedOutOpcodes += opcode },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.requestControl()
        handler.advanceBy(3000L)

        assertTrue(timedOutOpcodes.isEmpty())
    }

    @Test
    fun mismatchedResponseDoesNotReleaseBusyAndTimesOutExpectedCommand() {
        val writes = mutableListOf<ByteArray>()
        val timedOutOpcodes = mutableListOf<Int?>()
        val unexpectedResponses = mutableListOf<UnexpectedResponse>()
        val handler = ManualHandler()
        val controller = FtmsController(
            writeControlPoint = { payload ->
                writes += payload.copyOf()
                true
            },
            onCommandTimeout = { opcode -> timedOutOpcodes += opcode },
            onUnexpectedControlPointResponse = { expectedOpcode, receivedOpcode, resultCode, reason ->
                unexpectedResponses += UnexpectedResponse(
                    expectedOpcode = expectedOpcode,
                    receivedOpcode = receivedOpcode,
                    resultCode = resultCode,
                    reason = reason,
                )
            },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.requestControl()
        assertEquals(1, writes.size)
        assertEquals(0x00, writes.first().first().toInt() and 0xFF)

        val matched = controller.onControlPointResponse(requestOpcode = 0x05, resultCode = 0x01)
        assertFalse(matched)
        assertEquals(1, unexpectedResponses.size)
        assertEquals(
            FtmsUnexpectedControlPointResponseReason.OPCODE_MISMATCH,
            unexpectedResponses.first().reason,
        )
        assertEquals(0x00, unexpectedResponses.first().expectedOpcode)

        controller.setTargetPower(250)
        assertEquals(1, writes.size)

        handler.advanceBy(2500L)
        assertEquals(listOf(0x00), timedOutOpcodes)
        assertEquals(2, writes.size)
        assertEquals(0x05, writes[1].first().toInt() and 0xFF)
    }

    @Test
    fun staleLinkGenerationResponseDoesNotReleaseBusyAndTimesOutExpectedCommand() {
        val writes = mutableListOf<ByteArray>()
        val timedOutOpcodes = mutableListOf<Int?>()
        val unexpectedResponses = mutableListOf<UnexpectedResponse>()
        val lifecycleEvents = mutableListOf<FtmsCommandLifecycleEvent>()
        val handler = ManualHandler()
        val controller = FtmsController(
            writeControlPoint = { payload ->
                writes += payload.copyOf()
                true
            },
            onCommandTimeout = { opcode -> timedOutOpcodes += opcode },
            onUnexpectedControlPointResponse = { expectedOpcode, receivedOpcode, resultCode, reason ->
                unexpectedResponses += UnexpectedResponse(
                    expectedOpcode = expectedOpcode,
                    receivedOpcode = receivedOpcode,
                    resultCode = resultCode,
                    reason = reason,
                )
            },
            onCommandLifecycleEvent = { event -> lifecycleEvents += event },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.onControlLinkGenerationChanged(generation = 7)
        controller.requestControl()
        assertEquals(1, writes.size)

        val matched = controller.onControlPointResponse(
            requestOpcode = 0x00,
            resultCode = 0x01,
            responseLinkGeneration = 6,
        )
        assertFalse(matched)
        assertEquals(1, unexpectedResponses.size)
        assertEquals(
            FtmsUnexpectedControlPointResponseReason.STALE_LINK_GENERATION,
            unexpectedResponses.first().reason,
        )
        assertEquals(0x00, unexpectedResponses.first().expectedOpcode)

        controller.setTargetPower(250)
        assertEquals(1, writes.size)

        handler.advanceBy(2500L)
        assertEquals(listOf(0x00), timedOutOpcodes)
        assertEquals(2, writes.size)
        assertEquals(0x05, writes[1].first().toInt() and 0xFF)
        assertEquals(
            FtmsUnexpectedControlPointResponseReason.STALE_LINK_GENERATION,
            lifecycleEvents.last { it.stage == FtmsCommandLifecycleStage.UNEXPECTED_RESPONSE }
                .unexpectedReason,
        )
    }

    @Test
    fun responseWithoutInFlightSignalsUnexpected() {
        val unexpectedResponses = mutableListOf<UnexpectedResponse>()
        val lifecycleEvents = mutableListOf<FtmsCommandLifecycleEvent>()
        val controller = FtmsController(
            writeControlPoint = { true },
            onCommandLifecycleEvent = { event -> lifecycleEvents += event },
            onUnexpectedControlPointResponse = { expectedOpcode, receivedOpcode, resultCode, reason ->
                unexpectedResponses += UnexpectedResponse(
                    expectedOpcode = expectedOpcode,
                    receivedOpcode = receivedOpcode,
                    resultCode = resultCode,
                    reason = reason,
                )
            },
        )

        controller.setTransportReady(true)
        val matched = controller.onControlPointResponse(requestOpcode = 0x00, resultCode = 0x01)
        assertFalse(matched)

        assertEquals(1, unexpectedResponses.size)
        assertEquals(
            FtmsUnexpectedControlPointResponseReason.NO_COMMAND_IN_FLIGHT,
            unexpectedResponses.first().reason,
        )
        assertNull(unexpectedResponses.first().expectedOpcode)
        assertEquals(1, lifecycleEvents.size)
        assertEquals(FtmsCommandLifecycleStage.UNEXPECTED_RESPONSE, lifecycleEvents.first().stage)
        assertNull(lifecycleEvents.first().correlationId)
    }

    private data class UnexpectedResponse(
        val expectedOpcode: Int?,
        val receivedOpcode: Int,
        val resultCode: Int,
        val reason: FtmsUnexpectedControlPointResponseReason,
    )

    private class ManualHandler : android.os.Handler(android.os.Looper.getMainLooper()) {
        private data class ScheduledRunnable(
            val runnable: Runnable,
            val runAtMs: Long,
            val order: Long,
        )

        private val queue = mutableListOf<ScheduledRunnable>()
        private var nextOrder = 0L
        private var nowMs = 0L

        override fun post(runnable: Runnable): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = nowMs,
                order = nextOrder++,
            )
            return true
        }

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
            require(deltaMs >= 0L) { "Delta must be non-negative." }
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

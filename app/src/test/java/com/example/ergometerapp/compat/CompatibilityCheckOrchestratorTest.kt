package com.example.ergometerapp.compat

import com.example.ergometerapp.compat.quirks.CompatibilityQuirks
import com.example.ergometerapp.compat.quirks.MatchConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class CompatibilityCheckOrchestratorTest {

    @Test
    fun run_retriesConnectWithinBoundAndPassesWhenSecondAttemptSucceeds() {
        val clock = FakeClock(now = 1_000L)
        val gateway = FakeGateway(clock)
        gateway.connectScript += ScriptedCommand(
            result = CompatibilityCommandResult(CompatibilityCommandStatus.TIMEOUT),
            durationMs = 200L,
        )
        gateway.connectScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 200L,
        )

        val orchestrator = CompatibilityCheckOrchestrator(
            deviceGateway = gateway,
            nowEpochMs = { clock.now },
        )

        val result = orchestrator.run(defaultQuirks(enableResetOptional = false))

        assertEquals(CompatibilitySummaryStatus.PASS, result.summary.status)
        assertEquals(2, gateway.calls.count { it.startsWith("connect(") })
    }

    @Test
    fun run_retriesRequestControlForWriteNotStartedThenPasses() {
        val clock = FakeClock(now = 10_000L)
        val gateway = FakeGateway(clock)
        gateway.requestControlScript += ScriptedCommand(
            result = CompatibilityCommandResult(
                status = CompatibilityCommandStatus.WRITE_NOT_STARTED,
                controlPointTrace = CompatibilityControlPointTrace(
                    expectedOpcode = 0x00,
                    stage = "write_not_started",
                ),
            ),
            durationMs = 50L,
        )
        gateway.requestControlScript += ScriptedCommand(
            result = CompatibilityCommandResult(
                status = CompatibilityCommandStatus.SUCCESS,
                controlPointTrace = CompatibilityControlPointTrace(
                    correlationId = "cp-1",
                    requestOpcode = 0x00,
                    expectedOpcode = 0x00,
                    receivedOpcode = 0x00,
                    resultCode = 0x01,
                    stage = "response",
                ),
            ),
            durationMs = 50L,
        )

        val orchestrator = CompatibilityCheckOrchestrator(
            deviceGateway = gateway,
            nowEpochMs = { clock.now },
        )

        val result = orchestrator.run(defaultQuirks(enableResetOptional = false))

        assertEquals(CompatibilitySummaryStatus.PASS, result.summary.status)
        assertEquals(2, gateway.calls.count { it.startsWith("requestControl(") })
        val completedEvents = result.timeline.filter { it.event == "request_control_attempt_completed" }
        assertEquals("0x00", completedEvents.first().details["expectedOpcode"])
        assertEquals("write_not_started", completedEvents.first().details["controlPointStage"])
        assertEquals("cp-1", completedEvents.last().details["correlationId"])
        assertEquals("0x01", completedEvents.last().details["resultCode"])
    }

    @Test
    fun run_doesNotRetryRequestControlReject_andMapsFailureClassification() {
        val clock = FakeClock(now = 20_000L)
        val gateway = FakeGateway(clock)
        gateway.requestControlScript += ScriptedCommand(
            result = CompatibilityCommandResult(CompatibilityCommandStatus.REJECTED, detail = "opcode rejected"),
            durationMs = 40L,
        )

        val orchestrator = CompatibilityCheckOrchestrator(
            deviceGateway = gateway,
            nowEpochMs = { clock.now },
        )

        val result = orchestrator.run(defaultQuirks())

        assertEquals(CompatibilitySummaryStatus.FAIL, result.summary.status)
        assertEquals(CompatibilityFailureCode.REQUEST_CONTROL_REJECTED, result.summary.failureCode)
        assertEquals(CompatibilityFailureCategory.CONTROL_POINT, result.summary.failureCategory)
        assertEquals("request_control_rejected", result.summary.failureReasonKey)
        assertEquals(1, gateway.calls.count { it.startsWith("requestControl(") })
    }

    @Test
    fun run_reacquiresControlAfterOptionalResetBeforePowerSteps() {
        val clock = FakeClock(now = 30_000L)
        val gateway = FakeGateway(clock)
        gateway.requestControlScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 40L,
        )
        gateway.resetScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 40L,
        )
        gateway.requestControlScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 40L,
        )

        val orchestrator = CompatibilityCheckOrchestrator(
            deviceGateway = gateway,
            nowEpochMs = { clock.now },
        )

        val result = orchestrator.run(defaultQuirks())

        assertEquals(CompatibilitySummaryStatus.PASS, result.summary.status)
        assertEquals(2, gateway.calls.count { it.startsWith("requestControl(") })
        assertTrue(
            gateway.calls.indexOfFirst { it == "reset(timeout=2500)" } <
                gateway.calls.indexOfLast { it == "requestControl(timeout=2500)" },
        )
    }

    @Test
    fun run_switchesToForcedDisconnectWhenDeadlineExceededDuringCleanup() {
        val clock = FakeClock(now = 0L)
        val gateway = FakeGateway(clock)

        gateway.connectScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 10_000L,
        )
        gateway.requestControlScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 8_000L,
        )
        gateway.resetScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 1_000L,
        )
        gateway.setTargetPowerScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 1_000L,
        )
        gateway.setTargetPowerScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 1_000L,
        )
        gateway.setTargetPowerScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 1_000L,
        )
        gateway.stopScript += ScriptedCommand(
            result = CompatibilityCommandResult(CompatibilityCommandStatus.TIMEOUT, detail = "stop timeout"),
            durationMs = 7_000L,
        )
        gateway.disconnectScript += ScriptedCommand(
            result = CompatibilityCommandResult.Success,
            durationMs = 0L,
        )

        val orchestrator = CompatibilityCheckOrchestrator(
            deviceGateway = gateway,
            nowEpochMs = { clock.now },
        )

        val result = orchestrator.run(defaultQuirks())

        assertEquals(CompatibilitySummaryStatus.FAIL, result.summary.status)
        assertEquals(CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED, result.summary.failureCode)
        assertEquals(CompatibilityFailureCategory.DEADLINE, result.summary.failureCategory)
        assertTrue(gateway.calls.any { it == "disconnect(timeout=0)" })
    }

    private fun defaultQuirks(enableResetOptional: Boolean = true): CompatibilityQuirks {
        return CompatibilityQuirks(
            id = "default",
            matchConfidence = MatchConfidence.HIGH,
            notes = "Default profile",
            enableResetOptional = enableResetOptional,
        )
    }

    private data class FakeClock(var now: Long)

    private data class ScriptedCommand(
        val result: CompatibilityCommandResult,
        val durationMs: Long,
    )

    private class FakeGateway(
        private val clock: FakeClock,
    ) : CompatibilityDeviceGateway {
        val calls = mutableListOf<String>()

        val connectScript = ArrayDeque<ScriptedCommand>()
        val requestControlScript = ArrayDeque<ScriptedCommand>()
        val resetScript = ArrayDeque<ScriptedCommand>()
        val setTargetPowerScript = ArrayDeque<ScriptedCommand>()
        val stopScript = ArrayDeque<ScriptedCommand>()
        val disconnectScript = ArrayDeque<ScriptedCommand>()

        override fun connect(timeoutMs: Long): CompatibilityCommandResult {
            calls += "connect(timeout=$timeoutMs)"
            return runScript(connectScript)
        }

        override fun requestControl(timeoutMs: Long): CompatibilityCommandResult {
            calls += "requestControl(timeout=$timeoutMs)"
            return runScript(requestControlScript)
        }

        override fun reset(timeoutMs: Long): CompatibilityCommandResult {
            calls += "reset(timeout=$timeoutMs)"
            return runScript(resetScript)
        }

        override fun setTargetPower(watts: Int, timeoutMs: Long): CompatibilityCommandResult {
            calls += "setTargetPower(watts=$watts,timeout=$timeoutMs)"
            return runScript(setTargetPowerScript)
        }

        override fun stopWorkout(timeoutMs: Long): CompatibilityCommandResult {
            calls += "stopWorkout(timeout=$timeoutMs)"
            return runScript(stopScript)
        }

        override fun disconnect(timeoutMs: Long): CompatibilityCommandResult {
            calls += "disconnect(timeout=$timeoutMs)"
            return runScript(disconnectScript)
        }

        override fun hold(durationMs: Long) {
            calls += "hold(duration=$durationMs)"
            clock.now += durationMs
        }

        private fun runScript(script: ArrayDeque<ScriptedCommand>): CompatibilityCommandResult {
            val command = if (script.isEmpty()) {
                ScriptedCommand(
                    result = CompatibilityCommandResult.Success,
                    durationMs = 0L,
                )
            } else {
                script.removeFirst()
            }
            clock.now += command.durationMs
            return command.result
        }
    }
}

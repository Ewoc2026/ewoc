package com.example.ergometerapp.ble

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import com.example.ergometerapp.BuildConfig
import com.example.ergometerapp.ble.debug.FtmsDebugBuffer
import com.example.ergometerapp.ble.debug.FtmsDebugEvent
import com.example.ergometerapp.logging.AppLog

enum class FtmsUnexpectedControlPointResponseReason {
    NO_COMMAND_IN_FLIGHT,
    OPCODE_MISMATCH,
    STALE_LINK_GENERATION,
}

/**
 * Serializes FTMS Control Point commands and matches them to response opcodes.
 *
 * BLE Control Point procedures are defined as request/response; devices may drop or
 * reject concurrent commands. This controller enforces a single in-flight command,
 * applies a "last wins" policy for target power updates, and releases the BUSY
 * state on either a response or a timeout to avoid permanent deadlock.
 */
class FtmsController(
    private val writeControlPoint: (ByteArray) -> Boolean,
    private val onStopAcknowledged: () -> Unit = {},
    private val onCommandTimeout: (requestOpcode: Int?) -> Unit = {},
    private val onCommandLifecycleEvent: (FtmsCommandLifecycleEvent) -> Unit = {},
    private val onUnexpectedControlPointResponse: (
        expectedOpcode: Int?,
        receivedOpcode: Int,
        resultCode: Int,
        reason: FtmsUnexpectedControlPointResponseReason,
    ) -> Unit = { _, _, _, _ -> },
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var transportReady = false

    private var hasStopped = false

    private var pendingReset = false

    private var commandState = FtmsCommandState.IDLE
    private var pendingTargetPowerWatts: Int? = null
    private var pendingStopWorkout = false

    private var lastSentTargetPower: Int? = null

    private val commandTimeoutMs = 2500L

    private var timeoutRunnable: Runnable? = null
    private var inFlightRequestOpcode: Int? = null
    private var inFlightControlLinkGeneration: Int? = null
    private var inFlightCommandCorrelationId: String? = null
    private var commandSequence = 0L
    private var activeControlLinkGeneration = 0

    private fun enforceMainThreadContract(methodName: String) {
        if (!BuildConfig.DEBUG) return
        val currentLooper = Looper.myLooper() ?: return
        val mainLooper = Looper.getMainLooper() ?: return
        check(currentLooper == mainLooper) {
            "FtmsController.$methodName must be called on the main thread."
        }
    }

    private fun dumpControllerState(event: String) {
        AppLog.telemetryDebug("FTMS") {
            "CTRL_DUMP event=$event state=$commandState transportReady=$transportReady " +
                "hasStopped=$hasStopped " +
                "pendingTarget=$pendingTargetPowerWatts pendingReset=$pendingReset " +
                "pendingStop=$pendingStopWorkout lastSentTarget=$lastSentTargetPower " +
                "inFlightOpcode=$inFlightRequestOpcode " +
                "inFlightLinkGeneration=$inFlightControlLinkGeneration " +
                "activeLinkGeneration=$activeControlLinkGeneration " +
                "inFlightCorrelationId=$inFlightCommandCorrelationId " +
                "timeoutArmed=${timeoutRunnable != null}"
        }
    }

    private fun debugLog(message: String) {
        AppLog.telemetryDebug("FTMS") { message }
    }

    private fun controlPointPayloadSummary(payload: ByteArray): String {
        val opcode = payload.firstOrNull()?.toInt()?.and(0xFF)
        return "opcode=${opcode ?: "none"} size=${payload.size}"
    }

    private fun nextCommandCorrelationId(requestOpcode: Int?): String {
        commandSequence += 1
        val opcodeHex = requestOpcode?.let { value ->
            value.toString(16).padStart(2, '0')
        } ?: "na"
        return "ftms-cmd-$commandSequence-op$opcodeHex"
    }

    /**
     * Indicates whether the controller is ready to send commands.
     */
    @MainThread
    fun isReady(): Boolean = transportReady

    /**
     * Updates command transport readiness from BLE setup state.
     *
     * Callers must set this true only after Control Point indication setup is
     * complete, and false on disconnect/teardown.
     */
    @MainThread
    fun setTransportReady(ready: Boolean) {
        enforceMainThreadContract("setTransportReady")
        transportReady = ready
        dumpControllerState("setTransportReady(ready=$ready)")
    }

    /**
     * Updates active FTMS link generation used for response ownership checks.
     *
     * Responses are accepted only when they match both the in-flight opcode and
     * the link generation active when the command was sent.
     */
    @MainThread
    fun onControlLinkGenerationChanged(generation: Int) {
        enforceMainThreadContract("onControlLinkGenerationChanged")
        activeControlLinkGeneration = generation.coerceAtLeast(0)
        dumpControllerState("onControlLinkGenerationChanged(generation=$activeControlLinkGeneration)")
    }

    private fun sendCommand(payload: ByteArray, label: String) {
        if (!transportReady) {
            Log.w("FTMS", "Command ignored (not ready): $label ${controlPointPayloadSummary(payload)}")
            dumpControllerState("sendCommandIgnoredNotReady(label=$label)")
            return
        }

        if (commandState == FtmsCommandState.BUSY) {
            Log.w("FTMS", "Command ignored (BUSY): $label ${controlPointPayloadSummary(payload)}")
            dumpControllerState("sendCommandIgnoredBusy(label=$label)")
            return
        }

        debugLog("Sending: $label ${controlPointPayloadSummary(payload)}")
        val writeStarted = writeControlPoint(payload)
        if (!writeStarted) {
            Log.w(
                "FTMS",
                "Command send failed (write not started): $label ${controlPointPayloadSummary(payload)}",
            )
            dumpControllerState("sendCommandWriteFailed(label=$label)")
            return
        }

        commandState = FtmsCommandState.BUSY
        inFlightRequestOpcode = payload.firstOrNull()?.toInt()?.and(0xFF)
        inFlightControlLinkGeneration = activeControlLinkGeneration
        inFlightCommandCorrelationId = nextCommandCorrelationId(inFlightRequestOpcode)
        onCommandLifecycleEvent(
            FtmsCommandLifecycleEvent(
                stage = FtmsCommandLifecycleStage.SENT,
                correlationId = inFlightCommandCorrelationId,
                requestOpcode = inFlightRequestOpcode,
                expectedOpcode = inFlightRequestOpcode,
                receivedOpcode = null,
                resultCode = null,
                unexpectedReason = null,
            ),
        )
        startTimeoutTimer()
        dumpControllerState("sendCommandSent(label=$label,writeStarted=true)")
        // BUSY is released only after a Control Point response or timeout.
    }

    /**
     * Requests exclusive control of the trainer.
     *
     * Some FTMS devices require this before accepting other Control Point commands.
     */
    @MainThread
    fun requestControl() {
        enforceMainThreadContract("requestControl")
        if (hasStopped) {
            debugLog("requestControl() ignored (already stopped)")
            dumpControllerState("requestControlIgnoredAlreadyStopped")
            return
        }

        val payload = byteArrayOf(0x00.toByte())
        sendCommand(payload, "requestControl")
    }

    @Suppress("unused")
    /**
     * Sets a device-specific resistance level.
     *
     * This is optional in FTMS and may be ignored by devices that only support
     * power-based targets.
     */
    @MainThread
    fun setResistanceLevel(level: Int) {
        enforceMainThreadContract("setResistanceLevel")
        val payload = byteArrayOf(0x04.toByte(), level.toByte())
        sendCommand(payload, "setResistanceLevel($level)")
    }

    /**
     * Sets target power in watts (clamped to a safe range for FTMS encoding).
     *
     * "Last wins" ensures rapid UI updates do not queue stale targets while the
     * device is processing the previous command.
     */
    @MainThread
    fun setTargetPower(watts: Int) {
        enforceMainThreadContract("setTargetPower")
        val w = watts.coerceIn(0, 2000)

        if (commandState == FtmsCommandState.BUSY) {
            pendingTargetPowerWatts = w
            debugLog("Queued target power (last wins): $w W")
            dumpControllerState("setTargetPowerQueuedBusy(watts=$w)")
            return
        }

        if (lastSentTargetPower == w) return

        lastSentTargetPower = w

        FtmsDebugBuffer.record(
            FtmsDebugEvent.TargetPowerIssued(System.currentTimeMillis(), w)
        )
        handler.postDelayed(
            {
                FtmsDebugBuffer.record(
                    FtmsDebugEvent.ObservationEnded(System.currentTimeMillis())
                )
            },
            1000L
        )

        // "Last wins" avoids a backlog that FTMS devices are unlikely to process.
        if (commandState == FtmsCommandState.BUSY) {
            pendingTargetPowerWatts = w
            debugLog("Queued target power (last wins): $w W")
            dumpControllerState("setTargetPowerQueuedBusyPostObservation(watts=$w)")
            return
        }

        val lo = (w and 0xFF).toByte()
        val hi = ((w shr 8) and 0xFF).toByte()
        val payload = byteArrayOf(0x05.toByte(), lo, hi)

        sendCommand(payload, "setTargetPower($w)")
    }

    /**
     * Stops the current workout session if the device supports it.
     *
     * STOP is serialized through the same Control Point pipeline as other
     * commands. If another command is in flight, STOP is queued and sent as
     * soon as BUSY clears.
     */
    @MainThread
    fun stopWorkout() {
        enforceMainThreadContract("stopWorkout")
        if (hasStopped) {
            debugLog("stopWorkout() ignored (already stopped)")
            dumpControllerState("stopWorkoutIgnoredAlreadyStopped")
            return
        }
        hasStopped = true

        // STOP is terminal for queued work in this lifecycle.
        pendingTargetPowerWatts = null
        pendingReset = false
        pendingStopWorkout = true

        if (commandState == FtmsCommandState.BUSY) {
            debugLog("Queued stopWorkout (pending)")
            dumpControllerState("stopWorkoutQueuedBusy")
            return
        }

        sendStopWorkout()
    }

    private fun sendStopWorkout() {
        pendingStopWorkout = false
        dumpControllerState("sendStopWorkout")
        val payload = byteArrayOf(0x08.toByte(), 0x01.toByte())
        sendCommand(payload, "stopWorkout")
    }


    /**
     * Sends an explicit zero-watt target without implying STOP or RESET semantics.
     *
     * This is a trainer-specific probe path for comparing `Set Target Power(0)` against
     * normative FTMS procedures such as STOP and RESET. Callers must not treat this as a
     * generic "release ERG" abstraction because FTMS does not define that procedure.
     */
    @MainThread
    fun setZeroTargetPower() {
        enforceMainThreadContract("setZeroTargetPower")
        setTargetPower(0)
    }

    @Suppress("unused")
    /**
     * Pauses the current workout session if the device supports it.
     */
    @MainThread
    fun pause() {
        enforceMainThreadContract("pause")
        if (hasStopped) {
            debugLog("pause() ignored (already stopped)")
            dumpControllerState("pauseIgnoredAlreadyStopped")
            return
        }
        if (commandState == FtmsCommandState.BUSY) {
            debugLog("pause() ignored (BUSY)")
            dumpControllerState("pauseIgnoredBusy")
            return
        }
        val payload = byteArrayOf(0x08.toByte(), 0x02.toByte())
        sendCommand(payload, "pause")
    }


    /**
     * Resets the device state.
     *
     * Reset is queued while BUSY to preserve the invariant of a single in-flight
     * Control Point command.
     */
    @MainThread
    fun reset() {
        enforceMainThreadContract("reset")

        hasStopped = false
        lastSentTargetPower = null
        pendingStopWorkout = false

        if (commandState == FtmsCommandState.BUSY) {
            pendingReset = true
            debugLog("Queued reset (pending)")
            dumpControllerState("resetQueuedBusy")
            return
        }
        val payload = byteArrayOf(0x01.toByte())
        sendCommand(payload, "reset")
    }

    /**
     * Seeds an in-flight command without transport writes for deterministic tests.
     *
     * This lets orchestration tests verify opcode-mismatch response handling while
     * preserving the BUSY invariants used by production response gating.
     */
    @MainThread
    internal fun seedInFlightCommandForTest(requestOpcode: Int) {
        enforceMainThreadContract("seedInFlightCommandForTest")
        val normalizedOpcode = requestOpcode and 0xFF
        cancelTimeoutTimer()
        commandState = FtmsCommandState.BUSY
        inFlightRequestOpcode = normalizedOpcode
        inFlightControlLinkGeneration = activeControlLinkGeneration
        inFlightCommandCorrelationId =
            "ftms-test-inflight-op${normalizedOpcode.toString(16).padStart(2, '0')}"
        dumpControllerState("seedInFlightCommandForTest(op=$normalizedOpcode)")
    }

    /**
     * Exposes the currently tracked in-flight opcode to JVM tests without reflection.
     */
    @MainThread
    internal fun inFlightRequestOpcodeForTest(): Int? {
        enforceMainThreadContract("inFlightRequestOpcodeForTest")
        return inFlightRequestOpcode
    }

    /**
     * Handles a Control Point response packet (`0x80 ...`) from the transport.
     *
     * Returns true only when the response matched the active in-flight command.
     * Callers should gate command-specific side effects (for example session
     * transitions) on this result so stale or mismatched responses cannot mutate
     * orchestration state.
     */
    @MainThread
    fun onControlPointResponse(
        requestOpcode: Int,
        resultCode: Int,
        responseLinkGeneration: Int = activeControlLinkGeneration,
    ): Boolean {
        enforceMainThreadContract("onControlPointResponse")
        if (commandState != FtmsCommandState.BUSY) {
            Log.w(
                "FTMS",
                "Unexpected CP response without in-flight command: opcode=$requestOpcode result=$resultCode",
            )
            dumpControllerState("onControlPointResponseUnexpectedNoInFlight(op=$requestOpcode,result=$resultCode)")
            onUnexpectedControlPointResponse(
                inFlightRequestOpcode,
                requestOpcode,
                resultCode,
                FtmsUnexpectedControlPointResponseReason.NO_COMMAND_IN_FLIGHT,
            )
            onCommandLifecycleEvent(
                FtmsCommandLifecycleEvent(
                    stage = FtmsCommandLifecycleStage.UNEXPECTED_RESPONSE,
                    correlationId = inFlightCommandCorrelationId,
                    requestOpcode = inFlightRequestOpcode,
                    expectedOpcode = inFlightRequestOpcode,
                    receivedOpcode = requestOpcode,
                    resultCode = resultCode,
                    unexpectedReason = FtmsUnexpectedControlPointResponseReason.NO_COMMAND_IN_FLIGHT,
                ),
            )
            return false
        }

        val expectedLinkGeneration = inFlightControlLinkGeneration ?: activeControlLinkGeneration
        if (expectedLinkGeneration != responseLinkGeneration) {
            Log.w(
                "FTMS",
                "Ignoring CP response from stale link generation expected=$expectedLinkGeneration got=$responseLinkGeneration opcode=$requestOpcode result=$resultCode",
            )
            dumpControllerState(
                "onControlPointResponseUnexpectedStaleLink(expected=$expectedLinkGeneration,got=$responseLinkGeneration,op=$requestOpcode,result=$resultCode)",
            )
            onUnexpectedControlPointResponse(
                inFlightRequestOpcode,
                requestOpcode,
                resultCode,
                FtmsUnexpectedControlPointResponseReason.STALE_LINK_GENERATION,
            )
            onCommandLifecycleEvent(
                FtmsCommandLifecycleEvent(
                    stage = FtmsCommandLifecycleStage.UNEXPECTED_RESPONSE,
                    correlationId = inFlightCommandCorrelationId,
                    requestOpcode = inFlightRequestOpcode,
                    expectedOpcode = inFlightRequestOpcode,
                    receivedOpcode = requestOpcode,
                    resultCode = resultCode,
                    unexpectedReason = FtmsUnexpectedControlPointResponseReason.STALE_LINK_GENERATION,
                ),
            )
            // Keep BUSY so the current link's command can still resolve.
            return false
        }

        val expectedOpcode = inFlightRequestOpcode
        if (expectedOpcode != null && expectedOpcode != requestOpcode) {
            Log.w(
                "FTMS",
                "Ignoring mismatched CP response expected=$expectedOpcode got=$requestOpcode result=$resultCode",
            )
            dumpControllerState(
                "onControlPointResponseUnexpectedMismatch(expected=$expectedOpcode,got=$requestOpcode,result=$resultCode)",
            )
            onUnexpectedControlPointResponse(
                expectedOpcode,
                requestOpcode,
                resultCode,
                FtmsUnexpectedControlPointResponseReason.OPCODE_MISMATCH,
            )
            onCommandLifecycleEvent(
                FtmsCommandLifecycleEvent(
                    stage = FtmsCommandLifecycleStage.UNEXPECTED_RESPONSE,
                    correlationId = inFlightCommandCorrelationId,
                    requestOpcode = expectedOpcode,
                    expectedOpcode = expectedOpcode,
                    receivedOpcode = requestOpcode,
                    resultCode = resultCode,
                    unexpectedReason = FtmsUnexpectedControlPointResponseReason.OPCODE_MISMATCH,
                ),
            )
            // Keep BUSY so the expected command can still resolve by matching response or timeout.
            return false
        }

        val resolvedCorrelationId = inFlightCommandCorrelationId
        cancelTimeoutTimer()
        inFlightRequestOpcode = null
        inFlightControlLinkGeneration = null
        inFlightCommandCorrelationId = null

        val ok = (resultCode == 0x01)
        commandState = if (ok) FtmsCommandState.SUCCESS else FtmsCommandState.ERROR
        debugLog("CP response opcode=$requestOpcode result=$resultCode state=$commandState")
        dumpControllerState("onControlPointResponse(op=$requestOpcode,result=$resultCode,ok=$ok)")
        onCommandLifecycleEvent(
            FtmsCommandLifecycleEvent(
                stage = FtmsCommandLifecycleStage.RESPONSE,
                correlationId = resolvedCorrelationId,
                requestOpcode = requestOpcode,
                expectedOpcode = requestOpcode,
                receivedOpcode = requestOpcode,
                resultCode = resultCode,
                unexpectedReason = null,
            ),
        )
        if (requestOpcode == 0x08 && ok) {

            onStopAcknowledged()
        }

        // Release for the next command after a definitive response.
        commandState = FtmsCommandState.IDLE

        if (pendingStopWorkout) {
            debugLog("Sending queued stopWorkout")
            dumpControllerState("onControlPointResponseSendingQueuedStop")
            sendStopWorkout()
            return true
        }

        if (pendingReset) {
            pendingReset = false
            debugLog("Sending queued reset")
            dumpControllerState("onControlPointResponseSendingQueuedReset")
            reset()
            return true
        }

        // Apply the latest target if it was updated while BUSY.
        val pending = pendingTargetPowerWatts
        if (pending != null) {
            pendingTargetPowerWatts = null
            debugLog("Sending queued target power: $pending W")
            dumpControllerState("onControlPointResponseSendingQueuedTarget(watts=$pending)")
            setTargetPower(pending) // this will go through now because commandState is IDLE
        }

        dumpControllerState("onControlPointResponseDone")
        return true
    }

    /**
     * Clears command pipeline state after a GATT disconnect.
     *
     * If disconnect happens while STOP is pending/in flight, timeout recovery
     * is bypassed and BUSY is cleared immediately.
     */
    @MainThread
    fun onDisconnected() {
        enforceMainThreadContract("onDisconnected")
        transportReady = false
        val stopWasPendingOrInFlight =
            hasStopped && (pendingStopWorkout || commandState == FtmsCommandState.BUSY)
        cancelTimeoutTimer()

        pendingTargetPowerWatts = null
        pendingReset = false
        pendingStopWorkout = false
        inFlightRequestOpcode = null
        inFlightControlLinkGeneration = null
        inFlightCommandCorrelationId = null

        if (stopWasPendingOrInFlight) {
            debugLog("Disconnected during STOP; cleared BUSY without timeout recovery")
        }
        commandState = FtmsCommandState.IDLE
        dumpControllerState("onDisconnected")
    }

    private fun startTimeoutTimer() {
        cancelTimeoutTimer()

        timeoutRunnable = Runnable {
            cancelTimeoutTimer()
            // If the device never responds, release BUSY to avoid a permanent lock.
            if (commandState == FtmsCommandState.BUSY) {
                Log.w("FTMS", "Control Point command timeout -> forcing IDLE")
                val timedOutOpcode = inFlightRequestOpcode
                val timedOutCorrelationId = inFlightCommandCorrelationId
                commandState = FtmsCommandState.IDLE
                inFlightRequestOpcode = null
                inFlightControlLinkGeneration = null
                inFlightCommandCorrelationId = null
                dumpControllerState("commandTimeoutForcedIdle")
                onCommandTimeout(timedOutOpcode)
                onCommandLifecycleEvent(
                    FtmsCommandLifecycleEvent(
                        stage = FtmsCommandLifecycleStage.TIMEOUT,
                        correlationId = timedOutCorrelationId,
                        requestOpcode = timedOutOpcode,
                        expectedOpcode = timedOutOpcode,
                        receivedOpcode = null,
                        resultCode = null,
                        unexpectedReason = null,
                    ),
                )

                if (pendingStopWorkout) {
                    debugLog("Sending queued stopWorkout after timeout")
                    dumpControllerState("commandTimeoutSendingQueuedStop")
                    sendStopWorkout()
                } else if (pendingReset) {
                    pendingReset = false
                    debugLog("Sending queued reset after timeout")
                    dumpControllerState("commandTimeoutSendingQueuedReset")
                    reset()
                } else {
                    val pending = pendingTargetPowerWatts
                    if (pending != null) {
                        pendingTargetPowerWatts = null
                        debugLog("Sending queued target power after timeout: $pending W")
                        dumpControllerState("commandTimeoutSendingQueuedTarget(watts=$pending)")
                        setTargetPower(pending)
                    }
                }
            }
        }

        handler.postDelayed(timeoutRunnable!!, commandTimeoutMs)
    }

    private fun cancelTimeoutTimer() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

}

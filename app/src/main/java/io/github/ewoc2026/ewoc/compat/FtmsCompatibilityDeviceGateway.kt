package io.github.ewoc2026.ewoc.compat

import android.content.Context
import io.github.ewoc2026.ewoc.ble.FtmsBleClient
import io.github.ewoc2026.ewoc.ble.FtmsCommandLifecycleEvent
import io.github.ewoc2026.ewoc.ble.FtmsCommandLifecycleStage
import io.github.ewoc2026.ewoc.ble.FtmsController

private const val ftmsOpcodeRequestControl = 0x00
private const val ftmsOpcodeReset = 0x01
private const val ftmsOpcodeSetTargetPower = 0x05
private const val ftmsOpcodeStopWorkout = 0x08
private const val ftmsResultCodeSuccess = 0x01

internal sealed class CompatibilityGatewayTerminalOutcome {
    data class Response(val resultCode: Int?) : CompatibilityGatewayTerminalOutcome()
    object Timeout : CompatibilityGatewayTerminalOutcome()
    object Disconnected : CompatibilityGatewayTerminalOutcome()
}

private fun CompatibilityControlPointTrace.withFallback(
    expectedOpcode: Int?,
    stage: String,
    resultCode: Int? = this.resultCode,
): CompatibilityControlPointTrace {
    return CompatibilityControlPointTrace(
        correlationId = correlationId,
        requestOpcode = requestOpcode,
        expectedOpcode = this.expectedOpcode ?: expectedOpcode,
        receivedOpcode = receivedOpcode,
        resultCode = resultCode,
        stage = this.stage ?: stage,
    )
}

internal fun resolveCompatibilityGatewayCommandResult(
    commandStarted: Boolean,
    terminalOutcome: CompatibilityGatewayTerminalOutcome?,
    expectedOpcode: Int? = null,
    controlPointTrace: CompatibilityControlPointTrace? = null,
): CompatibilityCommandResult {
    if (!commandStarted) {
        return CompatibilityCommandResult(
            status = CompatibilityCommandStatus.WRITE_NOT_STARTED,
            detail = "FTMS control-point write did not start.",
            controlPointTrace = controlPointTrace?.withFallback(
                expectedOpcode = expectedOpcode,
                stage = "write_not_started",
            ) ?: CompatibilityControlPointTrace(
                expectedOpcode = expectedOpcode,
                stage = "write_not_started",
            ),
        )
    }
    return when (terminalOutcome) {
        is CompatibilityGatewayTerminalOutcome.Response -> {
            if (terminalOutcome.resultCode == ftmsResultCodeSuccess) {
                CompatibilityCommandResult(
                    status = CompatibilityCommandStatus.SUCCESS,
                    controlPointTrace = controlPointTrace?.withFallback(
                        expectedOpcode = expectedOpcode,
                        stage = "response",
                        resultCode = terminalOutcome.resultCode,
                    ) ?: CompatibilityControlPointTrace(
                        expectedOpcode = expectedOpcode,
                        resultCode = terminalOutcome.resultCode,
                        stage = "response",
                    ),
                )
            } else {
                CompatibilityCommandResult(
                    status = CompatibilityCommandStatus.REJECTED,
                    detail = "FTMS control-point rejected command (resultCode=${terminalOutcome.resultCode}).",
                    controlPointTrace = controlPointTrace?.withFallback(
                        expectedOpcode = expectedOpcode,
                        stage = "response",
                        resultCode = terminalOutcome.resultCode,
                    ) ?: CompatibilityControlPointTrace(
                        expectedOpcode = expectedOpcode,
                        resultCode = terminalOutcome.resultCode,
                        stage = "response",
                    ),
                )
            }
        }

        CompatibilityGatewayTerminalOutcome.Timeout -> {
            CompatibilityCommandResult(
                status = CompatibilityCommandStatus.TIMEOUT,
                detail = "Timed out waiting for FTMS control-point acknowledgement.",
                controlPointTrace = controlPointTrace?.withFallback(
                    expectedOpcode = expectedOpcode,
                    stage = "timeout",
                ) ?: CompatibilityControlPointTrace(
                    expectedOpcode = expectedOpcode,
                    stage = "timeout",
                ),
            )
        }

        CompatibilityGatewayTerminalOutcome.Disconnected -> {
            CompatibilityCommandResult(
                status = CompatibilityCommandStatus.DISCONNECTED,
                detail = "FTMS link disconnected while command was in flight.",
                controlPointTrace = controlPointTrace?.withFallback(
                    expectedOpcode = expectedOpcode,
                    stage = "disconnected",
                ) ?: CompatibilityControlPointTrace(
                    expectedOpcode = expectedOpcode,
                    stage = "disconnected",
                ),
            )
        }

        null -> {
            CompatibilityCommandResult(
                status = CompatibilityCommandStatus.TIMEOUT,
                detail = "Timed out waiting for FTMS command terminal event.",
                controlPointTrace = controlPointTrace?.withFallback(
                    expectedOpcode = expectedOpcode,
                    stage = "timeout",
                ) ?: CompatibilityControlPointTrace(
                    expectedOpcode = expectedOpcode,
                    stage = "timeout",
                ),
            )
        }
    }
}

private data class PendingCommand(
    val expectedOpcode: Int,
    var sent: Boolean = false,
    var correlationId: String? = null,
    var requestOpcode: Int? = null,
    var receivedOpcode: Int? = null,
    var resultCode: Int? = null,
    var terminalStage: String? = null,
    var terminal: CompatibilityGatewayTerminalOutcome? = null,
)

/**
 * Concrete Compatibility Mode gateway backed by isolated FTMS BLE/controller instances.
 *
 * This adapter is intentionally separate from regular session orchestration so
 * compatibility retries and blocking waits cannot alter normal-session behavior.
 */
class FtmsCompatibilityDeviceGateway(
    private val context: Context,
    private val trainerMacAddress: String,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val sleeper: (Long) -> Unit = { durationMs -> Thread.sleep(durationMs) },
) : CompatibilityDeviceGateway {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = Object()
    private var generation = 0
    private var bleClient: FtmsBleClient? = null
    private var ftmsController: FtmsController? = null
    private var transportReady = false
    private var connectFailureObserved = false
    private var disconnectObserved = false
    private var pendingCommand: PendingCommand? = null

    override fun connect(timeoutMs: Long): CompatibilityCommandResult {
        if (timeoutMs <= 0L) {
            return CompatibilityCommandResult(
                status = CompatibilityCommandStatus.TIMEOUT,
                detail = "Connect timeout must be positive.",
            )
        }

        recreateTransport()
        val client = synchronized(lock) { bleClient }
            ?: return CompatibilityCommandResult(
                status = CompatibilityCommandStatus.FAILED,
                detail = "Failed creating FTMS transport.",
            )

        synchronized(lock) {
            connectFailureObserved = false
            disconnectObserved = false
            transportReady = false
        }

        client.connect(trainerMacAddress)
        val deadline = nowEpochMs() + timeoutMs
        synchronized(lock) {
            while (true) {
                if (transportReady) {
                    return CompatibilityCommandResult.Success
                }
                if (connectFailureObserved || disconnectObserved) {
                    break
                }
                val remainingMs = deadline - nowEpochMs()
                if (remainingMs <= 0L) break
                lock.wait(remainingMs)
            }
        }

        client.close()
        return synchronized(lock) {
            if (connectFailureObserved || disconnectObserved) {
                CompatibilityCommandResult(
                    status = CompatibilityCommandStatus.DISCONNECTED,
                    detail = "FTMS transport disconnected before becoming ready.",
                )
            } else {
                CompatibilityCommandResult(
                    status = CompatibilityCommandStatus.TIMEOUT,
                    detail = "Timed out waiting for FTMS transport readiness.",
                )
            }
        }
    }

    override fun requestControl(timeoutMs: Long): CompatibilityCommandResult {
        return executeControlPointCommand(
            timeoutMs = timeoutMs,
            expectedOpcode = ftmsOpcodeRequestControl,
            issue = { controller -> controller.requestControl() },
        )
    }

    override fun reset(timeoutMs: Long): CompatibilityCommandResult {
        return executeControlPointCommand(
            timeoutMs = timeoutMs,
            expectedOpcode = ftmsOpcodeReset,
            issue = { controller -> controller.reset() },
        )
    }

    override fun setTargetPower(watts: Int, timeoutMs: Long): CompatibilityCommandResult {
        return executeControlPointCommand(
            timeoutMs = timeoutMs,
            expectedOpcode = ftmsOpcodeSetTargetPower,
            issue = { controller -> controller.setTargetPower(watts) },
        )
    }

    override fun stopWorkout(timeoutMs: Long): CompatibilityCommandResult {
        return executeControlPointCommand(
            timeoutMs = timeoutMs,
            expectedOpcode = ftmsOpcodeStopWorkout,
            issue = { controller -> controller.stopWorkout() },
        )
    }

    override fun disconnect(timeoutMs: Long): CompatibilityCommandResult {
        val client = synchronized(lock) { bleClient } ?: return CompatibilityCommandResult.Success
        synchronized(lock) {
            disconnectObserved = false
        }
        client.close()

        if (timeoutMs <= 0L) {
            synchronized(lock) {
                transportReady = false
            }
            return CompatibilityCommandResult.Success
        }

        val deadline = nowEpochMs() + timeoutMs
        synchronized(lock) {
            while (transportReady && !disconnectObserved) {
                val remainingMs = deadline - nowEpochMs()
                if (remainingMs <= 0L) {
                    return CompatibilityCommandResult(
                        status = CompatibilityCommandStatus.TIMEOUT,
                        detail = "Timed out waiting for FTMS disconnect.",
                    )
                }
                lock.wait(remainingMs)
            }
        }
        return CompatibilityCommandResult.Success
    }

    override fun hold(durationMs: Long) {
        if (durationMs <= 0L) return
        try {
            sleeper(durationMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun executeControlPointCommand(
        timeoutMs: Long,
        expectedOpcode: Int,
        issue: (FtmsController) -> Unit,
    ): CompatibilityCommandResult {
        if (timeoutMs <= 0L) {
            return CompatibilityCommandResult(
                status = CompatibilityCommandStatus.TIMEOUT,
                detail = "Command timeout must be positive.",
                controlPointTrace = CompatibilityControlPointTrace(
                    expectedOpcode = expectedOpcode,
                    stage = "timeout",
                ),
            )
        }

        val controller = synchronized(lock) {
            val active = ftmsController
            if (active == null || !transportReady || !active.isReady()) {
                return CompatibilityCommandResult(
                    status = CompatibilityCommandStatus.WRITE_NOT_STARTED,
                    detail = "FTMS transport is not ready for control-point writes.",
                    controlPointTrace = CompatibilityControlPointTrace(
                        expectedOpcode = expectedOpcode,
                        stage = "write_not_started",
                    ),
                )
            }
            pendingCommand = PendingCommand(expectedOpcode = expectedOpcode)
            active
        }

        issue(controller)

        val pendingAfterIssue = synchronized(lock) { pendingCommand }
        val commandStarted = pendingAfterIssue?.let { pending ->
            val started = pending.sent || pending.terminal != null
            if (!started) {
                synchronized(lock) {
                    if (pendingCommand === pending) {
                        pendingCommand = null
                    }
                }
            }
            started
        } ?: false
        if (!commandStarted) {
            return resolveCompatibilityGatewayCommandResult(
                commandStarted = false,
                terminalOutcome = null,
                expectedOpcode = expectedOpcode,
                controlPointTrace = buildControlPointTrace(pendingAfterIssue),
            )
        }

        val deadline = nowEpochMs() + timeoutMs
        var terminalOutcome: CompatibilityGatewayTerminalOutcome? = null
        var resolvedPending: PendingCommand? = null
        synchronized(lock) {
            while (terminalOutcome == null) {
                val pending = pendingCommand
                terminalOutcome = pending?.terminal
                if (terminalOutcome != null) {
                    resolvedPending = pending
                    pendingCommand = null
                    break
                }
                val remainingMs = deadline - nowEpochMs()
                if (remainingMs <= 0L) {
                    resolvedPending = pending
                    pendingCommand = null
                    break
                }
                lock.wait(remainingMs)
            }
        }

        return resolveCompatibilityGatewayCommandResult(
            commandStarted = true,
            terminalOutcome = terminalOutcome,
            expectedOpcode = expectedOpcode,
            controlPointTrace = buildControlPointTrace(resolvedPending),
        )
    }

    private fun buildControlPointTrace(pending: PendingCommand?): CompatibilityControlPointTrace? {
        val resolvedPending = pending ?: return null
        return CompatibilityControlPointTrace(
            correlationId = resolvedPending.correlationId,
            requestOpcode = resolvedPending.requestOpcode,
            expectedOpcode = resolvedPending.expectedOpcode,
            receivedOpcode = resolvedPending.receivedOpcode,
            resultCode = resolvedPending.resultCode,
            stage = resolvedPending.terminalStage,
        )
    }

    private fun recreateTransport() {
        val staleClient = synchronized(lock) {
            generation += 1
            val existingClient = bleClient
            bleClient = null
            ftmsController = null
            pendingCommand = null
            transportReady = false
            connectFailureObserved = false
            disconnectObserved = false
            existingClient
        }
        staleClient?.close()

        val localGeneration = synchronized(lock) { generation }
        var controllerRef: FtmsController? = null
        val client = FtmsBleClient(
            context = context,
            onIndoorBikeData = {},
            onReady = { ready, linkGeneration ->
                controllerRef?.onControlLinkGenerationChanged(linkGeneration)
                onTransportReadyChanged(generation = localGeneration, ready = ready)
            },
            onControlPointResponse = { requestOpcode, resultCode, linkGeneration ->
                if (!isActiveGeneration(localGeneration)) return@FtmsBleClient
                controllerRef?.onControlPointResponse(
                    requestOpcode = requestOpcode,
                    resultCode = resultCode,
                    responseLinkGeneration = linkGeneration,
                )
            },
            onDisconnected = {
                onTransportDisconnected(generation = localGeneration)
            },
        )
        val controller = FtmsController(
            writeControlPoint = { payload ->
                if (!isActiveGeneration(localGeneration)) {
                    return@FtmsController false
                }
                client.writeControlPoint(payload)
            },
            onCommandLifecycleEvent = { event ->
                onCommandLifecycleEvent(
                    generation = localGeneration,
                    lifecycleEvent = event,
                )
            },
        )
        controllerRef = controller

        synchronized(lock) {
            if (generation != localGeneration) {
                client.close()
                return
            }
            bleClient = client
            ftmsController = controller
        }
    }

    private fun isActiveGeneration(candidateGeneration: Int): Boolean {
        return synchronized(lock) { generation == candidateGeneration }
    }

    private fun onTransportReadyChanged(generation: Int, ready: Boolean) {
        val controller = synchronized(lock) {
            if (this.generation != generation) return
            transportReady = ready
            if (!ready) {
                connectFailureObserved = true
            }
            lock.notifyAll()
            ftmsController
        }

        controller?.setTransportReady(ready)
        if (!ready) {
            controller?.onDisconnected()
            // Compatibility retries are orchestrator-owned; cancel BLE auto-reconnect here.
            synchronized(lock) {
                if (this.generation == generation) {
                    bleClient?.close()
                }
            }
        }
    }

    private fun onTransportDisconnected(generation: Int) {
        val controller = synchronized(lock) {
            if (this.generation != generation) return
            disconnectObserved = true
            connectFailureObserved = true
            transportReady = false
            pendingCommand?.terminalStage = "disconnected"
            pendingCommand?.terminal = CompatibilityGatewayTerminalOutcome.Disconnected
            lock.notifyAll()
            ftmsController
        }
        controller?.setTransportReady(false)
        controller?.onDisconnected()
    }

    private fun onCommandLifecycleEvent(
        generation: Int,
        lifecycleEvent: FtmsCommandLifecycleEvent,
    ) {
        synchronized(lock) {
            if (this.generation != generation) return
            val pending = pendingCommand ?: return
            val expectedOpcode = pending.expectedOpcode

            when (lifecycleEvent.stage) {
                FtmsCommandLifecycleStage.SENT -> {
                    if (lifecycleEvent.requestOpcode == expectedOpcode) {
                        pending.sent = true
                        pending.correlationId = lifecycleEvent.correlationId
                        pending.requestOpcode = lifecycleEvent.requestOpcode
                        pending.terminalStage = "sent"
                        lock.notifyAll()
                    }
                }

                FtmsCommandLifecycleStage.RESPONSE -> {
                    if (lifecycleEvent.requestOpcode == expectedOpcode) {
                        pending.sent = true
                        pending.correlationId = lifecycleEvent.correlationId
                        pending.requestOpcode = lifecycleEvent.requestOpcode
                        pending.receivedOpcode = lifecycleEvent.receivedOpcode
                        pending.resultCode = lifecycleEvent.resultCode
                        pending.terminalStage = "response"
                        pending.terminal = CompatibilityGatewayTerminalOutcome.Response(
                            resultCode = lifecycleEvent.resultCode,
                        )
                        lock.notifyAll()
                    }
                }

                FtmsCommandLifecycleStage.TIMEOUT -> {
                    if (lifecycleEvent.requestOpcode == expectedOpcode) {
                        pending.sent = true
                        pending.correlationId = lifecycleEvent.correlationId
                        pending.requestOpcode = lifecycleEvent.requestOpcode
                        pending.terminalStage = "timeout"
                        pending.terminal = CompatibilityGatewayTerminalOutcome.Timeout
                        lock.notifyAll()
                    }
                }

                FtmsCommandLifecycleStage.UNEXPECTED_RESPONSE -> Unit
            }
        }
    }
}

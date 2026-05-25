package com.example.ergometerapp

import com.example.ergometerapp.compat.CompatibilityCheckResult
import com.example.ergometerapp.compat.CompatibilityFailureCode
import com.example.ergometerapp.compat.CompatibilityRunArtifacts
import com.example.ergometerapp.compat.CompatibilitySummaryOutput
import com.example.ergometerapp.compat.CompatibilitySummaryStatus
import com.example.ergometerapp.compat.quirks.MatchConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.RejectedExecutionException

class CompatibilityCheckRunFacadeTest {

    @Test
    fun startRun_blocksWhenLaunchPreflightFails() {
        val executorRequests = mutableListOf<CompatibilityCheckExecutionRequest>()
        val facade = facade(
            executor = object : CompatibilityCheckExecutor {
                override fun runAndPersist(request: CompatibilityCheckExecutionRequest): CompatibilityCheckExecutionResult {
                    executorRequests += request
                    return CompatibilityCheckExecutionResult(
                        artifacts = compatibilityArtifacts("unused", CompatibilitySummaryStatus.PASS),
                        persisted = true,
                    )
                }
            },
        )
        val statePort = FakeCompatibilityCheckStatePort(statusMessage = "old-status")

        facade.startRun(
            launchRequest = CompatibilityCheckLaunchRequest(
                trainerMacAddress = null,
                trainerAlias = "Trainer",
                hasBluetoothConnectPermission = true,
            ),
            statePort = statePort,
            resolveRequiresTrainerMessage = { "trainer-required" },
            resolveRequiresPermissionMessage = { "permission-required" },
            resolveRunningStatusMessage = { "running" },
            resolvePassStatusMessage = { "pass" },
            resolveFailStatusMessage = { reason -> "fail:$reason" },
            resolvePersistFailureStatusMessage = { baseMessage -> "persist:$baseMessage" },
        )

        assertEquals("trainer-required", statePort.statusMessage)
        assertFalse(statePort.checkInProgress)
        assertTrue(executorRequests.isEmpty())
    }

    @Test
    fun startRun_marksRunningThenCompletesOnMainThread() {
        var backgroundTask: (() -> Unit)? = null
        var mainThreadTask: (() -> Unit)? = null
        val executorRequests = mutableListOf<CompatibilityCheckExecutionRequest>()
        val artifacts = compatibilityArtifacts(
            runId = "pass-run",
            status = CompatibilitySummaryStatus.PASS,
        )
        val facade = facade(
            executor = object : CompatibilityCheckExecutor {
                override fun runAndPersist(request: CompatibilityCheckExecutionRequest): CompatibilityCheckExecutionResult {
                    executorRequests += request
                    return CompatibilityCheckExecutionResult(
                        artifacts = artifacts,
                        persisted = true,
                    )
                }
            },
            runOnBackgroundThread = { task ->
                backgroundTask = task
            },
            runOnMainThread = { task ->
                mainThreadTask = task
            },
        )
        val statePort = FakeCompatibilityCheckStatePort()

        facade.startRun(
            launchRequest = CompatibilityCheckLaunchRequest(
                trainerMacAddress = "AA:BB:CC:DD:EE:FF",
                trainerAlias = "  Trainer Name  ",
                hasBluetoothConnectPermission = true,
            ),
            statePort = statePort,
            resolveRequiresTrainerMessage = { "trainer-required" },
            resolveRequiresPermissionMessage = { "permission-required" },
            resolveRunningStatusMessage = { "running" },
            resolvePassStatusMessage = { "pass" },
            resolveFailStatusMessage = { reason -> "fail:$reason" },
            resolvePersistFailureStatusMessage = { baseMessage -> "persist:$baseMessage" },
        )

        assertEquals("running", statePort.statusMessage)
        assertTrue(statePort.checkInProgress)
        assertTrue(executorRequests.isEmpty())
        assertTrue(backgroundTask != null)

        backgroundTask?.invoke()

        assertEquals(
            listOf(
                CompatibilityCheckExecutionRequest(
                    trainerMacAddress = "AA:BB:CC:DD:EE:FF",
                    trainerAlias = "Trainer Name",
                ),
            ),
            executorRequests,
        )
        assertTrue(mainThreadTask != null)
        assertTrue(statePort.checkInProgress)

        mainThreadTask?.invoke()

        assertSame(artifacts, statePort.latestRunArtifacts)
        assertFalse(statePort.checkInProgress)
        assertEquals("pass", statePort.statusMessage)
    }

    @Test
    fun startRun_ignoresRejectedBackgroundScheduling() {
        val statePort = FakeCompatibilityCheckStatePort(statusMessage = "old-status")
        val facade = facade(
            runOnBackgroundThread = {
                throw RejectedExecutionException("closed")
            },
        )

        facade.startRun(
            launchRequest = CompatibilityCheckLaunchRequest(
                trainerMacAddress = "AA:BB:CC:DD:EE:FF",
                trainerAlias = "Trainer",
                hasBluetoothConnectPermission = true,
            ),
            statePort = statePort,
            resolveRequiresTrainerMessage = { "trainer-required" },
            resolveRequiresPermissionMessage = { "permission-required" },
            resolveRunningStatusMessage = { "running" },
            resolvePassStatusMessage = { "pass" },
            resolveFailStatusMessage = { reason -> "fail:$reason" },
            resolvePersistFailureStatusMessage = { baseMessage -> "persist:$baseMessage" },
        )

        assertEquals("old-status", statePort.statusMessage)
        assertFalse(statePort.checkInProgress)
    }

    private fun facade(
        executor: CompatibilityCheckExecutor = object : CompatibilityCheckExecutor {
            override fun runAndPersist(request: CompatibilityCheckExecutionRequest): CompatibilityCheckExecutionResult {
                return CompatibilityCheckExecutionResult(
                    artifacts = compatibilityArtifacts("default", CompatibilitySummaryStatus.PASS),
                    persisted = true,
                )
            }
        },
        runOnBackgroundThread: ((() -> Unit) -> Unit) = { task -> task() },
        runOnMainThread: ((() -> Unit) -> Unit) = { task -> task() },
    ): CompatibilityCheckRunFacade {
        return CompatibilityCheckRunFacade(
            launchCoordinator = CompatibilityCheckLaunchCoordinator(),
            checkCoordinator = CompatibilityCheckCoordinator(
                resolveFailureReasonMessage = { _, _ -> "Unknown failure" },
            ),
            executor = executor,
            runOnBackgroundThread = runOnBackgroundThread,
            runOnMainThread = runOnMainThread,
        )
    }

    private fun compatibilityArtifacts(
        runId: String,
        status: CompatibilitySummaryStatus,
        failureReasonKey: String? = null,
        failureCode: CompatibilityFailureCode? = null,
    ): CompatibilityRunArtifacts {
        return CompatibilityRunArtifacts(
            runId = runId,
            capturedAtEpochMs = 2_000L,
            trainerIdentity = "AA:BB:CC:DD:EE:FF",
            trainerAlias = "Trainer",
            result = CompatibilityCheckResult(
                summary = CompatibilitySummaryOutput(
                    status = status,
                    startedAtEpochMs = 1_000L,
                    endedAtEpochMs = 2_000L,
                    elapsedMs = 1_000L,
                    totalBudgetMs = 3_000L,
                    quirksId = "default",
                    quirksMatchConfidence = MatchConfidence.LOW,
                    degradationSignals = emptyList(),
                    failureCode = failureCode,
                    failureCategory = null,
                    failureReasonKey = failureReasonKey,
                    failureDetail = null,
                ),
                timeline = emptyList(),
            ),
        )
    }

    private class FakeCompatibilityCheckStatePort(
        override var latestRunArtifacts: CompatibilityRunArtifacts? = null,
        override var checkInProgress: Boolean = false,
        override var statusMessage: String? = null,
    ) : CompatibilityCheckStatePort
}

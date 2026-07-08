package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.compat.CompatibilityRunArtifacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class CompatibilityFeatureFacadeTest {

    @Test
    fun onRunRequested_skipsDuplicateStartWhileRunInProgress() {
        var started = false
        val facade = facade(
            statePort = FakeCompatibilityFeatureStatePort(checkInProgress = true),
            runCompatibilityCheck = { _, _, _, _, _, _, _, _ ->
                started = true
            },
        )

        facade.onRunRequested()

        assertFalse(started)
    }

    @Test
    fun onRunRequested_buildsLaunchRequestFromCurrentViewModelState() {
        lateinit var capturedRequest: CompatibilityCheckLaunchRequest
        val statePort = FakeCompatibilityFeatureStatePort()
        val facade = facade(
            statePort = statePort,
            currentTrainerMac = { "AA:BB:CC:DD:EE:FF" },
            currentTrainerAlias = { "Trainer Alpha" },
            hasBluetoothConnectPermission = { false },
            runCompatibilityCheck = { launchRequest, delegatedStatePort, requiresTrainer, requiresPermission, running, pass, fail, persistFail ->
                capturedRequest = launchRequest
                assertSame(statePort, delegatedStatePort)
                assertEquals("trainer-required", requiresTrainer())
                assertEquals("permission-required", requiresPermission())
                assertEquals("running", running())
                assertEquals("pass", pass())
                assertEquals("fail:reason", fail("reason"))
                assertEquals("persist:base", persistFail("base"))
            },
        )

        facade.onRunRequested()

        assertEquals("AA:BB:CC:DD:EE:FF", capturedRequest.trainerMacAddress)
        assertEquals("Trainer Alpha", capturedRequest.trainerAlias)
        assertFalse(capturedRequest.hasBluetoothConnectPermission)
    }

    private fun facade(
        statePort: FakeCompatibilityFeatureStatePort = FakeCompatibilityFeatureStatePort(),
        currentTrainerMac: () -> String? = { "11:22:33:44:55:66" },
        currentTrainerAlias: () -> String = { "Trainer" },
        hasBluetoothConnectPermission: () -> Boolean = { true },
        runCompatibilityCheck: (
            CompatibilityCheckLaunchRequest,
            CompatibilityCheckStatePort,
            () -> String,
            () -> String,
            () -> String,
            () -> String,
            (String) -> String,
            (String) -> String,
        ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    ): CompatibilityFeatureFacade {
        return CompatibilityFeatureFacade(
            statePort = statePort,
            currentTrainerMac = currentTrainerMac,
            currentTrainerAlias = currentTrainerAlias,
            hasBluetoothConnectPermission = hasBluetoothConnectPermission,
            runCompatibilityCheck = runCompatibilityCheck,
            resolveRequiresTrainerMessage = { "trainer-required" },
            resolveRequiresPermissionMessage = { "permission-required" },
            resolveRunningStatusMessage = { "running" },
            resolvePassStatusMessage = { "pass" },
            resolveFailStatusMessage = { reason -> "fail:$reason" },
            resolvePersistFailureStatusMessage = { base -> "persist:$base" },
        )
    }

    private data class FakeCompatibilityFeatureStatePort(
        override var latestRunArtifacts: CompatibilityRunArtifacts? = null,
        override var checkInProgress: Boolean = false,
        override var statusMessage: String? = null,
    ) : CompatibilityFeatureStatePort
}

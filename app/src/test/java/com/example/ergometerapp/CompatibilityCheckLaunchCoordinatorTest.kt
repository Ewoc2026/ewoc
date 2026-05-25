package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityCheckLaunchCoordinatorTest {

    @Test
    fun prepareRun_blocksWhenTrainerIsMissing() {
        val coordinator = CompatibilityCheckLaunchCoordinator()

        val result = coordinator.prepareRun(
            request = CompatibilityCheckLaunchRequest(
                trainerMacAddress = null,
                trainerAlias = "Trainer",
                hasBluetoothConnectPermission = true,
            ),
            resolveRequiresTrainerMessage = { "trainer-required" },
            resolveRequiresPermissionMessage = { "permission-required" },
        )

        assertTrue(result is CompatibilityCheckLaunchPreparationResult.Blocked)
        assertEquals(
            "trainer-required",
            (result as CompatibilityCheckLaunchPreparationResult.Blocked).statusMessage,
        )
    }

    @Test
    fun prepareRun_blocksWhenPermissionIsMissingEvenIfAliasExists() {
        val coordinator = CompatibilityCheckLaunchCoordinator()

        val result = coordinator.prepareRun(
            request = CompatibilityCheckLaunchRequest(
                trainerMacAddress = "AA:BB:CC:DD:EE:FF",
                trainerAlias = "Trainer",
                hasBluetoothConnectPermission = false,
            ),
            resolveRequiresTrainerMessage = { "trainer-required" },
            resolveRequiresPermissionMessage = { "permission-required" },
        )

        assertTrue(result is CompatibilityCheckLaunchPreparationResult.Blocked)
        assertEquals(
            "permission-required",
            (result as CompatibilityCheckLaunchPreparationResult.Blocked).statusMessage,
        )
    }

    @Test
    fun prepareRun_returnsExecutionRequestWithTrimmedAlias() {
        val coordinator = CompatibilityCheckLaunchCoordinator()

        val result = coordinator.prepareRun(
            request = CompatibilityCheckLaunchRequest(
                trainerMacAddress = "AA:BB:CC:DD:EE:FF",
                trainerAlias = "  Trainer Name  ",
                hasBluetoothConnectPermission = true,
            ),
            resolveRequiresTrainerMessage = { "trainer-required" },
            resolveRequiresPermissionMessage = { "permission-required" },
        )

        assertTrue(result is CompatibilityCheckLaunchPreparationResult.Ready)
        val executionRequest =
            (result as CompatibilityCheckLaunchPreparationResult.Ready).executionRequest
        assertEquals("AA:BB:CC:DD:EE:FF", executionRequest.trainerMacAddress)
        assertEquals("Trainer Name", executionRequest.trainerAlias)
    }

    @Test
    fun prepareRun_dropsBlankAliasFromExecutionRequest() {
        val coordinator = CompatibilityCheckLaunchCoordinator()

        val result = coordinator.prepareRun(
            request = CompatibilityCheckLaunchRequest(
                trainerMacAddress = "AA:BB:CC:DD:EE:FF",
                trainerAlias = "   ",
                hasBluetoothConnectPermission = true,
            ),
            resolveRequiresTrainerMessage = { "trainer-required" },
            resolveRequiresPermissionMessage = { "permission-required" },
        )

        assertTrue(result is CompatibilityCheckLaunchPreparationResult.Ready)
        val executionRequest =
            (result as CompatibilityCheckLaunchPreparationResult.Ready).executionRequest
        assertNull(executionRequest.trainerAlias)
    }
}

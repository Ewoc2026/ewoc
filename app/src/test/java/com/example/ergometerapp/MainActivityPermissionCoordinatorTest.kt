package com.example.ergometerapp

import android.Manifest
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityPermissionCoordinatorTest {
    @Test
    fun missingBluetoothConnectPermissionLaunchesRequestOutsideInstrumentation() {
        var requestedPermission: String? = null
        val coordinator = createCoordinator(
            isPermissionGranted = { false },
            requestBluetoothConnectPermission = { permission ->
                requestedPermission = permission
            },
        )

        val granted = coordinator.ensureBluetoothConnectPermission()

        assertFalse(granted)
        assertEquals(Manifest.permission.BLUETOOTH_CONNECT, requestedPermission)
    }

    @Test
    fun missingBluetoothScanPermissionDoesNotLaunchRequestUnderInstrumentation() {
        var requestedPermission: String? = null
        val coordinator = createCoordinator(
            isPermissionGranted = { false },
            isInstrumentationTestRuntime = true,
            requestBluetoothScanPermission = { permission ->
                requestedPermission = permission
            },
        )

        val granted = coordinator.ensureBluetoothScanPermission()

        assertFalse(granted)
        assertNull(requestedPermission)
    }

    @Test
    fun grantedBluetoothScanPermissionReturnsTrueWithoutLaunch() {
        var launched = false
        val coordinator = createCoordinator(
            isPermissionGranted = { permission -> permission == Manifest.permission.BLUETOOTH_SCAN },
            requestBluetoothScanPermission = {
                launched = true
            },
        )

        val granted = coordinator.ensureBluetoothScanPermission()

        assertTrue(granted)
        assertFalse(launched)
    }

    @Test
    fun openAppSettingsTargetsCurrentPackage() {
        var requestedPackageName: String? = null
        var launchedIntent: Intent? = null
        val expectedIntent = Intent("open-settings")
        val coordinator = createCoordinator(
            launchActivity = { intent ->
                launchedIntent = intent
            },
            packageNameProvider = { "io.github.ewoc2026.ewoc" },
            createAppSettingsIntent = { packageName ->
                requestedPackageName = packageName
                expectedIntent
            },
        )

        coordinator.openAppSettings()

        assertEquals("io.github.ewoc2026.ewoc", requestedPackageName)
        assertEquals(expectedIntent, launchedIntent)
    }

    private fun createCoordinator(
        isPermissionGranted: (String) -> Boolean = { true },
        isInstrumentationTestRuntime: Boolean = false,
        requestBluetoothConnectPermission: (String) -> Unit = {},
        requestBluetoothScanPermission: (String) -> Unit = {},
        launchActivity: (Intent) -> Unit = {},
        packageNameProvider: () -> String = { "com.example" },
        createAppSettingsIntent: (String) -> Intent = { Intent("settings") },
    ): MainActivityPermissionCoordinator {
        return MainActivityPermissionCoordinator(
            isPermissionGranted = isPermissionGranted,
            isInstrumentationTestRuntime = isInstrumentationTestRuntime,
            requestBluetoothConnectPermission = requestBluetoothConnectPermission,
            requestBluetoothScanPermission = requestBluetoothScanPermission,
            launchActivity = launchActivity,
            packageNameProvider = packageNameProvider,
            createAppSettingsIntent = createAppSettingsIntent,
        )
    }
}

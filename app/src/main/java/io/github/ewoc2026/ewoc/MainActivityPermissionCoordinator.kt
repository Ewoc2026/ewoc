package io.github.ewoc2026.ewoc

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Owns MainActivity-side permission and settings-launch policy so lifecycle wiring stays thin.
 *
 * Invariants:
 * - Runtime Bluetooth permission requests are no-ops under instrumentation because tests do not
 *   complete the platform dialog flow and would otherwise stall on synthetic launches.
 * - App-settings recovery always targets this app package so connection-issue prompts do not drift
 *   into generic Settings destinations.
 */
internal class MainActivityPermissionCoordinator(
    private val isPermissionGranted: (String) -> Boolean,
    private val isInstrumentationTestRuntime: Boolean,
    private val requestBluetoothConnectPermission: (String) -> Unit,
    private val requestBluetoothScanPermission: (String) -> Unit,
    private val launchActivity: (Intent) -> Unit,
    private val packageNameProvider: () -> String,
    private val createAppSettingsIntent: (String) -> Intent = { packageName ->
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    },
) {

    fun ensureBluetoothConnectPermission(): Boolean {
        if (isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)) {
            return true
        }
        if (isInstrumentationTestRuntime) {
            return false
        }
        requestBluetoothConnectPermission(Manifest.permission.BLUETOOTH_CONNECT)
        return false
    }

    fun ensureBluetoothScanPermission(): Boolean {
        if (isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)) {
            return true
        }
        if (isInstrumentationTestRuntime) {
            return false
        }
        requestBluetoothScanPermission(Manifest.permission.BLUETOOTH_SCAN)
        return false
    }

    fun openAppSettings() {
        launchActivity(createAppSettingsIntent(packageNameProvider()))
    }
}

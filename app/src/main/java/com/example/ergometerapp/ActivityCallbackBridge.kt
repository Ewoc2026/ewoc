package com.example.ergometerapp

import android.app.Activity

/**
 * Owns Activity-bound callbacks that must be rebound across configuration changes.
 *
 * Keeping permission launchers and screen-on hooks behind one bridge prevents those
 * lifecycle-bound references from drifting back into another loose `MainViewModel` var
 * cluster as the Phase 2 hotspot keeps shrinking.
 */
internal class ActivityCallbackBridge {
    private var ensureBluetoothConnectPermissionCallback: (() -> Boolean)? = null
    private var ensureBluetoothScanPermissionCallback: (() -> Boolean)? = null
    private var keepScreenOnCallback: (() -> Unit)? = null
    private var allowScreenOffCallback: (() -> Unit)? = null
    private var currentBillingActivityCallback: (() -> Activity?)? = null

    fun bind(
        ensureBluetoothConnectPermission: () -> Boolean,
        ensureBluetoothScanPermission: () -> Boolean,
        keepScreenOn: () -> Unit,
        allowScreenOff: () -> Unit,
        currentBillingActivity: () -> Activity?,
    ) {
        ensureBluetoothConnectPermissionCallback = ensureBluetoothConnectPermission
        ensureBluetoothScanPermissionCallback = ensureBluetoothScanPermission
        keepScreenOnCallback = keepScreenOn
        allowScreenOffCallback = allowScreenOff
        currentBillingActivityCallback = currentBillingActivity
    }

    fun unbind() {
        ensureBluetoothConnectPermissionCallback = null
        ensureBluetoothScanPermissionCallback = null
        keepScreenOnCallback = null
        allowScreenOffCallback = null
        currentBillingActivityCallback = null
    }

    fun hasBluetoothConnectPermission(defaultWhenUnbound: Boolean = false): Boolean {
        return ensureBluetoothConnectPermissionCallback?.invoke() ?: defaultWhenUnbound
    }

    fun hasBluetoothScanPermission(defaultWhenUnbound: Boolean = false): Boolean {
        return ensureBluetoothScanPermissionCallback?.invoke() ?: defaultWhenUnbound
    }

    fun keepScreenOn() {
        keepScreenOnCallback?.invoke()
    }

    fun allowScreenOff() {
        allowScreenOffCallback?.invoke()
    }

    fun currentBillingActivity(): Activity? = currentBillingActivityCallback?.invoke()
}

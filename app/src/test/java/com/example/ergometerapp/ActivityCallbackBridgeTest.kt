package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityCallbackBridgeTest {

    @Test
    fun bind_exposesCallbacksAndUnbindClearsThem() {
        val bridge = ActivityCallbackBridge()
        var connectPermissionChecks = 0
        var scanPermissionChecks = 0
        var keepScreenOnCalls = 0
        var allowScreenOffCalls = 0

        bridge.bind(
            ensureBluetoothConnectPermission = {
                connectPermissionChecks += 1
                true
            },
            ensureBluetoothScanPermission = {
                scanPermissionChecks += 1
                false
            },
            keepScreenOn = {
                keepScreenOnCalls += 1
            },
            allowScreenOff = {
                allowScreenOffCalls += 1
            },
            currentBillingActivity = { null },
        )

        assertTrue(bridge.hasBluetoothConnectPermission())
        assertFalse(bridge.hasBluetoothScanPermission())

        bridge.keepScreenOn()
        bridge.allowScreenOff()

        assertEquals(1, connectPermissionChecks)
        assertEquals(1, scanPermissionChecks)
        assertEquals(1, keepScreenOnCalls)
        assertEquals(1, allowScreenOffCalls)

        bridge.unbind()

        assertFalse(bridge.hasBluetoothConnectPermission())
        assertTrue(bridge.hasBluetoothConnectPermission(defaultWhenUnbound = true))
        assertFalse(bridge.hasBluetoothScanPermission())
        assertNull(bridge.currentBillingActivity())

        bridge.keepScreenOn()
        bridge.allowScreenOff()

        assertEquals(1, keepScreenOnCalls)
        assertEquals(1, allowScreenOffCalls)
    }
}

package io.github.ewoc2026.ewoc.ble

import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class HrBleClientPermissionTest {

    @Test
    fun initialConnectPermissionDeniedIsSurfacedExplicitly() {
        var permissionDeniedCalls = 0
        var reconnectExhaustedCalls = 0
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        val client = HrBleClient(
            context = context,
            onHeartRate = {},
            onPermissionDenied = { permissionDeniedCalls += 1 },
            onReconnectExhausted = { reconnectExhaustedCalls += 1 },
        )

        val method = HrBleClient::class.java.getDeclaredMethod(
            "connectInternal",
            String::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(client, "AA:BB:CC:DD:EE:FF", false)

        assertEquals(1, permissionDeniedCalls)
        assertEquals(0, reconnectExhaustedCalls)
    }

    @Test
    fun reconnectPermissionDeniedIsSurfacedExplicitlyWithoutExhaustion() {
        var permissionDeniedCalls = 0
        var reconnectExhaustedCalls = 0
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        val client = HrBleClient(
            context = context,
            onHeartRate = {},
            onPermissionDenied = { permissionDeniedCalls += 1 },
            onReconnectExhausted = { reconnectExhaustedCalls += 1 },
        )

        val method = HrBleClient::class.java.getDeclaredMethod(
            "connectInternal",
            String::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(client, "AA:BB:CC:DD:EE:FF", true)

        assertEquals(1, permissionDeniedCalls)
        assertEquals(0, reconnectExhaustedCalls)
    }
}

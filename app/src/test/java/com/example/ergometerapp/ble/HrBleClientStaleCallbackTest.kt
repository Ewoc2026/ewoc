package com.example.ergometerapp.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito

class HrBleClientStaleCallbackTest {
    @Test
    fun staleHeartRateNotificationIsIgnoredWhileActiveConnectionStillUpdates() {
        var heartRateCount = 0
        val client = HrBleClient(
            context = Mockito.mock(Context::class.java),
            onHeartRate = { heartRateCount++ },
        )
        val activeGatt = Mockito.mock(BluetoothGatt::class.java)
        val staleGatt = Mockito.mock(BluetoothGatt::class.java)
        val characteristic = Mockito.mock(BluetoothGattCharacteristic::class.java)
        Mockito.`when`(characteristic.uuid).thenReturn(HR_MEASUREMENT_UUID)
        setActiveGatt(client, activeGatt)
        val callback = getGattCallback(client)

        callback.onCharacteristicChanged(staleGatt, characteristic, byteArrayOf(0x00, 60))
        callback.onCharacteristicChanged(activeGatt, characteristic, byteArrayOf(0x00, 60))

        assertEquals(1, heartRateCount)
    }

    @Test
    fun staleConnectionCallbackClosesOnlyStaleGatt() {
        var disconnectedCount = 0
        val client = HrBleClient(
            context = Mockito.mock(Context::class.java),
            onHeartRate = {},
            onDisconnected = { disconnectedCount++ },
        )
        val activeGatt = Mockito.mock(BluetoothGatt::class.java)
        val staleGatt = Mockito.mock(BluetoothGatt::class.java)
        setActiveGatt(client, activeGatt)
        val callback = getGattCallback(client)

        callback.onConnectionStateChange(
            staleGatt,
            /* status = */ 0,
            BluetoothProfile.STATE_CONNECTED,
        )

        Mockito.verify(staleGatt).close()
        assertEquals(0, disconnectedCount)
        assertSame(activeGatt, getActiveGatt(client))
    }

    private fun getGattCallback(client: HrBleClient): BluetoothGattCallback {
        val field = HrBleClient::class.java.getDeclaredField("gattCallback")
        field.isAccessible = true
        return field.get(client) as BluetoothGattCallback
    }

    private fun setActiveGatt(client: HrBleClient, gatt: BluetoothGatt?) {
        val field = HrBleClient::class.java.getDeclaredField("gatt")
        field.isAccessible = true
        field.set(client, gatt)
    }

    private fun getActiveGatt(client: HrBleClient): BluetoothGatt? {
        val field = HrBleClient::class.java.getDeclaredField("gatt")
        field.isAccessible = true
        return field.get(client) as BluetoothGatt?
    }

    companion object {
        private val HR_MEASUREMENT_UUID: UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    }
}

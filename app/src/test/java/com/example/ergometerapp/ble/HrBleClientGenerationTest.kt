package com.example.ergometerapp.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class HrBleClientGenerationTest {

    @Test
    fun disconnectFiresOncePerGeneration() {
        var disconnectCount = 0
        val client = HrBleClient(
            context = Mockito.mock(Context::class.java),
            onHeartRate = {},
            onDisconnected = { disconnectCount++ },
        )
        val gatt1 = Mockito.mock(BluetoothGatt::class.java)
        val gatt2 = Mockito.mock(BluetoothGatt::class.java)
        val callback = getGattCallback(client)

        setActiveGattAndGeneration(client, gatt1, generation = 1)
        callback.onConnectionStateChange(gatt1, 0, BluetoothProfile.STATE_DISCONNECTED)
        callback.onConnectionStateChange(gatt1, 0, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(1, disconnectCount)

        setActiveGattAndGeneration(client, gatt2, generation = 2)
        callback.onConnectionStateChange(gatt2, 0, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(2, disconnectCount)
    }

    @Test
    fun staleDisconnectAfterReconnectIsIgnored() {
        var disconnectCount = 0
        val client = HrBleClient(
            context = Mockito.mock(Context::class.java),
            onHeartRate = {},
            onDisconnected = { disconnectCount++ },
        )
        val gatt1 = Mockito.mock(BluetoothGatt::class.java)
        val gatt2 = Mockito.mock(BluetoothGatt::class.java)
        val callback = getGattCallback(client)

        setActiveGattAndGeneration(client, gatt1, generation = 1)
        setActiveGattAndGeneration(client, gatt2, generation = 2)

        callback.onConnectionStateChange(gatt1, 0, BluetoothProfile.STATE_DISCONNECTED)
        callback.onConnectionStateChange(gatt2, 0, BluetoothProfile.STATE_DISCONNECTED)

        assertEquals(1, disconnectCount)
    }

    private fun getGattCallback(client: HrBleClient): BluetoothGattCallback {
        val field = HrBleClient::class.java.getDeclaredField("gattCallback")
        field.isAccessible = true
        return field.get(client) as BluetoothGattCallback
    }

    private fun setActiveGattAndGeneration(client: HrBleClient, gatt: BluetoothGatt, generation: Int) {
        setField(client, "gatt", gatt)
        setField(client, "activeGeneration", generation)
        setField(client, "gattGeneration", generation)
        setField(client, "disconnectEmittedGeneration", -1)
    }

    private fun setField(target: Any, name: String, value: Any?) {
        val field = target::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
}

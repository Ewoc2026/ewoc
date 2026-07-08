package io.github.ewoc2026.ewoc.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.ewoc2026.ewoc.BluetoothMacAddress
import java.util.UUID

/**
 * BLE client for the standard Heart Rate Service (0x180D).
 *
 * The HR Measurement characteristic is notify-only; the client must enable the
 * CCCD and then parse notifications according to the flag byte.
 */
class HrBleClient(
    private val context: Context,
    private val onHeartRate: (Int) -> Unit,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onPermissionDenied: () -> Unit = {},
    private val onReconnectExhausted: () -> Unit = {},
    private val reconnectPolicy: HrReconnectPolicy = HrReconnectPolicy(),
) {
    private var gatt: BluetoothGatt? = null
    private var notificationsReady = false
    private var activeGeneration = 0
    private var gattGeneration = 0
    private var disconnectEmittedGeneration = -1
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private val reconnectCoordinator = HrReconnectCoordinator(
        handler = mainThreadHandler,
        onReconnectRequested = { mac -> connectInternal(mac, isReconnect = true) },
        onDisconnected = { mainThreadHandler.post { onDisconnected() } },
        onReconnectExhausted = { mainThreadHandler.post { onReconnectExhausted() } },
        reconnectPolicy = reconnectPolicy,
    )

    private val HR_SERVICE_UUID =
        UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HR_MEASUREMENT_UUID =
        UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CCC_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (gatt != this@HrBleClient.gatt) {
                Log.d("HR", "Ignoring stale connection callback state=$newState status=$status")
                safeCloseGatt(gatt, source = "staleConnectionCallback")
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                notificationsReady = false
                if (!hasBluetoothConnectPermission()) {
                    abortSetupForPermissionDenied(
                        gatt = gatt,
                        reason = "Missing BLUETOOTH_CONNECT permission; cannot discover HR services",
                    )
                    return
                }
                try {
                    val discoveryStarted = gatt.discoverServices()
                    if (!discoveryStarted) {
                        abortSetup(
                            gatt = gatt,
                            reason = "HR service discovery failed to start",
                        )
                    }
                } catch (e: SecurityException) {
                    abortSetup(
                        gatt = gatt,
                        reason = "discoverServices failed: ${e.message}",
                    )
                }
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                notificationsReady = false
                if (disconnectEmittedGeneration == gattGeneration) {
                    Log.d("HR", "Ignoring duplicate disconnect for generation=$gattGeneration")
                    safeCloseGatt(gatt, source = "onConnectionStateChangeDuplicate")
                    if (this@HrBleClient.gatt === gatt) {
                        this@HrBleClient.gatt = null
                    }
                    return
                }
                disconnectEmittedGeneration = gattGeneration
                safeCloseGatt(gatt, source = "onConnectionStateChange")
                if (this@HrBleClient.gatt === gatt) {
                    this@HrBleClient.gatt = null
                }
                reconnectCoordinator.onGattDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt != this@HrBleClient.gatt) {
                Log.d("HR", "Ignoring stale services-discovered callback status=$status")
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                abortSetup(gatt = gatt, reason = "HR service discovery failed (status=$status)")
                return
            }
            if (!hasBluetoothConnectPermission()) {
                abortSetupForPermissionDenied(
                    gatt = gatt,
                    reason = "Missing BLUETOOTH_CONNECT permission; cannot configure HR notifications",
                )
                return
            }
            val service = gatt.getService(HR_SERVICE_UUID)
            if (service == null) {
                abortSetup(gatt = gatt, reason = "Heart Rate service not found")
                return
            }
            val ch = service.getCharacteristic(HR_MEASUREMENT_UUID)
            if (ch == null) {
                abortSetup(gatt = gatt, reason = "Heart Rate Measurement characteristic not found")
                return
            }
            val ccc = ch.getDescriptor(CCC_UUID)
            if (ccc == null) {
                abortSetup(gatt = gatt, reason = "Heart Rate CCCD descriptor not found")
                return
            }

            try {
                val localNotifyEnabled = gatt.setCharacteristicNotification(ch, true)
                if (!localNotifyEnabled) {
                    abortSetup(
                        gatt = gatt,
                        reason = "setCharacteristicNotification failed for Heart Rate Measurement",
                    )
                    return
                }

                val writeStatus =
                    gatt.writeDescriptor(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                if (writeStatus != BluetoothStatusCodes.SUCCESS) {
                    abortSetup(
                        gatt = gatt,
                        reason = "Writing Heart Rate CCCD failed to start (status=$writeStatus)",
                    )
                }
            } catch (e: SecurityException) {
                abortSetup(gatt = gatt, reason = "Configuring HR notifications failed: ${e.message}")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (gatt != this@HrBleClient.gatt) {
                Log.d("HR", "Ignoring stale descriptor callback status=$status")
                return
            }
            if (descriptor.uuid != CCC_UUID) return
            if (descriptor.characteristic.uuid != HR_MEASUREMENT_UUID) return

            if (status != BluetoothGatt.GATT_SUCCESS) {
                abortSetup(gatt = gatt, reason = "Heart Rate CCCD write failed (status=$status)")
                return
            }

            if (notificationsReady) return
            notificationsReady = true
            disconnectEmittedGeneration = -1
            reconnectCoordinator.onConnected()
            mainThreadHandler.post { onConnected() }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (gatt != this@HrBleClient.gatt) {
                Log.d("HR", "Ignoring stale characteristic callback")
                return
            }
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                val bpm = HrMeasurementParser.parseBpm(value) ?: return
                mainThreadHandler.post { onHeartRate(bpm) }
            }
        }

    }

    /**
     * Connects to a Heart Rate peripheral by MAC address.
     *
     * This uses the platform GATT stack; callers must ensure Bluetooth is enabled
     * and the device is bonded if required by the peripheral.
     */
    fun connect(mac: String) {
        require(BluetoothAdapter.checkBluetoothAddress(mac)) {
            "Invalid Bluetooth MAC address: ${BluetoothMacAddress.redactForLogs(mac)}"
        }
        reconnectCoordinator.onConnectRequested(mac)
        connectInternal(mac, isReconnect = false)
    }
    @Suppress("unused")
    /**
     * Releases the GATT connection.
     *
     * Always safe to call; exceptions can occur if permissions were revoked.
     */
    fun close() {
        reconnectCoordinator.onCloseRequested()
        notificationsReady = false
        try {
            gatt?.close()
        } catch (e: SecurityException) {
            Log.w("HR", "close failed: ${e.message}")
        }
        gatt = null
    }

    private fun connectInternal(mac: String, isReconnect: Boolean) {
        activeGeneration += 1
        gattGeneration = activeGeneration
        disconnectEmittedGeneration = -1
        if (!hasBluetoothConnectPermission()) {
            Log.w("HR", "Missing BLUETOOTH_CONNECT permission; cannot connect")
            reconnectCoordinator.onPermissionDenied()
            mainThreadHandler.post { onPermissionDenied() }
            return
        }

        try {
            gatt?.close()
        } catch (e: SecurityException) {
            Log.w("HR", "close before connect failed: ${e.message}")
        }
        gatt = null
        notificationsReady = false

        try {
            val bluetoothManager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            val adapter = bluetoothManager.adapter
            val device = adapter.getRemoteDevice(mac)
            val newGatt = device.connectGatt(context, false, gattCallback)
            if (newGatt == null) {
                Log.w("HR", "connectGatt returned null")
                reconnectCoordinator.onConnectAttemptFailed()
                return
            }
            gatt = newGatt
        } catch (e: SecurityException) {
            Log.w("HR", "connectGatt failed: ${e.message}")
            reconnectCoordinator.onConnectAttemptFailed()
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Aborts notification setup via one deterministic reconnect/disconnect path.
     *
     * Invariant: setup failures are never silent. Every failure attempts to tear
     * down the link and schedules reconnect policy through the coordinator.
     */
    private fun abortSetup(gatt: BluetoothGatt, reason: String) {
        if (gatt != this.gatt) {
            Log.d("HR", "Ignoring setup-abort for stale GATT: $reason")
            return
        }
        Log.w("HR", reason)
        notificationsReady = false
        reconnectCoordinator.onConnectAttemptFailed()
        try {
            gatt.disconnect()
        } catch (e: SecurityException) {
            Log.w("HR", "disconnect failed during setup abort: ${e.message}")
            safeCloseGatt(gatt, source = "abortSetup")
            if (this.gatt === gatt) {
                this.gatt = null
            }
        }
    }

    private fun abortSetupForPermissionDenied(gatt: BluetoothGatt, reason: String) {
        if (gatt != this.gatt) {
            Log.d("HR", "Ignoring permission-denied setup abort for stale GATT: $reason")
            return
        }
        Log.w("HR", reason)
        notificationsReady = false
        reconnectCoordinator.onPermissionDenied()
        mainThreadHandler.post { onPermissionDenied() }
        safeCloseGatt(gatt, source = "permissionDeniedSetupAbort")
        if (this.gatt === gatt) {
            this.gatt = null
        }
    }

    private fun safeCloseGatt(gatt: BluetoothGatt, source: String) {
        try {
            gatt.close()
        } catch (e: SecurityException) {
            Log.w("HR", "gatt.close failed from $source: ${e.message}")
        }
    }
}

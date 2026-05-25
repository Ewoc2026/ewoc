package com.example.ergometerapp.ble

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
import android.os.SystemClock
import android.util.Log
import com.example.ergometerapp.BuildConfig
import com.example.ergometerapp.BluetoothMacAddress
import com.example.ergometerapp.logging.AppLog
import java.util.UUID

/**
 * BLE client for the Fitness Machine Service (FTMS, 0x1826).
 *
 * This client enables notifications/indications in a strict order to satisfy
 * GATT's single-operation-at-a-time constraint and FTMS expectations for
 * Control Point acknowledgements. It also centralizes reconnect strategy and
 * FTMS Control Point ownership state so callers can react to stable events.
 */

class FtmsBleClient(
    private val context: Context,
    private val onIndoorBikeData: (ByteArray) -> Unit,
    private val onReady: (Boolean, Int) -> Unit,
    private val onControlPointResponse: (Int, Int, Int) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onControlOwnershipChanged: (Boolean) -> Unit = {}
) {
    companion object {
        private const val CONTROL_POINT_RESPONSE_OPCODE = 0x80
        private const val CONTROL_POINT_OPCODE_REQUEST_CONTROL = 0x00
        private const val CONTROL_POINT_OPCODE_RESET = 0x01
        private const val CONTROL_POINT_RESULT_SUCCESS = 0x01
        private const val MAX_RECONNECT_ATTEMPTS = 4
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 8000L
        private const val INDOOR_BIKE_MAIN_THREAD_UPDATE_INTERVAL_MS = 200L
        private const val INDOOR_BIKE_DEBUG_LOG_WINDOW_MS = 5000L
    }

    private var gatt: BluetoothGatt? = null
    private var controlPointCharacteristic: BluetoothGattCharacteristic? = null
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    private var indoorBikeDataCharacteristic: BluetoothGattCharacteristic? = null
    private var controlOwnershipGranted = false

    @Volatile
    private var disconnectEventEmitted = false

    private enum class SetupStep { NONE, CP_CCCD, BIKE_CCCD }
    private var setupStep: SetupStep = SetupStep.NONE
    private var controlPointIndicationEnabled = false
    private var activeLinkGeneration = 0
    private var requestedMac: String? = null
    private var explicitCloseRequested = false
    private var reconnectAttempt = 0
    private var reconnectRunnable: Runnable? = null
    private var disconnectHandlingInProgress = false
    private val indoorBikeDispatchLock = Any()
    private var pendingIndoorBikeData: ByteArray? = null
    private var indoorBikeDispatchScheduled = false
    private var lastIndoorBikeDispatchElapsedMs = 0L
    private var indoorBikeNotificationCount = 0
    private var indoorBikeNotificationLogWindowStartElapsedMs = 0L
    private val indoorBikeDispatchRunnable = Runnable { dispatchLatestIndoorBikeData() }

    private val FTMS_SERVICE_UUID =
        UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val INDOOR_BIKE_DATA_UUID =
        UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")

    private val FTMS_CONTROL_POINT_UUID =
        UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")

    private val CCCD_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    handleGattConnected(gatt)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handleLinkLoss(
                        disconnectedGatt = gatt,
                        reason = "GATT disconnected (status=$status)",
                    )
                }
                else -> {
                    debugLog("Ignoring unsupported connection state=$newState status=$status")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (!isActiveGatt(gatt)) {
                debugLog("Ignoring descriptor write callback from stale GATT")
                return
            }
            debugLog("onDescriptorWrite step=$setupStep status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                abortSetup(
                    gatt = gatt,
                    reason = "Descriptor write failed at step=$setupStep status=$status",
                )
                return
            }

            when (setupStep) {
                SetupStep.CP_CCCD -> {
                    controlPointIndicationEnabled = true
                    writeBikeCccd(gatt)
                }
                SetupStep.BIKE_CCCD -> {
                    setupStep = SetupStep.NONE
                    val controlPointReady =
                        controlPointCharacteristic != null && controlPointIndicationEnabled
                    debugLog("FTMS notifications/indications setup done (controlPointReady=$controlPointReady)")
                    val linkGeneration = activeLinkGeneration
                    mainThreadHandler.post { onReady(controlPointReady, linkGeneration) }
                }
                else -> {
                    // ignore
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!isActiveGatt(gatt)) {
                debugLog("Ignoring services discovered callback from stale GATT")
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                abortSetup(
                    gatt = gatt,
                    reason = "Service discovery failed (status=$status)",
                )
                return
            }
            if (!hasBluetoothConnectPermission()) {
                abortSetup(
                    gatt = gatt,
                    reason = "Missing BLUETOOTH_CONNECT permission; cannot configure FTMS",
                )
                return
            }
            val service = gatt.getService(FTMS_SERVICE_UUID) ?: run {
                abortSetup(gatt = gatt, reason = "FTMS service not found")
                return
            }

            indoorBikeDataCharacteristic =
                service.getCharacteristic(INDOOR_BIKE_DATA_UUID)

            controlPointCharacteristic =
                service.getCharacteristic(FTMS_CONTROL_POINT_UUID)

            if (controlPointCharacteristic == null) {
                abortSetup(gatt = gatt, reason = "Control Point characteristic not found")
                return
            }
            debugLog("Control Point ready")
            val controlPoint = controlPointCharacteristic ?: run {
                abortSetup(gatt = gatt, reason = "Control Point characteristic missing during setup")
                return
            }

            if (indoorBikeDataCharacteristic == null) {
                abortSetup(gatt = gatt, reason = "Indoor Bike Data characteristic not found")
                return
            }

            // Local enablement is required before writing CCCD values on some stacks.
            try {
                gatt.setCharacteristicNotification(indoorBikeDataCharacteristic, true)
            } catch (e: SecurityException) {
                abortSetup(
                    gatt = gatt,
                    reason = "setCharacteristicNotification (Bike Data) failed: ${e.message}",
                )
                return
            }

            // Control Point uses indications for reliable acknowledgements.
            try {
                gatt.setCharacteristicNotification(controlPoint, true)
            } catch (e: SecurityException) {
                abortSetup(
                    gatt = gatt,
                    reason = "setCharacteristicNotification (Control Point) failed: ${e.message}",
                )
                return
            }

            writeControlPointCccd(gatt, controlPoint)
        }

        private fun handleCharacteristicChanged(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (this@FtmsBleClient.gatt == null) {
                debugLog("Notification ignored (GATT closed)")
                return
            }
            when (characteristic.uuid) {
                INDOOR_BIKE_DATA_UUID -> queueIndoorBikeDataForMainThread(value)
                FTMS_CONTROL_POINT_UUID -> {
                    debugLog("Control Point response ${controlPointPayloadSummary(value)}")

                    // FTMS: Response Code (0x80), then request opcode and result code.
                    if (value.size >= 3 && (value[0].toInt() and 0xFF) == CONTROL_POINT_RESPONSE_OPCODE) {
                        val requestOpcode = value[1].toInt() and 0xFF
                        val resultCode = value[2].toInt() and 0xFF
                        updateControlOwnershipFromResponse(requestOpcode, resultCode)
                        val linkGeneration = activeLinkGeneration
                        mainThreadHandler.post {
                            onControlPointResponse(requestOpcode, resultCode, linkGeneration)
                        }
                    }
                }

            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (!isActiveGatt(gatt)) {
                debugLog("Ignoring characteristic callback from stale GATT")
                return
            }
            handleCharacteristicChanged(characteristic, value)
        }

    }

    private fun controlPointPayloadSummary(payload: ByteArray): String {
        val opcode = payload.firstOrNull()?.toInt()?.and(0xFF)
        return "opcode=${opcode ?: "none"} size=${payload.size}"
    }

    /**
     * Connects to a Fitness Machine peripheral by MAC address.
     *
     * Callers should wait for [onReady] before issuing Control Point commands.
     */
    fun connect(mac: String) {
        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            Log.w("FTMS", "Invalid Bluetooth MAC address: ${BluetoothMacAddress.redactForLogs(mac)}")
            return
        }
        requestedMac = mac
        explicitCloseRequested = false
        reconnectAttempt = 0
        disconnectEventEmitted = false
        disconnectHandlingInProgress = false
        cancelPendingReconnect()

        if (gatt != null) {
            debugLog("connect ignored (already connected or connecting)")
            return
        }
        if (!startGattConnection(mac = mac, isReconnect = false)) {
            handleLinkLoss(
                disconnectedGatt = null,
                reason = "Initial connect failed for selected device",
            )
        }
    }

    @Suppress("unused")
    /**
     * Releases the GATT connection.
     *
     * Safe to call multiple times; exceptions can occur if permissions change.
     */
    fun close(): Boolean {
        explicitCloseRequested = true
        requestedMac = null
        reconnectAttempt = 0
        disconnectHandlingInProgress = false
        cancelPendingReconnect()
        clearPendingIndoorBikeDispatch()
        setControlOwnership(granted = false, reason = "closeRequested")

        val currentGatt = gatt ?: return false
        try {
            currentGatt.disconnect()
        } catch (e: SecurityException) {
            Log.w("FTMS", "disconnect failed: ${e.message}")
        }
        return true
    }


    /**
     * Emits a single semantic disconnect event per connection lifecycle.
     *
     * Android can surface multiple low-level teardown signals (explicit close,
     * callback disconnect, setup failure). Callers should observe one stable
     * disconnect transition regardless of source.
     */
    private fun emitDisconnectedOnce() {
        synchronized(this) {
            if (disconnectEventEmitted) return
            disconnectEventEmitted = true
        }
        disconnectHandlingInProgress = false
        mainThreadHandler.post { onDisconnected() }
    }

    private fun isActiveGatt(gatt: BluetoothGatt): Boolean {
        return this.gatt === gatt
    }

    private fun handleGattConnected(gatt: BluetoothGatt) {
        val activeGatt = this.gatt
        if (activeGatt != null && activeGatt !== gatt) {
            debugLog("Ignoring connected callback from stale GATT")
            safeCloseGatt(gatt, "staleConnected")
            return
        }

        this.gatt = gatt
        activeLinkGeneration = nextLinkGeneration()
        disconnectEventEmitted = false
        disconnectHandlingInProgress = false
        reconnectAttempt = 0
        cancelPendingReconnect()
        resetSetupState()
        setControlOwnership(granted = false, reason = "linkConnected")

        if (!hasBluetoothConnectPermission()) {
            abortSetup(
                gatt = gatt,
                reason = "Missing BLUETOOTH_CONNECT permission; cannot discover services",
            )
            return
        }
        try {
            gatt.discoverServices()
        } catch (e: SecurityException) {
            abortSetup(
                gatt = gatt,
                reason = "discoverServices failed: ${e.message}",
            )
        }
    }

    private fun handleLinkLoss(disconnectedGatt: BluetoothGatt?, reason: String) {
        val activeGatt = gatt
        if (disconnectedGatt != null && activeGatt != null && disconnectedGatt !== activeGatt) {
            debugLog("Ignoring disconnect path for stale GATT")
            safeCloseGatt(disconnectedGatt, "staleDisconnect")
            return
        }

        synchronized(this) {
            if (disconnectHandlingInProgress) {
                debugLog("Duplicate disconnect handling ignored")
                return
            }
            disconnectHandlingInProgress = true
        }

        Log.w("FTMS", reason)
        resetSetupState()
        setControlOwnership(granted = false, reason = "linkLost")
        val linkGeneration = activeLinkGeneration
        mainThreadHandler.post { onReady(false, linkGeneration) }

        if (disconnectedGatt != null) {
            safeCloseGatt(disconnectedGatt, "linkLossCleanup")
        } else {
            safeCloseGatt(activeGatt, "linkLossCleanupActive")
        }
        gatt = null

        if (scheduleReconnectIfNeeded(triggerReason = reason)) {
            return
        }
        emitDisconnectedOnce()
    }

    private fun startGattConnection(mac: String, isReconnect: Boolean): Boolean {
        if (!hasBluetoothConnectPermission()) {
            Log.w("FTMS", "Missing BLUETOOTH_CONNECT permission; cannot connect")
            return false
        }
        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            Log.w("FTMS", "Invalid Bluetooth MAC address: ${BluetoothMacAddress.redactForLogs(mac)}")
            return false
        }
        if (gatt != null) {
            debugLog("startGattConnection ignored (already connected or connecting)")
            return false
        }

        return try {
            val bluetoothManager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            val adapter = bluetoothManager.adapter
            val device = adapter.getRemoteDevice(mac)
            val newGatt = device.connectGatt(context, false, gattCallback)
            if (newGatt == null) {
                Log.w("FTMS", "connectGatt returned null; connection not started")
                false
            } else {
                gatt = newGatt
                disconnectHandlingInProgress = false
                val redactedMac = BluetoothMacAddress.redactForLogs(mac)
                debugLog(
                    if (isReconnect) "Reconnect started for $redactedMac" else "connectGatt started for $redactedMac",
                )
                true
            }
        } catch (e: SecurityException) {
            Log.w("FTMS", "connectGatt failed: ${e.message}")
            false
        }
    }

    private fun scheduleReconnectIfNeeded(triggerReason: String): Boolean {
        if (explicitCloseRequested) {
            debugLog("Reconnect disabled due to explicit close")
            return false
        }
        val mac = requestedMac ?: return false
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.w("FTMS", "Reconnect exhausted after $reconnectAttempt attempts")
            return false
        }
        if (!hasBluetoothConnectPermission()) {
            Log.w("FTMS", "Reconnect skipped: missing BLUETOOTH_CONNECT permission")
            return false
        }

        val attempt = reconnectAttempt + 1
        reconnectAttempt = attempt
        val delayMs = reconnectDelayMs(attempt)
        Log.w(
            "FTMS",
            "Scheduling reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms " +
                "(reason=$triggerReason)",
        )
        val runnable = Runnable {
            reconnectRunnable = null
            if (explicitCloseRequested) {
                debugLog("Reconnect cancelled by explicit close")
                return@Runnable
            }
            if (requestedMac != mac) {
                debugLog("Reconnect cancelled due to target MAC change")
                return@Runnable
            }
            disconnectHandlingInProgress = false
            if (!startGattConnection(mac = mac, isReconnect = true)) {
                handleLinkLoss(
                    disconnectedGatt = null,
                    reason = "Reconnect attempt $attempt failed for selected device",
                )
            }
        }
        reconnectRunnable = runnable
        mainThreadHandler.postDelayed(runnable, delayMs)
        return true
    }

    private fun reconnectDelayMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceAtLeast(0)
        val factor = 1L shl shift
        val delay = RECONNECT_BASE_DELAY_MS * factor
        return delay.coerceAtMost(RECONNECT_MAX_DELAY_MS)
    }

    private fun cancelPendingReconnect() {
        reconnectRunnable?.let { mainThreadHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    /**
     * Monotonic link generation used to scope readiness and response ownership.
     */
    private fun nextLinkGeneration(): Int {
        if (activeLinkGeneration == Int.MAX_VALUE) {
            activeLinkGeneration = 1
            return activeLinkGeneration
        }
        activeLinkGeneration += 1
        return activeLinkGeneration
    }

    private fun resetSetupState() {
        setupStep = SetupStep.NONE
        controlPointIndicationEnabled = false
        indoorBikeDataCharacteristic = null
        controlPointCharacteristic = null
        clearPendingIndoorBikeDispatch()
    }

    private fun safeCloseGatt(gatt: BluetoothGatt?, reason: String) {
        if (gatt == null) return
        try {
            gatt.close()
        } catch (e: SecurityException) {
            Log.w("FTMS", "close failed ($reason): ${e.message}")
        }
    }

    private fun setControlOwnership(granted: Boolean, reason: String) {
        if (controlOwnershipGranted == granted) return
        controlOwnershipGranted = granted
        debugLog("Control ownership changed granted=$granted reason=$reason")
        mainThreadHandler.post { onControlOwnershipChanged(granted) }
    }

    private fun updateControlOwnershipFromResponse(requestOpcode: Int, resultCode: Int) {
        when (requestOpcode) {
            CONTROL_POINT_OPCODE_REQUEST_CONTROL -> {
                setControlOwnership(
                    granted = resultCode == CONTROL_POINT_RESULT_SUCCESS,
                    reason = "requestControlResponse(result=$resultCode)",
                )
            }
            CONTROL_POINT_OPCODE_RESET -> {
                if (resultCode == CONTROL_POINT_RESULT_SUCCESS) {
                    setControlOwnership(
                        granted = false,
                        reason = "resetResponse(result=$resultCode)",
                    )
                }
            }
        }
    }

    private fun writeControlPointCccd(gatt: BluetoothGatt, cp: BluetoothGattCharacteristic) {
        if (!hasBluetoothConnectPermission()) {
            abortSetup(
                gatt = gatt,
                reason = "Missing BLUETOOTH_CONNECT permission; cannot write CP CCCD",
            )
            return
        }
        val cccd = cp.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            abortSetup(gatt = gatt, reason = "CCCD not found for Control Point")
            return
        }

        setupStep = SetupStep.CP_CCCD
        try {
            val status = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            debugLog("Writing CP CCCD (indication) -> status=$status")
            if (status != BluetoothStatusCodes.SUCCESS) {
                abortSetup(gatt = gatt, reason = "Writing CP CCCD failed to start")
                return
            }
        } catch (e: SecurityException) {
            abortSetup(gatt = gatt, reason = "writeDescriptor (CP CCCD) failed: ${e.message}")
        }
    }

    private fun writeBikeCccd(gatt: BluetoothGatt) {
        if (!hasBluetoothConnectPermission()) {
            abortSetup(
                gatt = gatt,
                reason = "Missing BLUETOOTH_CONNECT permission; cannot write Bike CCCD",
            )
            return
        }
        val ch = indoorBikeDataCharacteristic ?: run {
            abortSetup(gatt = gatt, reason = "Bike characteristic missing when writing CCCD")
            return
        }

        val cccd = ch.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            abortSetup(gatt = gatt, reason = "CCCD not found for Indoor Bike Data")
            return
        }

        setupStep = SetupStep.BIKE_CCCD
        try {
            val status = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            debugLog("Writing BIKE CCCD (notification) -> status=$status")
            if (status != BluetoothStatusCodes.SUCCESS) {
                abortSetup(gatt = gatt, reason = "Writing Bike CCCD failed to start")
                return
            }
        } catch (e: SecurityException) {
            abortSetup(gatt = gatt, reason = "writeDescriptor (Bike CCCD) failed: ${e.message}")
        }
    }

    /**
     * Aborts FTMS setup through one deterministic teardown path.
     *
     * Setup failures can happen from multiple callbacks and API calls. This path
     * always clears transient setup state and routes control to the centralized
     * disconnect policy, which either schedules reconnect or emits a terminal
     * disconnect signal to the app layer.
     */
    private fun abortSetup(gatt: BluetoothGatt, reason: String) {
        try {
            gatt.disconnect()
        } catch (e: SecurityException) {
            Log.w("FTMS", "disconnect failed during setup abort: ${e.message}")
        }
        handleLinkLoss(disconnectedGatt = gatt, reason = reason)
    }

    /**
     * Writes a raw FTMS Control Point payload.
     *
     * FTMS devices are allowed to reject commands until they have granted control
     * and Control Point indications are enabled.
     */
    fun writeControlPoint(payload: ByteArray): Boolean {
        if (!hasBluetoothConnectPermission()) {
            Log.w("FTMS", "Missing BLUETOOTH_CONNECT permission; cannot write Control Point")
            return false
        }
        if (payload.isEmpty()) {
            Log.w("FTMS", "writeControlPoint ignored (empty payload)")
            return false
        }

        val opcode = payload[0].toInt() and 0xFF
        val requiresControlOwnership = opcode != CONTROL_POINT_OPCODE_REQUEST_CONTROL
        if (requiresControlOwnership && !controlOwnershipGranted) {
            Log.w(
                "FTMS",
                "writeControlPoint ignored (control not granted) ${controlPointPayloadSummary(payload)}",
            )
            return false
        }

        val currentGatt = gatt
        val characteristic = controlPointCharacteristic

        if (currentGatt == null || characteristic == null) {
            Log.w("FTMS", "writeControlPoint called but not ready")
            return false
        }

        try {
            val status = currentGatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            val started = status == BluetoothStatusCodes.SUCCESS
            debugLog(
                "writeControlPoint ${controlPointPayloadSummary(payload)} -> " +
                    "status=$status started=$started"
            )
            return started
        } catch (e: SecurityException) {
            Log.w("FTMS", "writeCharacteristic failed: ${e.message}")
            return false
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Coalesces high-frequency bike telemetry so UI/session observers are updated
     * at a stable main-thread cadence without dropping the latest sample.
     */
    private fun queueIndoorBikeDataForMainThread(value: ByteArray) {
        val snapshot = value.copyOf()
        val delayMs = synchronized(indoorBikeDispatchLock) {
            pendingIndoorBikeData = snapshot
            maybeLogIndoorBikeNotificationRateLocked()

            if (indoorBikeDispatchScheduled) {
                return@synchronized -1L
            }
            indoorBikeDispatchScheduled = true

            val nowMs = SystemClock.elapsedRealtime()
            val earliestDispatchAtMs =
                lastIndoorBikeDispatchElapsedMs + INDOOR_BIKE_MAIN_THREAD_UPDATE_INTERVAL_MS
            (earliestDispatchAtMs - nowMs).coerceAtLeast(0L)
        }

        if (delayMs < 0L) return
        if (delayMs == 0L) {
            mainThreadHandler.post(indoorBikeDispatchRunnable)
        } else {
            mainThreadHandler.postDelayed(indoorBikeDispatchRunnable, delayMs)
        }
    }

    private fun dispatchLatestIndoorBikeData() {
        val payload = synchronized(indoorBikeDispatchLock) {
            indoorBikeDispatchScheduled = false
            val latest = pendingIndoorBikeData
            pendingIndoorBikeData = null
            if (latest != null) {
                lastIndoorBikeDispatchElapsedMs = SystemClock.elapsedRealtime()
            }
            latest
        } ?: return

        if (gatt == null) {
            return
        }
        onIndoorBikeData(payload)
    }

    private fun maybeLogIndoorBikeNotificationRateLocked() {
        if (!BuildConfig.VERBOSE_TELEMETRY_LOGGING) return

        val nowMs = SystemClock.elapsedRealtime()
        if (indoorBikeNotificationLogWindowStartElapsedMs == 0L) {
            indoorBikeNotificationLogWindowStartElapsedMs = nowMs
        }
        indoorBikeNotificationCount += 1

        val elapsedMs = nowMs - indoorBikeNotificationLogWindowStartElapsedMs
        if (elapsedMs < INDOOR_BIKE_DEBUG_LOG_WINDOW_MS) return

        debugLog(
            "IndoorBikeData callbacks: $indoorBikeNotificationCount in ${elapsedMs}ms " +
                "(coalesced to <=${1000 / INDOOR_BIKE_MAIN_THREAD_UPDATE_INTERVAL_MS}Hz main-thread updates)"
        )
        indoorBikeNotificationCount = 0
        indoorBikeNotificationLogWindowStartElapsedMs = nowMs
    }

    private fun clearPendingIndoorBikeDispatch() {
        synchronized(indoorBikeDispatchLock) {
            pendingIndoorBikeData = null
            indoorBikeDispatchScheduled = false
            indoorBikeNotificationCount = 0
            indoorBikeNotificationLogWindowStartElapsedMs = 0L
            lastIndoorBikeDispatchElapsedMs = 0L
        }
        mainThreadHandler.removeCallbacks(indoorBikeDispatchRunnable)
    }

    private fun debugLog(message: String) {
        AppLog.telemetryDebug("FTMS") { message }
    }

}

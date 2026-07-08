package io.github.ewoc2026.ewoc

import android.os.Handler
import android.os.Looper
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceScanCoordinatorTest {
    private val ftmsUuid: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val hrUuid: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    @Test
    fun requestScanStartsFlowWithExpectedCallbackOrder() {
        val events = mutableListOf<String>()
        val ui = FakeUiPort()
        val engine = FakeScanEngine(
            plans = ArrayDeque(
                listOf(
                    ScanPlan(started = true, finishedWithError = null),
                ),
            ),
            onStart = {
                events += "engine.start"
            },
        )

        val coordinator = buildCoordinator(
            uiPort = ui,
            scanEngine = engine,
            ensurePermission = { true },
            onBeforeScanRequest = { events += "before.request" },
            onBeforeScanStart = { events += "before.start" },
        )

        coordinator.requestScan(DeviceSelectionKind.FTMS)

        assertEquals(listOf("before.request", "before.start", "engine.start"), events)
        assertEquals(DeviceSelectionKind.FTMS, ui.activeSelectionKind)
        assertFalse(ui.scanInProgress)
        assertEquals("no_results", ui.scanStatus)
    }

    @Test
    fun permissionResultStartsPendingScanWhenPermissionArrivesLater() {
        val events = mutableListOf<String>()
        val ui = FakeUiPort()
        val engine = FakeScanEngine(
            plans = ArrayDeque(
                listOf(
                    ScanPlan(started = true, finishedWithError = null),
                ),
            ),
            onStart = {
                events += "engine.start"
            },
        )
        val coordinator = buildCoordinator(
            uiPort = ui,
            scanEngine = engine,
            ensurePermission = { false },
            onBeforeScanRequest = { kind -> events += "before.request:$kind" },
            onBeforeScanStart = { events += "before.start" },
        )

        coordinator.requestScan(DeviceSelectionKind.HEART_RATE)
        assertEquals(listOf("before.request:HEART_RATE"), events)
        assertEquals(0, engine.startCalls.size)

        val result = coordinator.onBluetoothScanPermissionResult(granted = true)

        assertEquals(ScanPermissionResult.STARTED_PENDING_SCAN, result)
        assertEquals(
            listOf("before.request:HEART_RATE", "before.start", "engine.start"),
            events,
        )
        assertEquals(1, engine.startCalls.size)
        assertEquals(hrUuid, engine.startCalls.first().serviceUuid)
    }

    @Test
    fun permissionResultDeniedAfterPendingScanDoesNotStartScan() {
        val events = mutableListOf<String>()
        val ui = FakeUiPort()
        val engine = FakeScanEngine(
            plans = ArrayDeque(),
            onStart = {
                events += "engine.start"
            },
        )
        val coordinator = buildCoordinator(
            uiPort = ui,
            scanEngine = engine,
            ensurePermission = { false },
            onBeforeScanRequest = { kind -> events += "before.request:$kind" },
            onBeforeScanStart = { events += "before.start" },
        )

        coordinator.requestScan(DeviceSelectionKind.FTMS)

        val result = coordinator.onBluetoothScanPermissionResult(granted = false)

        assertEquals(ScanPermissionResult.DENIED, result)
        assertEquals(listOf("before.request:FTMS"), events)
        assertEquals(0, engine.startCalls.size)
        assertFalse(ui.scanInProgress)
        assertEquals("permission_required", ui.scanStatus)
    }

    @Test
    fun retryTooFrequentRunsSingleAutomaticRetry() {
        val handler = ControlledHandler()
        val ui = FakeUiPort()
        val engine = FakeScanEngine(
            plans = ArrayDeque(
                listOf(
                    ScanPlan(started = true, finishedWithError = "Scan failed (code=6)."),
                    ScanPlan(started = true, finishedWithError = null),
                ),
            ),
        )

        val coordinator = buildCoordinator(
            uiPort = ui,
            scanEngine = engine,
            handler = handler,
            ensurePermission = { true },
        )

        coordinator.requestScan(DeviceSelectionKind.FTMS)
        assertEquals("retrying", ui.scanStatus)
        assertEquals(1, engine.startCalls.size)

        handler.runAllDelayed()

        assertEquals(2, engine.startCalls.size)
        assertFalse(ui.scanInProgress)
        assertEquals("no_results", ui.scanStatus)
    }

    @Test
    fun dismissSelectionStopsScanAndClearsUiState() {
        val events = mutableListOf<String>()
        val ui = FakeUiPort().apply {
            activeSelectionKind = DeviceSelectionKind.FTMS
            scanInProgress = true
            scanStatus = "scanning"
            stopEnabled = false
            scannedDevices.add(
                ScannedBleDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    displayName = "Trainer",
                    rssi = -40,
                ),
            )
        }
        val engine = FakeScanEngine(plans = ArrayDeque())

        val coordinator = buildCoordinator(
            uiPort = ui,
            scanEngine = engine,
            ensurePermission = { true },
            onAfterPickerDismissed = { events += "dismissed" },
        )

        coordinator.dismissSelection()

        assertEquals(1, engine.stopCount)
        assertNull(ui.activeSelectionKind)
        assertFalse(ui.scanInProgress)
        assertNull(ui.scanStatus)
        assertTrue(ui.stopEnabled)
        assertTrue(ui.scannedDevices.isEmpty())
        assertEquals(listOf("dismissed"), events)
    }

    @Test
    fun scanResultsAreBufferedUntilThrottleFlush() {
        val handler = ControlledHandler()
        val ui = FakeUiPort()
        val engine = FakeScanEngine(
            plans = ArrayDeque(
                listOf(
                    ScanPlan(started = true, autoFinish = false),
                ),
            ),
        )
        val coordinator = buildCoordinator(
            uiPort = ui,
            scanEngine = engine,
            handler = handler,
            ensurePermission = { true },
        )

        coordinator.requestScan(DeviceSelectionKind.FTMS)
        engine.emitFound(
            ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:01",
                displayName = "Trainer A",
                rssi = -70,
            ),
        )
        engine.emitFound(
            ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:02",
                displayName = "Trainer B",
                rssi = -45,
            ),
        )

        assertTrue(ui.scannedDevices.isEmpty())

        handler.runAllDelayed()

        assertEquals(
            listOf("AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:01"),
            ui.scannedDevices.map { it.macAddress },
        )
        assertTrue(ui.scanInProgress)
    }

    @Test
    fun scanCompletionFlushesBufferedResultsImmediately() {
        val ui = FakeUiPort()
        val engine = FakeScanEngine(
            plans = ArrayDeque(
                listOf(
                    ScanPlan(started = true, autoFinish = false),
                ),
            ),
        )
        val coordinator = buildCoordinator(
            uiPort = ui,
            scanEngine = engine,
            ensurePermission = { true },
        )

        coordinator.requestScan(DeviceSelectionKind.FTMS)
        engine.emitFound(
            ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:09",
                displayName = "Trainer Z",
                rssi = -50,
            ),
        )

        assertTrue(ui.scannedDevices.isEmpty())

        engine.finishCurrent(errorMessage = null)

        assertEquals(1, ui.scannedDevices.size)
        assertEquals("AA:BB:CC:DD:EE:09", ui.scannedDevices.first().macAddress)
        assertFalse(ui.scanInProgress)
        assertEquals("done:1", ui.scanStatus)
    }

    private fun buildCoordinator(
        uiPort: DeviceScanUiPort,
        scanEngine: DeviceScanEngine,
        handler: ControlledHandler = ControlledHandler(),
        ensurePermission: () -> Boolean,
        onBeforeScanRequest: (DeviceSelectionKind) -> Unit = {},
        onBeforeScanStart: () -> Unit = {},
        onAfterPickerDismissed: () -> Unit = {},
    ): DeviceScanCoordinator {
        return DeviceScanCoordinator(
            uiPort = uiPort,
            scanEngine = scanEngine,
            handler = handler,
            messages = DeviceScanMessages(
                scanning = { "scanning" },
                retrying = { "retrying" },
                noResults = { "no_results" },
                done = { count -> "done:$count" },
                failed = { "failed" },
                permissionRequired = { "permission_required" },
            ),
            ftmsServiceUuid = ftmsUuid,
            hrServiceUuid = hrUuid,
            pickerScanMode = 2,
            pickerScanRetryDelayMs = 1_500L,
            pickerStopButtonLockDurationMs = 3_000L,
            scannedDeviceSortThrottleMs = 300L,
            ensureBluetoothScanPermission = ensurePermission,
            onBeforeScanRequest = onBeforeScanRequest,
            onBeforeScanStart = onBeforeScanStart,
            onAfterPickerDismissed = onAfterPickerDismissed,
        )
    }

    private data class ScanPlan(
        val started: Boolean,
        val foundDevices: List<ScannedBleDevice> = emptyList(),
        val finishedWithError: String? = null,
        val autoFinish: Boolean = true,
    )

    private class FakeScanEngine(
        private val plans: ArrayDeque<ScanPlan>,
        private val onStart: (() -> Unit)? = null,
    ) : DeviceScanEngine {
        data class StartCall(
            val serviceUuid: UUID,
            val scanMode: Int,
        )

        val startCalls = mutableListOf<StartCall>()
        var stopCount = 0
        private var currentOnDeviceFound: ((ScannedBleDevice) -> Unit)? = null
        private var currentOnFinished: ((String?) -> Unit)? = null

        override fun start(
            serviceUuid: UUID,
            scanMode: Int,
            onDeviceFound: (ScannedBleDevice) -> Unit,
            onFinished: (String?) -> Unit,
        ): Boolean {
            startCalls += StartCall(serviceUuid = serviceUuid, scanMode = scanMode)
            onStart?.invoke()
            val plan = plans.removeFirstOrNull() ?: return false
            if (!plan.started) {
                return false
            }
            currentOnDeviceFound = onDeviceFound
            currentOnFinished = onFinished
            plan.foundDevices.forEach { device ->
                emitFound(device)
            }
            if (plan.autoFinish) {
                finishCurrent(plan.finishedWithError)
            }
            return true
        }

        override fun stop() {
            stopCount += 1
        }

        fun emitFound(device: ScannedBleDevice) {
            currentOnDeviceFound?.invoke(device)
        }

        fun finishCurrent(errorMessage: String?) {
            val finished = currentOnFinished ?: return
            currentOnDeviceFound = null
            currentOnFinished = null
            finished.invoke(errorMessage)
        }
    }

    private class FakeUiPort : DeviceScanUiPort {
        override var activeSelectionKind: DeviceSelectionKind? = null
        override var scanInProgress: Boolean = false
        override var scanStatus: String? = null
        override var stopEnabled: Boolean = true
        override val scannedDevices: MutableList<ScannedBleDevice> = mutableListOf()
    }

    private class ControlledHandler : Handler(Looper.getMainLooper()) {
        private val delayed = mutableListOf<Runnable>()

        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
            delayed += runnable
            return true
        }

        override fun removeCallbacks(runnable: Runnable) {
            delayed.remove(runnable)
        }

        fun runAllDelayed() {
            while (delayed.isNotEmpty()) {
                val runnable = delayed.removeAt(0)
                runnable.run()
            }
        }
    }
}

package com.example.ergometerapp

import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSelectionFacadeTest {
    private val ftmsUuid: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val hrUuid: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    @Test
    fun onScannedDeviceSelectedRoutesFtmsSelectionAndRunsPostSelectionActions() {
        val ui = FakeUiPort().apply {
            activeSelectionKind = DeviceSelectionKind.FTMS
        }
        val events = mutableListOf<String>()
        var selectedMac: String? = null
        var selectedName: String? = null
        val facade = buildFacade(
            uiPort = ui,
            applyFtms = { normalizedMac, deviceName ->
                selectedMac = normalizedMac
                selectedName = deviceName
                events += "apply_ftms"
            },
            clearConnectionIssuePrompt = { events += "clear_prompt" },
            onAfterPickerDismissed = { events += "picker_dismissed" },
            refreshRecommendations = { events += "refresh" },
        )

        facade.onScannedDeviceSelected(
            ScannedBleDevice(
                macAddress = "aa:bb:cc:dd:ee:ff",
                displayName = "Trainer",
                rssi = -40,
            ),
        )

        assertEquals("AA:BB:CC:DD:EE:FF", selectedMac)
        assertEquals("Trainer", selectedName)
        assertNull(ui.activeSelectionKind)
        assertTrue(ui.scannedDevices.isEmpty())
        assertEquals(
            listOf("apply_ftms", "clear_prompt", "picker_dismissed", "refresh"),
            events,
        )
    }

    @Test
    fun onScannedDeviceSelectedRoutesHrSelection() {
        val ui = FakeUiPort().apply {
            activeSelectionKind = DeviceSelectionKind.HEART_RATE
        }
        val events = mutableListOf<String>()
        var hrMac: String? = null
        var hrName: String? = null
        val facade = buildFacade(
            uiPort = ui,
            applyFtms = { _, _ -> events += "apply_ftms" },
            applyHr = { normalizedMac, deviceName ->
                hrMac = normalizedMac
                hrName = deviceName
                events += "apply_hr"
            },
            clearConnectionIssuePrompt = { events += "clear_prompt" },
            onAfterPickerDismissed = { events += "picker_dismissed" },
            refreshRecommendations = { events += "refresh" },
        )

        facade.onScannedDeviceSelected(
            ScannedBleDevice(
                macAddress = "11:22:33:44:55:66",
                displayName = "HR Strap",
                rssi = -60,
            ),
        )

        assertFalse(events.contains("apply_ftms"))
        assertEquals("11:22:33:44:55:66", hrMac)
        assertEquals("HR Strap", hrName)
        assertEquals(
            listOf("apply_hr", "clear_prompt", "picker_dismissed", "refresh"),
            events,
        )
    }

    @Test
    fun onScannedDeviceSelectedNoopsWhenSelectionKindMissing() {
        val ui = FakeUiPort().apply {
            activeSelectionKind = null
        }
        var applyCalls = 0
        var clearCalls = 0
        var refreshCalls = 0
        val facade = buildFacade(
            uiPort = ui,
            applyFtms = { _, _ -> applyCalls += 1 },
            applyHr = { _, _ -> applyCalls += 1 },
            clearConnectionIssuePrompt = { clearCalls += 1 },
            refreshRecommendations = { refreshCalls += 1 },
        )

        facade.onScannedDeviceSelected(
            ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                displayName = "Ignored",
                rssi = -70,
            ),
        )

        assertEquals(0, applyCalls)
        assertEquals(0, clearCalls)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun onBluetoothScanPermissionResultReturnsGrantedNoPendingWhenNoPendingScan() {
        val facade = buildFacade(ensurePermission = { true })

        val result = facade.onBluetoothScanPermissionResult(granted = true)

        assertEquals(ScanPermissionResult.GRANTED_NO_PENDING, result)
    }

    @Test
    fun requestFtmsScanDelegatesToCoordinatorWithFtmsServiceUuid() {
        val engine = FakeScanEngine(
            plans = ArrayDeque(
                listOf(
                    ScanPlan(started = true, finishedWithError = null),
                ),
            ),
        )
        val facade = buildFacade(
            scanEngine = engine,
            ensurePermission = { true },
        )

        facade.requestFtmsScan()

        assertEquals(1, engine.startCalls.size)
        assertEquals(ftmsUuid, engine.startCalls.first().serviceUuid)
    }

    private fun buildFacade(
        uiPort: DeviceScanUiPort = FakeUiPort(),
        scanEngine: DeviceScanEngine = FakeScanEngine(ArrayDeque()),
        handler: ControlledHandler = ControlledHandler(),
        ensurePermission: () -> Boolean = { false },
        applyFtms: (String?, String?) -> Unit = { _, _ -> },
        applyHr: (String?, String?) -> Unit = { _, _ -> },
        clearConnectionIssuePrompt: () -> Unit = {},
        onAfterPickerDismissed: () -> Unit = {},
        refreshRecommendations: () -> Unit = {},
    ): DeviceSelectionFacade {
        return RealDeviceSelectionFacade(
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
            onBeforeScanRequest = {},
            onBeforeScanStart = {},
            onAfterPickerDismissed = onAfterPickerDismissed,
            applyFtmsSelection = applyFtms,
            applyHrSelection = applyHr,
            clearConnectionIssuePrompt = clearConnectionIssuePrompt,
            refreshAiAssistantRecommendations = refreshRecommendations,
        )
    }

    private data class ScanPlan(
        val started: Boolean,
        val foundDevices: List<ScannedBleDevice> = emptyList(),
        val finishedWithError: String? = null,
    )

    private class FakeScanEngine(
        private val plans: ArrayDeque<ScanPlan>,
    ) : DeviceScanEngine {
        data class StartCall(
            val serviceUuid: UUID,
            val scanMode: Int,
        )

        val startCalls = mutableListOf<StartCall>()
        var stopCount = 0

        override fun start(
            serviceUuid: UUID,
            scanMode: Int,
            onDeviceFound: (ScannedBleDevice) -> Unit,
            onFinished: (String?) -> Unit,
        ): Boolean {
            startCalls += StartCall(serviceUuid = serviceUuid, scanMode = scanMode)
            val plan = if (plans.isEmpty()) null else plans.removeFirst()
            if (plan == null) {
                return false
            }
            if (!plan.started) {
                return false
            }
            plan.foundDevices.forEach(onDeviceFound)
            onFinished(plan.finishedWithError)
            return true
        }

        override fun stop() {
            stopCount += 1
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
    }
}

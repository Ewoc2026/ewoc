package com.example.ergometerapp

import android.os.Handler
import android.os.Looper
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuStatusProbeCoordinatorTest {
    private val ftmsUuid: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val hrUuid: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    @Test
    fun probeTrainerAvailabilityNow_skipsWhenSuppressed() {
        val state = FakeStatePort().apply {
            currentFtmsDeviceMac = "AA:BB:CC:DD:EE:FF"
        }
        val clock = FakeClock(nowMs = 1_000L)
        val trainerEngine = FakeStatusProbeScanEngine()
        val hrEngine = FakeStatusProbeScanEngine()
        val coordinator = buildCoordinator(
            state = state,
            clock = clock,
            trainerEngine = trainerEngine,
            hrEngine = hrEngine,
        )

        coordinator.suppressStatusProbesTemporarily()
        coordinator.probeTrainerAvailabilityNow()

        assertTrue(trainerEngine.startCalls.isEmpty())
        assertTrue(hrEngine.startCalls.isEmpty())
    }

    @Test
    fun probeTrainerAvailabilityNow_updatesReachabilityAndChainsHrProbe() {
        val state = FakeStatePort().apply {
            currentFtmsDeviceMac = "AA:BB:CC:DD:EE:FF"
            currentHrDeviceMac = "11:22:33:44:55:66"
            ftmsReachable = null
        }
        var refreshCount = 0
        val trainerEngine = FakeStatusProbeScanEngine(
            plans = ArrayDeque(
                listOf(
                    ScanPlan(
                        started = true,
                        foundDevices = listOf(
                            ScannedBleDevice(
                                macAddress = "aa:bb:cc:dd:ee:ff",
                                displayName = "Trainer",
                                rssi = -40,
                            ),
                        ),
                        finishedWithError = null,
                    ),
                ),
            ),
        )
        val hrEngine = FakeStatusProbeScanEngine()
        val coordinator = buildCoordinator(
            state = state,
            trainerEngine = trainerEngine,
            hrEngine = hrEngine,
            onRefresh = { refreshCount += 1 },
        )

        coordinator.probeTrainerAvailabilityNow()

        assertEquals(1, trainerEngine.startCalls.size)
        assertEquals(ftmsUuid, trainerEngine.startCalls.first().serviceUuid)
        assertEquals(1, hrEngine.startCalls.size)
        assertEquals(true, state.ftmsReachable)
        assertEquals(1, refreshCount)
    }

    @Test
    fun probeTrainerAvailabilityNow_preservesLastStateWhenScanFails() {
        val state = FakeStatePort().apply {
            currentFtmsDeviceMac = "AA:BB:CC:DD:EE:FF"
            currentHrDeviceMac = "11:22:33:44:55:66"
            ftmsReachable = false
        }
        var refreshCount = 0
        val trainerEngine = FakeStatusProbeScanEngine(
            plans = ArrayDeque(
                listOf(
                    ScanPlan(
                        started = true,
                        finishedWithError = "Scan failed (code=6).",
                    ),
                ),
            ),
        )
        val hrEngine = FakeStatusProbeScanEngine()
        val coordinator = buildCoordinator(
            state = state,
            trainerEngine = trainerEngine,
            hrEngine = hrEngine,
            onRefresh = { refreshCount += 1 },
        )

        coordinator.probeTrainerAvailabilityNow()

        assertEquals(1, trainerEngine.startCalls.size)
        assertEquals(1, hrEngine.startCalls.size)
        assertEquals(false, state.ftmsReachable)
        assertEquals(0, refreshCount)
    }

    @Test
    fun probeHrAvailabilityNow_marksSensorUnreachableAfterMissThreshold() {
        val state = FakeStatePort().apply {
            currentHrDeviceMac = "11:22:33:44:55:66"
            hrReachable = true
            hrConsecutiveMisses = 1
            hrLastSeenElapsedMs = 200L
        }
        var refreshCount = 0
        val hrEngine = FakeStatusProbeScanEngine(
            plans = ArrayDeque(
                listOf(
                    ScanPlan(started = true),
                ),
            ),
        )
        val coordinator = buildCoordinator(
            state = state,
            hrEngine = hrEngine,
            onRefresh = { refreshCount += 1 },
        )

        coordinator.probeHrAvailabilityNow()

        assertEquals(1, hrEngine.startCalls.size)
        assertEquals(2, state.hrConsecutiveMisses)
        assertEquals(false, state.hrReachable)
        assertEquals(1, refreshCount)
    }

    @Test
    fun probeHrAvailabilityNow_clearsProbeStateWhenSelectionMissing() {
        val state = FakeStatePort().apply {
            currentHrDeviceMac = null
            hrReachable = false
            hrConsecutiveMisses = 5
            hrLastSeenElapsedMs = 100L
        }
        var refreshCount = 0
        val hrEngine = FakeStatusProbeScanEngine()
        val coordinator = buildCoordinator(
            state = state,
            hrEngine = hrEngine,
            onRefresh = { refreshCount += 1 },
        )

        coordinator.probeHrAvailabilityNow()

        assertTrue(hrEngine.startCalls.isEmpty())
        assertNull(state.hrReachable)
        assertEquals(0, state.hrConsecutiveMisses)
        assertNull(state.hrLastSeenElapsedMs)
        assertEquals(1, refreshCount)
    }

    private fun buildCoordinator(
        state: FakeStatePort,
        clock: FakeClock = FakeClock(nowMs = 5_000L),
        trainerEngine: FakeStatusProbeScanEngine = FakeStatusProbeScanEngine(),
        hrEngine: FakeStatusProbeScanEngine = FakeStatusProbeScanEngine(),
        onRefresh: () -> Unit = {},
    ): MenuStatusProbeCoordinator {
        return MenuStatusProbeCoordinator(
            statePort = state,
            trainerScanEngine = trainerEngine,
            hrScanEngine = hrEngine,
            handler = ControlledHandler(),
            nowElapsedMs = { clock.nowMs },
            isClosed = { false },
            hasBluetoothScanPermission = { true },
            refreshMenuRecommendations = onRefresh,
            ftmsServiceUuid = ftmsUuid,
            hrServiceUuid = hrUuid,
            statusProbeScanMode = 1,
            trainerStatusProbeIntervalMs = 10_000L,
            trainerStatusProbeDurationMs = 1_500L,
            hrStatusProbeIntervalMs = 30_000L,
            hrStatusProbeDurationMs = 1_500L,
            statusProbeResumeDelayAfterPickerMs = 2_000L,
            hrStatusMissThreshold = 2,
            hrStatusStaleTimeoutMs = 75_000L,
        )
    }

    private data class ScanPlan(
        val started: Boolean = true,
        val foundDevices: List<ScannedBleDevice> = emptyList(),
        val finishedWithError: String? = null,
        val autoFinish: Boolean = true,
    )

    private class FakeStatusProbeScanEngine(
        private val plans: ArrayDeque<ScanPlan> = ArrayDeque(),
    ) : StatusProbeScanEngine {
        data class StartCall(
            val serviceUuid: UUID,
            val durationMs: Long,
            val scanMode: Int,
        )

        val startCalls = mutableListOf<StartCall>()
        var stopCount = 0
        private var currentOnDeviceFound: ((ScannedBleDevice) -> Unit)? = null
        private var currentOnFinished: ((String?) -> Unit)? = null

        override fun start(
            serviceUuid: UUID,
            durationMs: Long,
            scanMode: Int,
            onDeviceFound: (ScannedBleDevice) -> Unit,
            onFinished: (String?) -> Unit,
        ): Boolean {
            startCalls += StartCall(
                serviceUuid = serviceUuid,
                durationMs = durationMs,
                scanMode = scanMode,
            )
            val plan = plans.removeFirstOrNull() ?: ScanPlan(started = false)
            if (!plan.started) return false
            currentOnDeviceFound = onDeviceFound
            currentOnFinished = onFinished
            plan.foundDevices.forEach { device ->
                currentOnDeviceFound?.invoke(device)
            }
            if (plan.autoFinish) {
                finishCurrent(plan.finishedWithError)
            }
            return true
        }

        override fun stop() {
            stopCount += 1
            currentOnDeviceFound = null
            currentOnFinished = null
        }

        private fun finishCurrent(errorMessage: String?) {
            val finished = currentOnFinished ?: return
            currentOnDeviceFound = null
            currentOnFinished = null
            finished.invoke(errorMessage)
        }
    }

    private class FakeStatePort : MenuStatusProbeStatePort {
        var screen: AppScreen = AppScreen.MENU
        var pickerActive: Boolean = false
        var scanInProgress: Boolean = false
        override var currentFtmsDeviceMac: String? = null
        override var currentHrDeviceMac: String? = null
        override var ftmsReachable: Boolean? = null
        override var hrReachable: Boolean? = null
        override var hrConsecutiveMisses: Int = 0
        override var hrLastSeenElapsedMs: Long? = null

        override val currentScreen: AppScreen
            get() = screen

        override val isPickerActiveOrScanInProgress: Boolean
            get() = pickerActive || scanInProgress
    }

    private data class FakeClock(
        var nowMs: Long,
    )

    private class ControlledHandler : Handler(Looper.getMainLooper()) {
        private val posted = mutableListOf<Runnable>()
        private val delayed = mutableListOf<Runnable>()

        override fun post(runnable: Runnable): Boolean {
            posted += runnable
            return true
        }

        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
            delayed += runnable
            return true
        }

        override fun removeCallbacks(runnable: Runnable) {
            posted.remove(runnable)
            delayed.remove(runnable)
        }
    }
}

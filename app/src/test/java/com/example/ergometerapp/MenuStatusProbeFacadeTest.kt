package com.example.ergometerapp

import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuStatusProbeFacadeTest {
    private val ftmsUuid: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val hrUuid: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    @Test
    fun probeTrainerAvailabilityNow_updatesReachabilityAndChainsHrProbe() {
        val state = FakeStatePort().apply {
            currentFtmsDeviceMac = "AA:BB:CC:DD:EE:FF"
            currentHrDeviceMac = "11:22:33:44:55:66"
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
                                rssi = -42,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val hrEngine = FakeStatusProbeScanEngine(
            plans = ArrayDeque(
                listOf(ScanPlan(started = true)),
            ),
        )
        val facade = buildFacade(
            state = state,
            trainerEngine = trainerEngine,
            hrEngine = hrEngine,
            onRefresh = { refreshCount += 1 },
        )

        facade.probeTrainerAvailabilityNow()

        assertEquals(true, state.ftmsReachable)
        assertEquals(1, trainerEngine.startCalls.size)
        assertEquals(1, hrEngine.startCalls.size)
        assertEquals(1, refreshCount)
    }

    @Test
    fun probeHrAvailabilityNow_clearsProbeStateWhenSelectionMissing() {
        val state = FakeStatePort().apply {
            currentHrDeviceMac = null
            hrReachable = false
            hrConsecutiveMisses = 3
            hrLastSeenElapsedMs = 123L
        }
        var refreshCount = 0
        val facade = buildFacade(
            state = state,
            onRefresh = { refreshCount += 1 },
        )

        facade.probeHrAvailabilityNow()

        assertNull(state.hrReachable)
        assertEquals(0, state.hrConsecutiveMisses)
        assertNull(state.hrLastSeenElapsedMs)
        assertEquals(1, refreshCount)
    }

    @Test
    fun close_stopsBothProbeEngines() {
        val trainerEngine = FakeStatusProbeScanEngine()
        val hrEngine = FakeStatusProbeScanEngine()
        val facade = buildFacade(
            trainerEngine = trainerEngine,
            hrEngine = hrEngine,
        )

        facade.close()

        assertEquals(1, trainerEngine.stopCount)
        assertEquals(1, hrEngine.stopCount)
    }

    private fun buildFacade(
        state: FakeStatePort = FakeStatePort(),
        trainerEngine: FakeStatusProbeScanEngine = FakeStatusProbeScanEngine(),
        hrEngine: FakeStatusProbeScanEngine = FakeStatusProbeScanEngine(),
        onRefresh: () -> Unit = {},
    ): MenuStatusProbeFacade {
        return RealMenuStatusProbeFacade(
            statePort = state,
            appHandler = ControlledHandler(),
            hasBluetoothScanPermission = { true },
            refreshMenuRecommendations = onRefresh,
            isClosed = { false },
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
            trainerScanEngine = trainerEngine,
            hrScanEngine = hrEngine,
            nowElapsedMs = { 5_000L },
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
            val plan = if (plans.isEmpty()) {
                ScanPlan(started = false)
            } else {
                plans.removeFirst()
            }
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
        override val currentScreen: AppScreen = AppScreen.MENU
        override val isPickerActiveOrScanInProgress: Boolean = false
        override var currentFtmsDeviceMac: String? = null
        override var currentHrDeviceMac: String? = null
        override var ftmsReachable: Boolean? = null
        override var hrReachable: Boolean? = null
        override var hrConsecutiveMisses: Int = 0
        override var hrLastSeenElapsedMs: Long? = null
    }

    private class ControlledHandler : Handler(Looper.getMainLooper()) {
        private val delayed = mutableListOf<Runnable>()

        override fun post(runnable: Runnable): Boolean {
            return true
        }

        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
            delayed += runnable
            return true
        }

        override fun removeCallbacks(runnable: Runnable) {
            delayed.remove(runnable)
        }
    }
}

package com.example.ergometerapp.ble.debug

sealed interface FtmsDebugEvent {
    data class TargetPowerIssued(val timestampMs: Long, val targetPowerWatts: Int) : FtmsDebugEvent
    data class PowerSample(val timestampMs: Long, val powerWatts: Int) : FtmsDebugEvent

    data class ObservationEnded(val timestampMs: Long) : FtmsDebugEvent
}

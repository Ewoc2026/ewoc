package com.example.ergometerapp.ftms

/**
 * Narrow output interface for ERG target control.
 *
 * - watts != null → write ERG target
 * - watts == null → no structured ERG target is requested anymore
 */
fun interface FtmsTargetWriter {
    fun setTargetWatts(watts: Int?)
}

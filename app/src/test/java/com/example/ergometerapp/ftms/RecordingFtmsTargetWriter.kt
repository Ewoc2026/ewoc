package com.example.ergometerapp.ftms

class RecordingFtmsTargetWriter : FtmsTargetWriter {
    val writes = mutableListOf<Int?>()
    override fun setTargetWatts(watts: Int?) {
        writes += watts
    }
}


package io.github.ewoc2026.ewoc.ftms

class RecordingFtmsTargetWriter : FtmsTargetWriter {
    val writes = mutableListOf<Int?>()
    override fun setTargetWatts(watts: Int?) {
        writes += watts
    }
}


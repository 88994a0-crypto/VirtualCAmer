package com.example.virtualcamer.xposed

object FrameConverter {
    fun convertAndResize(
        frame: ByteArray,
        width: Int,
        height: Int,
        format: Int,
        requestedSize: Int
    ): ByteArray {
        val limitedSize = minOf(frame.size, requestedSize)
        return frame.copyOf(limitedSize)
    }
}

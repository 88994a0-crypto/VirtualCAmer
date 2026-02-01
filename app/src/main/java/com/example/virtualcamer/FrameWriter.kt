package com.example.virtualcamer

import android.graphics.Bitmap
import java.nio.ByteBuffer

class FrameWriter(private val bridge: VirtualCameraBridge) {
    fun writeBitmap(bitmap: Bitmap): Boolean {
        bridge.configureStream(bitmap.width, bitmap.height)
        val buffer = ByteBuffer.allocateDirect(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        return bridge.writeFrame(buffer)
    }
}

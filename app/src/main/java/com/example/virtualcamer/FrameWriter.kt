package com.example.virtualcamer

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class FrameWriter(private val bridge: VirtualCameraBridge) {
    fun writeBitmap(bitmap: Bitmap): Boolean {
        val adjusted = ensureEvenDimensions(bitmap)
        if (adjusted == null) {
            Log.e("VirtualCamera", "Invalid bitmap dimensions: ${bitmap.width}x${bitmap.height}")
            return false
        }
        return try {
            val buffer = convertArgbToI420(adjusted)
            if (buffer == null) {
                Log.e("VirtualCamera", "Failed to convert frame to I420")
                return false
            }
            if (!bridge.configureStream(adjusted.width, adjusted.height)) {
                Log.e("VirtualCamera", "Failed to configure stream ${adjusted.width}x${adjusted.height}")
                return false
            }
            buffer.rewind()
            bridge.writeFrame(buffer)
        } finally {
            if (adjusted !== bitmap) {
                adjusted.recycle()
            }
        }
    }

    private fun convertArgbToI420(bitmap: Bitmap): ByteBuffer? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) {
            return null
        }
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val ySize = width * height
        val uvSize = ySize / 4
        val yPlane = ByteArray(ySize)
        val uPlane = ByteArray(uvSize)
        val vPlane = ByteArray(uvSize)

        var pixelIndex = 0
        for (y in 0 until height) {
            val uvRow = y / 2
            for (x in 0 until width) {
                val color = argb[pixelIndex++]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff

                val yValue = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val uValue = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val vValue = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                val yIndex = y * width + x
                yPlane[yIndex] = clampToByte(yValue)

                if (y % 2 == 0 && x % 2 == 0) {
                    val uvIndex = uvRow * (width / 2) + (x / 2)
                    uPlane[uvIndex] = clampToByte(uValue)
                    vPlane[uvIndex] = clampToByte(vValue)
                }
            }
        }

        val buffer = ByteBuffer.allocateDirect(ySize + uvSize * 2)
        buffer.put(yPlane)
        buffer.put(uPlane)
        buffer.put(vPlane)
        return buffer
    }

    private fun ensureEvenDimensions(bitmap: Bitmap): Bitmap? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 1 || height <= 1) {
            return null
        }
        val evenWidth = width - (width % 2)
        val evenHeight = height - (height % 2)
        if (evenWidth == width && evenHeight == height) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, evenWidth, evenHeight)
    }

    private fun clampToByte(value: Int): Byte {
        val clamped = min(255, max(0, value))
        return clamped.toByte()
    }
}

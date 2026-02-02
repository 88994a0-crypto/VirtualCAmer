package com.example.virtualcamer.xposed

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Log
import java.nio.ByteBuffer

object FrameConverter {
    private const val TAG = "FrameConverter"
    
    /**
     * Convert and resize RTMP frame to match camera's expected format and size
     */
    fun convertAndResize(
        frame: ByteArray,
        targetWidth: Int,
        targetHeight: Int,
        targetFormat: Int,
        requestedSize: Int
    ): ByteArray {
        try {
            // Assume input is NV21 from RTMP reader
            val sourceWidth = calculateWidth(frame.size)
            val sourceHeight = calculateHeight(frame.size, sourceWidth)
            
            // If sizes match and format is NV21, just return (most common case)
            if (sourceWidth == targetWidth && sourceHeight == targetHeight && targetFormat == ImageFormat.NV21) {
                return frame.copyOf(minOf(frame.size, requestedSize))
            }
            
            // Need to resize
            val resized = resizeYUV420(frame, sourceWidth, sourceHeight, targetWidth, targetHeight)
            
            // Convert format if needed
            return when (targetFormat) {
                ImageFormat.NV21 -> resized
                ImageFormat.YV12 -> nv21ToYV12(resized, targetWidth, targetHeight)
                ImageFormat.YUV_420_888 -> resized // Same as NV21 for practical purposes
                else -> {
                    Log.w(TAG, "Unsupported format $targetFormat, returning NV21")
                    resized
                }
            }.copyOf(minOf(resized.size, requestedSize))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting frame", e)
            // Fallback: just copy what we can
            return frame.copyOf(minOf(frame.size, requestedSize))
        }
    }
    
    /**
     * Resize YUV420 (NV21) frame using bilinear interpolation
     */
    private fun resizeYUV420(
        input: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): ByteArray {
        // Simple nearest-neighbor sampling for speed
        // For production, could use bilinear or bicubic
        
        val output = ByteArray(dstWidth * dstHeight * 3 / 2)
        
        // Resize Y plane
        val srcYSize = srcWidth * srcHeight
        val dstYSize = dstWidth * dstHeight
        
        val xRatio = srcWidth.toFloat() / dstWidth
        val yRatio = srcHeight.toFloat() / dstHeight
        
        for (y in 0 until dstHeight) {
            for (x in 0 until dstWidth) {
                val srcX = (x * xRatio).toInt().coerceIn(0, srcWidth - 1)
                val srcY = (y * yRatio).toInt().coerceIn(0, srcHeight - 1)
                output[y * dstWidth + x] = input[srcY * srcWidth + srcX]
            }
        }
        
        // Resize UV plane (half resolution)
        val srcUVWidth = srcWidth / 2
        val srcUVHeight = srcHeight / 2
        val dstUVWidth = dstWidth / 2
        val dstUVHeight = dstHeight / 2
        
        val uvXRatio = srcUVWidth.toFloat() / dstUVWidth
        val uvYRatio = srcUVHeight.toFloat() / dstUVHeight
        
        for (y in 0 until dstUVHeight) {
            for (x in 0 until dstUVWidth) {
                val srcX = (x * uvXRatio).toInt().coerceIn(0, srcUVWidth - 1)
                val srcY = (y * uvYRatio).toInt().coerceIn(0, srcUVHeight - 1)
                
                val srcIndex = srcYSize + srcY * srcWidth + srcX * 2
                val dstIndex = dstYSize + y * dstWidth + x * 2
                
                // Copy V and U
                output[dstIndex] = input[srcIndex]
                output[dstIndex + 1] = input[srcIndex + 1]
            }
        }
        
        return output
    }
    
    /**
     * Convert NV21 to YV12 format
     * NV21: Y plane, interleaved VU
     * YV12: Y plane, V plane, U plane
     */
    private fun nv21ToYV12(nv21: ByteArray, width: Int, height: Int): ByteArray {
        val yv12 = ByteArray(nv21.size)
        val frameSize = width * height
        val qFrameSize = frameSize / 4
        
        // Copy Y plane
        System.arraycopy(nv21, 0, yv12, 0, frameSize)
        
        // De-interleave VU to separate V and U planes
        var vIndex = frameSize
        var uIndex = frameSize + qFrameSize
        
        for (i in 0 until qFrameSize) {
            yv12[vIndex++] = nv21[frameSize + i * 2]      // V
            yv12[uIndex++] = nv21[frameSize + i * 2 + 1]  // U
        }
        
        return yv12
    }
    
    /**
     * Calculate width from frame size (assumes 4:3 or 16:9 aspect ratio)
     */
    private fun calculateWidth(size: Int): Int {
        // YUV420 size = width * height * 1.5
        // Common resolutions
        return when {
            size >= 1920 * 1080 * 3 / 2 -> 1920 // 1080p
            size >= 1280 * 720 * 3 / 2 -> 1280  // 720p
            size >= 640 * 480 * 3 / 2 -> 640    // VGA
            else -> 320                           // QVGA
        }
    }
    
    private fun calculateHeight(size: Int, width: Int): Int {
        // YUV420 size = width * height * 1.5
        return (size * 2 / 3) / width
    }
}

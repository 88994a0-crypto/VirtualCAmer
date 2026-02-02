package com.example.virtualcamer.xposed

import android.graphics.ImageFormat
import android.hardware.Camera
import android.util.Log

class RtmpFrameProvider private constructor() {
    private var rtmpReader: RtmpStreamReader? = null
    private var frameBuffer: FrameBuffer? = null
    private var isConnected = false
    
    private var currentWidth = 0
    private var currentHeight = 0

    fun connect(rtmpUrl: String) {
        rtmpReader?.disconnect()
        frameBuffer = FrameBuffer(60) // Increased buffer for smoother playback
        
        rtmpReader = RtmpStreamReader(rtmpUrl).apply {
            setFrameListener { frame ->
                // Extract dimensions from first frame if not set
                if (currentWidth == 0 || currentHeight == 0) {
                    estimateDimensions(frame.size)
                }
                frameBuffer?.addFrame(frame, currentWidth, currentHeight)
            }
            connect()
        }
        isConnected = true
        Log.d(TAG, "RTMP frame provider connected to $rtmpUrl")
    }

    fun disconnect() {
        rtmpReader?.disconnect()
        rtmpReader = null
        frameBuffer?.clear()
        frameBuffer = null
        isConnected = false
        currentWidth = 0
        currentHeight = 0
        Log.d(TAG, "RTMP frame provider disconnected")
    }

    fun getLatestFrame(size: Int, previewSize: Camera.Size?): ByteArray? {
        if (!isConnected || frameBuffer == null || previewSize == null) {
            return null
        }
        
        val frameInfo = frameBuffer?.getLatestFrameWithInfo() ?: return null
        
        return FrameConverter.convertAndResize(
            frameInfo.data,
            previewSize.width,
            previewSize.height,
            ImageFormat.NV21,
            size
        )
    }

    fun getLatestFrameYUV(): ByteArray? {
        if (!isConnected || frameBuffer == null) {
            return null
        }
        return frameBuffer?.getLatestFrame()
    }
    
    fun getLatestFrameYUVWithSize(targetWidth: Int, targetHeight: Int): ByteArray? {
        if (!isConnected || frameBuffer == null) {
            return null
        }
        
        val frameInfo = frameBuffer?.getLatestFrameWithInfo() ?: return null
        
        // If sizes match, return as-is
        if (frameInfo.width == targetWidth && frameInfo.height == targetHeight) {
            return frameInfo.data
        }
        
        // Otherwise resize
        return FrameConverter.convertAndResize(
            frameInfo.data,
            targetWidth,
            targetHeight,
            ImageFormat.NV21,
            targetWidth * targetHeight * 3 / 2
        )
    }
    
    fun isConnected(): Boolean = isConnected
    
    fun getStats(): FrameBuffer.BufferStats? = frameBuffer?.getStats()
    
    private fun estimateDimensions(frameSize: Int) {
        // YUV420 size = width * height * 1.5
        // Common resolutions
        when {
            frameSize >= 1920 * 1080 * 3 / 2 -> {
                currentWidth = 1920
                currentHeight = 1080
            }
            frameSize >= 1280 * 720 * 3 / 2 -> {
                currentWidth = 1280
                currentHeight = 720
            }
            frameSize >= 640 * 480 * 3 / 2 -> {
                currentWidth = 640
                currentHeight = 480
            }
            else -> {
                currentWidth = 320
                currentHeight = 240
            }
        }
        Log.d(TAG, "Estimated frame dimensions: ${currentWidth}x${currentHeight}")
    }

    companion object {
        private const val TAG = "RtmpFrameProvider"
        
        @Volatile
        private var instance: RtmpFrameProvider? = null

        fun getInstance(): RtmpFrameProvider {
            return instance ?: synchronized(this) {
                instance ?: RtmpFrameProvider().also { instance = it }
            }
        }
    }
}

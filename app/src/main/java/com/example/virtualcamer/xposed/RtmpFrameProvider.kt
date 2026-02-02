package com.example.virtualcamer.xposed

import android.graphics.ImageFormat
import android.hardware.Camera
import android.util.Log

class RtmpFrameProvider private constructor() {
    private var rtmpReader: RtmpStreamReader? = null
    private var frameBuffer: FrameBuffer? = null
    private var isConnected = false

    fun connect(rtmpUrl: String) {
        rtmpReader?.disconnect()
        frameBuffer = FrameBuffer(30)
        rtmpReader = RtmpStreamReader(rtmpUrl).apply {
            setFrameListener { frame ->
                frameBuffer?.addFrame(frame)
            }
            connect()
        }
        isConnected = true
        Log.d("RTMP_INJECT", "RTMP frame provider connected to $rtmpUrl")
    }

    fun disconnect() {
        rtmpReader?.disconnect()
        rtmpReader = null
        frameBuffer = null
        isConnected = false
    }

    fun getLatestFrame(size: Int, previewSize: Camera.Size?): ByteArray? {
        if (!isConnected || frameBuffer == null || previewSize == null) {
            return null
        }
        val frame = frameBuffer?.getLatestFrame() ?: return null
        return FrameConverter.convertAndResize(
            frame,
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

    companion object {
        @Volatile
        private var instance: RtmpFrameProvider? = null

        fun getInstance(): RtmpFrameProvider {
            return instance ?: synchronized(this) {
                instance ?: RtmpFrameProvider().also { instance = it }
            }
        }
    }
}

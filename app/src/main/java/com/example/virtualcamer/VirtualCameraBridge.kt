package com.example.virtualcamer

import android.util.Log
import java.nio.ByteBuffer

class VirtualCameraBridge {
    private var deviceHandle: Long = 0
    private var configuredWidth: Int = 0
    private var configuredHeight: Int = 0

    init {
        try {
            System.loadLibrary("virtualcamera")
        } catch (exception: UnsatisfiedLinkError) {
            Log.e("VirtualCamera", "Failed to load native library", exception)
        }
    }

    fun openDevice(path: String): Boolean {
        closeDevice()
        deviceHandle = nativeOpenDevice(path)
        configuredWidth = 0
        configuredHeight = 0
        return deviceHandle != 0L
    }

    fun writeFrame(buffer: ByteBuffer): Boolean {
        if (deviceHandle == 0L || !buffer.isDirect) {
            return false
        }
        return nativeWriteFrame(deviceHandle, buffer, buffer.remaining())
    }

    fun configureStream(width: Int, height: Int): Boolean {
        if (deviceHandle == 0L || width <= 0 || height <= 0) {
            return false
        }
        if (width == configuredWidth && height == configuredHeight) {
            return true
        }
        val success = nativeConfigureStream(deviceHandle, width, height)
        if (success) {
            configuredWidth = width
            configuredHeight = height
        }
        return success
    }

    fun closeDevice() {
        if (deviceHandle != 0L) {
            nativeCloseDevice(deviceHandle)
            deviceHandle = 0L
        }
    }

    private external fun nativeOpenDevice(path: String): Long

    private external fun nativeWriteFrame(handle: Long, buffer: ByteBuffer, length: Int): Boolean

    private external fun nativeConfigureStream(handle: Long, width: Int, height: Int): Boolean

    private external fun nativeCloseDevice(handle: Long)
}

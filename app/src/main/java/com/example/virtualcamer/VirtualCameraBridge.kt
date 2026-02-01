package com.example.virtualcamer

import android.util.Log
import java.nio.ByteBuffer

class VirtualCameraBridge {
    private var deviceHandle: Long = 0

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
        return deviceHandle != 0L
    }

    fun writeFrame(buffer: ByteBuffer): Boolean {
        if (deviceHandle == 0L || !buffer.isDirect) {
            return false
        }
        return nativeWriteFrame(deviceHandle, buffer, buffer.remaining())
    }

    fun closeDevice() {
        if (deviceHandle != 0L) {
            nativeCloseDevice(deviceHandle)
            deviceHandle = 0L
        }
    }

    private external fun nativeOpenDevice(path: String): Long

    private external fun nativeWriteFrame(handle: Long, buffer: ByteBuffer, length: Int): Boolean

    private external fun nativeCloseDevice(handle: Long)
}

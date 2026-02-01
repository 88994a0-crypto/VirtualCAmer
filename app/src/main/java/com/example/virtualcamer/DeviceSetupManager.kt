package com.example.virtualcamer

import android.util.Log
import java.io.File
import java.util.concurrent.ExecutorService

class DeviceSetupManager(
    private val executor: ExecutorService,
    private val onStatus: (String) -> Unit
) {
    fun ensureLoopbackInstalled() {
        executor.execute {
            val command =
                "modprobe v4l2loopback devices=1 video_nr=0 card_label=\"VirtualCam\" exclusive_caps=1"
            val success = runAsRoot(command)
            val message = when {
                !success -> "Failed to install v4l2loopback"
                detectDevicePath() == null -> "v4l2loopback loaded but no /dev/video* device found"
                else -> "v4l2loopback installed"
            }
            onStatus(message)
        }
    }

    fun detectDevicePath(): String? {
        val candidates = listOf("/dev/video0", "/dev/video1", "/dev/video2", "/dev/video3")
        return candidates.firstOrNull { File(it).exists() }
    }

    fun isDeviceAvailable(path: String): Boolean {
        if (path.isBlank()) {
            return false
        }
        val file = File(path)
        return file.exists() && file.canRead() && file.canWrite()
    }

    fun getDeviceIssue(path: String): String? {
        if (path.isBlank()) {
            return "Device path is empty"
        }
        val file = File(path)
        if (!file.exists()) {
            return "Virtual camera not found at $path"
        }
        if (!file.canRead() || !file.canWrite()) {
            return "Insufficient permissions for $path"
        }
        return null
    }

    private fun runAsRoot(command: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (exception: Exception) {
            Log.e("VirtualCamera", "Root command failed", exception)
            false
        }
    }
}

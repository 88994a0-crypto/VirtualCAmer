package com.example.virtualcamer

import java.io.File
import java.util.concurrent.ExecutorService

class DeviceSetupManager(
    private val executor: ExecutorService,
    private val onStatus: (String) -> Unit
) {
    fun reportObsVirtualCameraStatus() {
        executor.execute {
            val detectedPath = detectDevicePath()
            val message = if (detectedPath == null) {
                "OBS virtual camera not detected. Start OBS and enable Virtual Camera."
            } else {
                "OBS virtual camera detected at $detectedPath"
            }
            onStatus(message)
        }
    }

    fun detectDevicePath(): String? {
        val candidates = listOf("/dev/video0", "/dev/video1", "/dev/video2", "/dev/video3")
        return candidates.firstOrNull { File(it).exists() && File(it).canRead() && File(it).canWrite() }
    }

    fun listDevicePaths(): List<String> {
        val candidates = listOf("/dev/video0", "/dev/video1", "/dev/video2", "/dev/video3")
        return candidates.filter { File(it).exists() }
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

}

package com.example.virtualcamer.xposed

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.os.Handler
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.nio.ByteBuffer

object Camera2Hook {
    private val frameProvider = RtmpFrameProvider.getInstance()
    private const val TAG = "Camera2Hook"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.d(TAG, "Initializing Camera2 hooks for ${lpparam.packageName}")
        
        try {
            hookCameraManager(lpparam)
            hookCaptureSession(lpparam)
            hookImagePlanes(lpparam)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Camera2 hooks", e)
        }
    }
    
    private fun hookCameraManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraManager",
            lpparam.classLoader,
            "openCamera",
            String::class.java,
            CameraDevice.Callback::class.java,
            Handler::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val cameraId = param.args[0] as? String ?: return
                    if (!shouldInjectCamera(cameraId)) {
                        return
                    }
                    Log.d(TAG, "Hooking camera $cameraId")
                    val original = param.args[1] as? CameraDevice.Callback
                    param.args[1] = InjectingCameraCallback(original, cameraId)
                }
            }
        )
    }
    
    private fun hookCaptureSession(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraCaptureSession",
            lpparam.classLoader,
            "setRepeatingRequest",
            CaptureRequest::class.java,
            CameraCaptureSession.CaptureCallback::class.java,
            Handler::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val original = param.args[1] as? CameraCaptureSession.CaptureCallback
                    param.args[1] = InjectingCaptureCallback(original)
                }
            }
        )
        
        // Also hook single capture
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraCaptureSession",
            lpparam.classLoader,
            "capture",
            CaptureRequest::class.java,
            CameraCaptureSession.CaptureCallback::class.java,
            Handler::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val original = param.args[1] as? CameraCaptureSession.CaptureCallback
                    param.args[1] = InjectingCaptureCallback(original)
                }
            }
        )
    }
    
    private fun hookImagePlanes(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "android.media.Image",
            lpparam.classLoader,
            "getPlanes",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val planes = param.result as? Array<Image.Plane> ?: return
                        val image = param.thisObject as? Image ?: return
                        injectFrameData(planes, image.width, image.height)
                    } catch (e: Exception) {
                        // Silently fail to avoid spamming logs
                        if (Math.random() < 0.01) { // Log 1% of errors
                            Log.v(TAG, "Minor error in plane injection: ${e.message}")
                        }
                    }
                }
            }
        )
    }

    private fun injectFrameData(planes: Array<Image.Plane>, width: Int, height: Int) {
        if (planes.isEmpty()) {
            return
        }
        
        val rtmpFrame = frameProvider.getLatestFrameYUVWithSize(width, height)
        if (rtmpFrame == null) {
            return
        }
        
        try {
            // Inject Y plane (luminance)
            injectYPlane(planes[0], rtmpFrame, width, height)
            
            // Inject UV planes if they exist
            if (planes.size >= 2) {
                injectUVPlanes(planes, rtmpFrame, width, height)
            }
            
        } catch (e: Exception) {
            if (Math.random() < 0.01) { // Log 1% of errors
                Log.v(TAG, "Frame injection error: ${e.message}")
            }
        }
    }
    
    private fun injectYPlane(plane: Image.Plane, frame: ByteArray, width: Int, height: Int) {
        val buffer = plane.buffer
        if (buffer.isReadOnly) {
            return
        }
        
        try {
            buffer.rewind()
            val ySize = width * height
            val availableSize = minOf(ySize, frame.size, buffer.remaining())
            
            if (availableSize > 0) {
                buffer.put(frame, 0, availableSize)
            }
        } catch (e: Exception) {
            // Buffer might be in use, skip this frame
        }
    }
    
    private fun injectUVPlanes(planes: Array<Image.Plane>, frame: ByteArray, width: Int, height: Int) {
        val frameSize = width * height
        
        if (planes.size == 2) {
            // Semi-planar format (NV21/NV12) - UV interleaved
            injectSemiPlanarUV(planes[1], frame, frameSize)
        } else if (planes.size >= 3) {
            // Planar format (YV12/I420) - separate U and V
            injectPlanarUV(planes[1], planes[2], frame, frameSize)
        }
    }
    
    private fun injectSemiPlanarUV(uvPlane: Image.Plane, frame: ByteArray, frameSize: Int) {
        val buffer = uvPlane.buffer
        if (buffer.isReadOnly) {
            return
        }
        
        try {
            buffer.rewind()
            val uvSize = (frame.size - frameSize)
            val availableSize = minOf(uvSize, buffer.remaining())
            
            if (availableSize > 0) {
                buffer.put(frame, frameSize, availableSize)
            }
        } catch (e: Exception) {
            // Skip
        }
    }
    
    private fun injectPlanarUV(uPlane: Image.Plane, vPlane: Image.Plane, frame: ByteArray, frameSize: Int) {
        val uvPlaneSize = (frame.size - frameSize) / 2
        
        // Inject U plane
        if (!uPlane.buffer.isReadOnly) {
            try {
                val uBuffer = uPlane.buffer
                uBuffer.rewind()
                
                // Extract U values from NV21 (interleaved at frameSize + 1, frameSize + 3, ...)
                val uData = ByteArray(uvPlaneSize)
                for (i in 0 until uvPlaneSize) {
                    val srcIdx = frameSize + i * 2 + 1
                    if (srcIdx < frame.size) {
                        uData[i] = frame[srcIdx]
                    }
                }
                
                val copySize = minOf(uData.size, uBuffer.remaining())
                if (copySize > 0) {
                    uBuffer.put(uData, 0, copySize)
                }
            } catch (e: Exception) {
                // Skip
            }
        }
        
        // Inject V plane
        if (!vPlane.buffer.isReadOnly) {
            try {
                val vBuffer = vPlane.buffer
                vBuffer.rewind()
                
                // Extract V values from NV21 (interleaved at frameSize, frameSize + 2, ...)
                val vData = ByteArray(uvPlaneSize)
                for (i in 0 until uvPlaneSize) {
                    val srcIdx = frameSize + i * 2
                    if (srcIdx < frame.size) {
                        vData[i] = frame[srcIdx]
                    }
                }
                
                val copySize = minOf(vData.size, vBuffer.remaining())
                if (copySize > 0) {
                    vBuffer.put(vData, 0, copySize)
                }
            } catch (e: Exception) {
                // Skip
            }
        }
    }

    private class InjectingCaptureCallback(
        private val original: CameraCaptureSession.CaptureCallback?
    ) : CameraCaptureSession.CaptureCallback() {
        
        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            original?.onCaptureStarted(session, request, timestamp, frameNumber)
        }
        
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            // Frame injection happens in Image.getPlanes() hook
            original?.onCaptureCompleted(session, request, result)
        }
        
        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: android.hardware.camera2.CaptureFailure
        ) {
            original?.onCaptureFailed(session, request, failure)
        }
    }

    private class InjectingCameraCallback(
        private val original: CameraDevice.Callback?,
        private val cameraId: String
    ) : CameraDevice.Callback() {
        
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera $cameraId opened for injection")
            original?.onOpened(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera $cameraId disconnected")
            original?.onDisconnected(camera)
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera $cameraId error: $error")
            original?.onError(camera, error)
        }
    }

    private fun shouldInjectCamera(cameraId: String): Boolean {
        val target = InjectionConfig.getTargetCamera()
        return when (target) {
            InjectionConfig.FRONT_CAMERA -> cameraId == "1"
            InjectionConfig.BACK_CAMERA -> cameraId == "0"
            else -> false
        }
    }
}

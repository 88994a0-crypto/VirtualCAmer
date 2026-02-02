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

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
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
                    val original = param.args[1] as? CameraDevice.Callback
                    param.args[1] = InjectingCameraCallback(original)
                }
            }
        )

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

        XposedHelpers.findAndHookMethod(
            "android.media.Image",
            lpparam.classLoader,
            "getPlanes",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val planes = param.result as? Array<Image.Plane> ?: return
                    injectFrameData(planes)
                }
            }
        )
    }

    private fun injectFrameData(planes: Array<Image.Plane>) {
        val rtmpFrame = frameProvider.getLatestFrameYUV()
        if (rtmpFrame == null || planes.isEmpty()) {
            return
        }
        try {
            val buffer: ByteBuffer = planes[0].buffer
            buffer.clear()
            val length = minOf(rtmpFrame.size, buffer.capacity())
            buffer.put(rtmpFrame, 0, length)
        } catch (exception: Exception) {
            Log.e("RTMP_INJECT", "Failed to inject frame", exception)
        }
    }

    private class InjectingCaptureCallback(
        private val original: CameraCaptureSession.CaptureCallback?
    ) : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            original?.onCaptureCompleted(session, request, result)
        }
    }

    private class InjectingCameraCallback(
        private val original: CameraDevice.Callback?
    ) : CameraDevice.Callback() {
        override fun onOpened(camera: CameraDevice) {
            original?.onOpened(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            original?.onDisconnected(camera)
        }

        override fun onError(camera: CameraDevice, error: Int) {
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

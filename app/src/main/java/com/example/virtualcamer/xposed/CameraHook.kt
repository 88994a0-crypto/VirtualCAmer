package com.example.virtualcamer.xposed

import android.hardware.Camera
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object CameraHook {
    private val frameProvider = RtmpFrameProvider.getInstance()

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "android.hardware.Camera",
            lpparam.classLoader,
            "open",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val cameraId = param.args[0] as? Int ?: return
                    val camera = param.result as? Camera ?: return
                    if (shouldInjectCamera(cameraId)) {
                        Log.d("RTMP_INJECT", "Injecting camera $cameraId")
                        setupInjection(camera, cameraId)
                    }
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "android.hardware.Camera",
            lpparam.classLoader,
            "setPreviewCallback",
            Camera.PreviewCallback::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val original = param.args[0] as? Camera.PreviewCallback
                    param.args[0] = InjectingPreviewCallback(original)
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "android.hardware.Camera",
            lpparam.classLoader,
            "setPreviewCallbackWithBuffer",
            Camera.PreviewCallback::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val original = param.args[0] as? Camera.PreviewCallback
                    param.args[0] = InjectingPreviewCallback(original)
                }
            }
        )
    }

    private fun setupInjection(camera: Camera, cameraId: Int) {
        Log.d("RTMP_INJECT", "Camera $cameraId ready for injection (${camera.parameters.previewSize})")
    }

    private class InjectingPreviewCallback(
        private val original: Camera.PreviewCallback?
    ) : Camera.PreviewCallback {
        override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
            if (camera == null || data == null) {
                original?.onPreviewFrame(data, camera)
                return
            }
            val previewSize = camera.parameters?.previewSize
            val injected = frameProvider.getLatestFrame(data.size, previewSize)
            if (injected != null) {
                original?.onPreviewFrame(injected, camera)
            } else {
                original?.onPreviewFrame(data, camera)
            }
        }
    }

    private fun shouldInjectCamera(cameraId: Int): Boolean {
        return when (InjectionConfig.getTargetCamera()) {
            InjectionConfig.FRONT_CAMERA -> cameraId == 1
            InjectionConfig.BACK_CAMERA -> cameraId == 0
            else -> false
        }
    }
}

package com.example.virtualcamer.xposed

import android.media.Image
import android.util.Log
import androidx.camera.core.ImageProxy
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.nio.ByteBuffer

object CameraXHook {
    private val frameProvider = RtmpFrameProvider.getInstance()
    private const val TAG = "CameraXHook"
    
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Log.d(TAG, "Initializing CameraX hooks for ${lpparam.packageName}")
            
            // Hook various CameraX components
            hookImageAnalysis(lpparam)
            hookImageCapture(lpparam)
            hookPreview(lpparam)
            
            Log.d(TAG, "CameraX hooks initialized successfully")
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "CameraX not found in ${lpparam.packageName}, skipping")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CameraX hooks", e)
        }
    }
    
    private fun hookImageAnalysis(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val imageAnalysisClass = XposedHelpers.findClass(
                "androidx.camera.core.ImageAnalysis",
                lpparam.classLoader
            )
            
            XposedHelpers.findAndHookMethod(
                imageAnalysisClass,
                "setAnalyzer",
                java.util.concurrent.Executor::class.java,
                "androidx.camera.core.ImageAnalysis\$Analyzer",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val originalAnalyzer = param.args[1]
                            param.args[1] = createWrappedAnalyzer(originalAnalyzer, lpparam.classLoader)
                            Log.d(TAG, "Wrapped ImageAnalysis.Analyzer")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error wrapping analyzer", e)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "Could not hook ImageAnalysis: ${e.message}")
        }
    }
    
    private fun createWrappedAnalyzer(original: Any, classLoader: ClassLoader): Any {
        val analyzerClass = XposedHelpers.findClass(
            "androidx.camera.core.ImageAnalysis\$Analyzer",
            classLoader
        )
        
        return java.lang.reflect.Proxy.newProxyInstance(
            classLoader,
            arrayOf(analyzerClass)
        ) { _, method, args ->
            if (method.name == "analyze" && args != null && args.isNotEmpty()) {
                val imageProxy = args[0]
                injectImageProxy(imageProxy)
            }
            method.invoke(original, *(args ?: emptyArray()))
        }
    }
    
    private fun hookImageCapture(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val imageCaptureClass = XposedHelpers.findClass(
                "androidx.camera.core.ImageCapture",
                lpparam.classLoader
            )
            
            // Hook the image capture result
            XposedHelpers.findAndHookMethod(
                "androidx.camera.core.ImageCapture\$OnImageCapturedCallback",
                lpparam.classLoader,
                "onCaptureSuccess",
                "androidx.camera.core.ImageProxy",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val imageProxy = param.args[0]
                            injectImageProxy(imageProxy)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error injecting capture image", e)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "Could not hook ImageCapture: ${e.message}")
        }
    }
    
    private fun hookPreview(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook Preview SurfaceRequest
            val previewClass = XposedHelpers.findClass(
                "androidx.camera.core.Preview",
                lpparam.classLoader
            )
            
            XposedHelpers.findAndHookMethod(
                "androidx.camera.core.Preview\$SurfaceProvider",
                lpparam.classLoader,
                "onSurfaceRequested",
                "androidx.camera.core.SurfaceRequest",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        Log.d(TAG, "Preview surface requested")
                    }
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "Could not hook Preview: ${e.message}")
        }
    }
    
    private fun injectImageProxy(imageProxy: Any) {
        try {
            // Get the underlying Image
            val image = XposedHelpers.callMethod(imageProxy, "getImage") as? Image ?: return
            
            val planes = image.planes
            if (planes.isEmpty()) {
                return
            }
            
            // Get frame dimensions
            val width = image.width
            val height = image.height
            
            // Get RTMP frame resized to match
            val rtmpFrame = frameProvider.getLatestFrameYUVWithSize(width, height)
            if (rtmpFrame == null) {
                Log.v(TAG, "No RTMP frame available")
                return
            }
            
            // Inject into Y plane (and UV if available)
            injectIntoPlanes(planes, rtmpFrame, width, height)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting into ImageProxy", e)
        }
    }
    
    private fun injectIntoPlanes(planes: Array<Image.Plane>, rtmpFrame: ByteArray, width: Int, height: Int) {
        try {
            // Y plane
            val yPlane = planes[0]
            val yBuffer = yPlane.buffer
            
            if (!yBuffer.isReadOnly) {
                yBuffer.rewind()
                val ySize = width * height
                val copySize = minOf(ySize, rtmpFrame.size, yBuffer.remaining())
                yBuffer.put(rtmpFrame, 0, copySize)
            }
            
            // UV planes (if they exist)
            if (planes.size >= 3) {
                val frameSize = width * height
                val uvPlaneSize = frameSize / 4
                
                // U plane
                if (planes.size > 1 && !planes[1].buffer.isReadOnly) {
                    val uBuffer = planes[1].buffer
                    uBuffer.rewind()
                    
                    // Extract U values from NV21 (interleaved VU)
                    // In NV21: after Y, we have VUVUVU...
                    val uData = ByteArray(uvPlaneSize)
                    for (i in 0 until uvPlaneSize) {
                        val srcIdx = frameSize + i * 2 + 1
                        if (srcIdx < rtmpFrame.size) {
                            uData[i] = rtmpFrame[srcIdx]
                        }
                    }
                    uBuffer.put(uData, 0, minOf(uData.size, uBuffer.remaining()))
                }
                
                // V plane
                if (planes.size > 2 && !planes[2].buffer.isReadOnly) {
                    val vBuffer = planes[2].buffer
                    vBuffer.rewind()
                    
                    // Extract V values from NV21
                    val vData = ByteArray(uvPlaneSize)
                    for (i in 0 until uvPlaneSize) {
                        val srcIdx = frameSize + i * 2
                        if (srcIdx < rtmpFrame.size) {
                            vData[i] = rtmpFrame[srcIdx]
                        }
                    }
                    vBuffer.put(vData, 0, minOf(vData.size, vBuffer.remaining()))
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting into planes", e)
        }
    }
}

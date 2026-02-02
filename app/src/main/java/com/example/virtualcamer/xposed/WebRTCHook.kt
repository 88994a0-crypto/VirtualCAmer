package com.example.virtualcamer.xposed

import android.media.Image
import android.os.Build
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

/**
 * Hooks for WebRTC camera access in Chrome and other browsers
 * Targets getUserMedia, MediaStream, and related WebRTC APIs
 */
object WebRTCHook {
    private val frameProvider = RtmpFrameProvider.getInstance()
    private const val TAG = "WebRTCHook"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Log.d(TAG, "Initializing WebRTC hooks for ${lpparam.packageName}")
            
            // Hook WebView camera permissions
            hookWebChromeClient(lpparam)
            
            // Hook WebRTC native components if available
            hookWebRTCNative(lpparam)
            
            // Hook MediaRecorder for video capture
            hookMediaRecorder(lpparam)
            
            Log.d(TAG, "WebRTC hooks initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC hooks", e)
        }
    }

    /**
     * Hook WebChromeClient for permission requests
     */
    private fun hookWebChromeClient(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val webChromeClientClass = XposedHelpers.findClass(
                "android.webkit.WebChromeClient",
                lpparam.classLoader
            )

            // Hook onPermissionRequest to intercept camera permission
            XposedHelpers.findAndHookMethod(
                webChromeClientClass,
                "onPermissionRequest",
                "android.webkit.PermissionRequest",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val request = param.args[0]
                            val resources = XposedHelpers.callMethod(request, "getResources") as? Array<*>
                            
                            resources?.forEach { resource ->
                                if (resource.toString().contains("VIDEO_CAPTURE")) {
                                    Log.d(TAG, "Camera permission requested via WebRTC")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onPermissionRequest hook", e)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "Could not hook WebChromeClient: ${e.message}")
        }
    }

    /**
     * Hook WebRTC native video capturer
     * Chrome uses native code for camera access
     */
    private fun hookWebRTCNative(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Try to find Chrome's video capturer classes
            val possibleClasses = listOf(
                "org.webrtc.Camera1Enumerator",
                "org.webrtc.Camera2Enumerator",
                "org.webrtc.VideoCapturer",
                "org.webrtc.CameraVideoCapturer",
                "org.webrtc.Camera1Capturer",
                "org.webrtc.Camera2Capturer"
            )

            possibleClasses.forEach { className ->
                try {
                    val capturerClass = XposedHelpers.findClass(className, lpparam.classLoader)
                    hookWebRTCCapturer(capturerClass, lpparam)
                    Log.d(TAG, "Hooked WebRTC class: $className")
                } catch (e: ClassNotFoundException) {
                    // Class not found, try next
                }
            }

            // Hook VideoFrame class if available
            hookVideoFrame(lpparam)
            
        } catch (e: Exception) {
            Log.d(TAG, "WebRTC native classes not found (this is normal for non-WebRTC apps)")
        }
    }

    /**
     * Hook specific WebRTC capturer class
     */
    private fun hookWebRTCCapturer(capturerClass: Class<*>, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook onFrame callback
            XposedBridge.hookAllMethods(capturerClass, "onFrame", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        // VideoFrame is the first parameter
                        val videoFrame = param.args[0]
                        injectIntoVideoFrame(videoFrame)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error injecting into VideoFrame", e)
                    }
                }
            })
        } catch (e: Exception) {
            Log.d(TAG, "Could not hook capturer methods: ${e.message}")
        }
    }

    /**
     * Hook WebRTC VideoFrame for frame injection
     */
    private fun hookVideoFrame(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val videoFrameClass = XposedHelpers.findClass("org.webrtc.VideoFrame", lpparam.classLoader)
            val bufferClass = XposedHelpers.findClass("org.webrtc.VideoFrame\$Buffer", lpparam.classLoader)

            // Hook getBuffer to return our injected buffer
            XposedHelpers.findAndHookMethod(
                videoFrameClass,
                "getBuffer",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val buffer = param.result
                            if (buffer != null) {
                                injectIntoBuffer(buffer)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in getBuffer hook", e)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "Could not hook VideoFrame: ${e.message}")
        }
    }

    /**
     * Hook MediaRecorder for video recording
     */
    private fun hookMediaRecorder(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.media.MediaRecorder",
                lpparam.classLoader,
                "setVideoSource",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val source = param.args[0] as Int
                        // CAMERA = 1
                        if (source == 1) {
                            Log.d(TAG, "MediaRecorder camera source detected")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "Could not hook MediaRecorder: ${e.message}")
        }
    }

    /**
     * Inject RTMP frame data into WebRTC VideoFrame
     */
    private fun injectIntoVideoFrame(videoFrame: Any) {
        try {
            // Get frame dimensions
            val width = XposedHelpers.callMethod(videoFrame, "getWidth") as? Int ?: return
            val height = XposedHelpers.callMethod(videoFrame, "getHeight") as? Int ?: return
            
            // Get buffer
            val buffer = XposedHelpers.callMethod(videoFrame, "getBuffer") ?: return
            
            injectIntoBuffer(buffer, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting into VideoFrame", e)
        }
    }

    /**
     * Inject into WebRTC buffer
     */
    private fun injectIntoBuffer(buffer: Any, width: Int = 0, height: Int = 0) {
        try {
            // Try to get dimensions if not provided
            val w = if (width > 0) width else (XposedHelpers.callMethod(buffer, "getWidth") as? Int ?: return)
            val h = if (height > 0) height else (XposedHelpers.callMethod(buffer, "getHeight") as? Int ?: return)
            
            // Get RTMP frame
            val rtmpFrame = frameProvider.getLatestFrameYUVWithSize(w, h) ?: return
            
            // Try to access I420 buffer (most common format in WebRTC)
            try {
                val dataY = XposedHelpers.callMethod(buffer, "getDataY") as? java.nio.ByteBuffer
                val dataU = XposedHelpers.callMethod(buffer, "getDataU") as? java.nio.ByteBuffer
                val dataV = XposedHelpers.callMethod(buffer, "getDataV") as? java.nio.ByteBuffer

                if (dataY != null && dataU != null && dataV != null) {
                    injectI420(dataY, dataU, dataV, rtmpFrame, w, h)
                }
            } catch (e: Exception) {
                // Not I420, try other formats
                Log.v(TAG, "Not I420 buffer: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting into buffer", e)
        }
    }

    /**
     * Inject NV21 data into I420 buffers (WebRTC format)
     */
    private fun injectI420(
        dataY: java.nio.ByteBuffer,
        dataU: java.nio.ByteBuffer,
        dataV: java.nio.ByteBuffer,
        nv21Frame: ByteArray,
        width: Int,
        height: Int
    ) {
        try {
            val frameSize = width * height
            val uvPlaneSize = frameSize / 4

            // Y plane - direct copy
            dataY.clear()
            if (dataY.remaining() >= frameSize && nv21Frame.size >= frameSize) {
                dataY.put(nv21Frame, 0, frameSize)
            }

            // NV21 to I420: de-interleave VU to separate U and V
            dataU.clear()
            dataV.clear()

            for (i in 0 until uvPlaneSize) {
                val srcIdxV = frameSize + i * 2      // V is at even positions
                val srcIdxU = frameSize + i * 2 + 1  // U is at odd positions
                
                if (srcIdxV < nv21Frame.size && srcIdxU < nv21Frame.size) {
                    if (dataV.hasRemaining()) dataV.put(nv21Frame[srcIdxV])
                    if (dataU.hasRemaining()) dataU.put(nv21Frame[srcIdxU])
                }
            }
            
            Log.v(TAG, "Injected I420 frame: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting I420", e)
        }
    }
}

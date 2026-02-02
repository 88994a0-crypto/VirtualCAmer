package com.example.virtualcamer.xposed

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedInit : IXposedHookLoadPackage {
    
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Don't hook our own app
        if (lpparam.packageName == MODULE_PACKAGE) {
            return
        }
        
        // Check if injection is enabled
        if (!InjectionConfig.isEnabled()) {
            return
        }
        
        // Check if this package should be injected
        if (!InjectionConfig.shouldInject(lpparam.packageName)) {
            return
        }

        Log.d(TAG, "Initializing VirtualCAmer hooks for ${lpparam.packageName}")
        
        try {
            // Initialize RTMP connection
            initializeRtmpStream()
            
            // Determine which hooks to apply based on package
            val isBrowser = isBrowserApp(lpparam.packageName)
            val isCameraApp = isCameraApp(lpparam.packageName)
            
            // Hook camera APIs (for all apps)
            hookCameraAPIs(lpparam)
            
            // Additional WebRTC hooks for browsers
            if (isBrowser) {
                Log.d(TAG, "Detected browser app, enabling WebRTC hooks")
                WebRTCHook.hook(lpparam)
            }
            
            Log.d(TAG, "VirtualCAmer hooks successfully initialized for ${lpparam.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize hooks for ${lpparam.packageName}", e)
        }
    }
    
    private fun hookCameraAPIs(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook legacy Camera API
            CameraHook.hook(lpparam)
            Log.d(TAG, "Legacy Camera API hooks initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hook legacy Camera API", e)
        }
        
        try {
            // Hook Camera2 API
            Camera2Hook.hook(lpparam)
            Log.d(TAG, "Camera2 API hooks initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hook Camera2 API", e)
        }
        
        try {
            // Hook CameraX API
            CameraXHook.hook(lpparam)
            Log.d(TAG, "CameraX API hooks initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hook CameraX API", e)
        }
    }
    
    private fun initializeRtmpStream() {
        val rtmpUrl = InjectionConfig.getRtmpUrl()
        
        if (rtmpUrl.isEmpty()) {
            Log.w(TAG, "RTMP URL not configured")
            return
        }
        
        // Connect to RTMP stream if not already connected
        val provider = RtmpFrameProvider.getInstance()
        if (!provider.isConnected()) {
            Log.d(TAG, "Connecting to RTMP stream: $rtmpUrl")
            try {
                provider.connect(rtmpUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to RTMP stream", e)
            }
        }
    }
    
    /**
     * Check if package is a browser/WebView app
     */
    private fun isBrowserApp(packageName: String): Boolean {
        val browserPackages = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.microsoft.emmx",        // Edge
            "com.brave.browser",
            "com.kiwibrowser.browser",
            "com.duckduckgo.mobile.android",
            "org.chromium.webview_shell" // WebView test app
        )
        return browserPackages.contains(packageName)
    }
    
    /**
     * Check if package is a camera app
     */
    private fun isCameraApp(packageName: String): Boolean {
        return packageName.contains("camera", ignoreCase = true) ||
               packageName == "com.android.camera2" ||
               packageName == "com.google.android.GoogleCamera"
    }

    companion object {
        private const val TAG = "VirtualCAmer"
        const val MODULE_PACKAGE = "com.example.virtualcamer"
    }
}

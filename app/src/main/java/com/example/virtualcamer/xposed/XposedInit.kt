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
            
            // Hook camera APIs
            CameraHook.hook(lpparam)
            Camera2Hook.hook(lpparam)
            CameraXHook.hook(lpparam)
            
            Log.d(TAG, "VirtualCAmer hooks successfully initialized for ${lpparam.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize hooks for ${lpparam.packageName}", e)
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
            provider.connect(rtmpUrl)
        }
    }

    companion object {
        private const val TAG = "VirtualCAmer"
        const val MODULE_PACKAGE = "com.example.virtualcamer"
    }
}

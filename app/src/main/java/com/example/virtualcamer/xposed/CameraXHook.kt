package com.example.virtualcamer.xposed

import android.util.Log
import de.robv.android.xposed.callbacks.XC_LoadPackage

object CameraXHook {
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.d("RTMP_INJECT", "CameraX hook initialized for ${lpparam.packageName}")
    }
}

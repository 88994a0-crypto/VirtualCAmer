package com.example.virtualcamer.xposed

import com.example.virtualcamer.BuildConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedInit : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == MODULE_PACKAGE) {
            return
        }
        if (!InjectionConfig.isEnabled()) {
            return
        }
        if (!InjectionConfig.shouldInject(lpparam.packageName)) {
            return
        }

        CameraHook.hook(lpparam)
        Camera2Hook.hook(lpparam)
        CameraXHook.hook(lpparam)
    }

    companion object {
        const val MODULE_PACKAGE = BuildConfig.APPLICATION_ID
    }
}

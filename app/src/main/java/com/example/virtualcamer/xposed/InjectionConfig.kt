package com.example.virtualcamer.xposed

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import de.robv.android.xposed.XSharedPreferences

class InjectionConfig(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveConfig(rtmpUrl: String, targetCamera: Int, enabled: Boolean) {
        prefs.edit()
            .putString(KEY_RTMP_URL, rtmpUrl)
            .putInt(KEY_TARGET_CAMERA, targetCamera)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        broadcastConfigChange(context = context)
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun getTargetCamera(): Int =
        prefs.getInt(KEY_TARGET_CAMERA, BACK_CAMERA)

    fun getRtmpUrl(): String = prefs.getString(KEY_RTMP_URL, "") ?: ""

    private fun broadcastConfigChange(context: Context) {
        val intent = Intent(ACTION_CONFIG_CHANGED)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    companion object {
        const val PREFS_NAME = "rtmp_injection_config"
        const val FRONT_CAMERA = 1
        const val BACK_CAMERA = 0
        const val ACTION_CONFIG_CHANGED = "com.example.virtualcamer.CONFIG_CHANGED"

        private const val KEY_RTMP_URL = "rtmp_url"
        private const val KEY_TARGET_CAMERA = "target_camera"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALLOWED_APPS = "allowed_packages"

        fun isEnabled(): Boolean {
            return sharedPrefs().getBoolean(KEY_ENABLED, false)
        }

        fun getTargetCamera(): Int {
            return sharedPrefs().getInt(KEY_TARGET_CAMERA, BACK_CAMERA)
        }

        fun getRtmpUrl(): String {
            return sharedPrefs().getString(KEY_RTMP_URL, "") ?: ""
        }

        fun shouldInject(packageName: String): Boolean {
            val allowed = sharedPrefs().getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()
            return allowed.isEmpty() || allowed.contains(packageName)
        }

        private fun sharedPrefs(): XSharedPreferences {
            val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, PREFS_NAME)
            prefs.makeWorldReadable()
            prefs.reload()
            return prefs
        }
    }
}

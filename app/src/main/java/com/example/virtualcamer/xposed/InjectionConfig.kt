package com.example.virtualcamer.xposed

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.example.virtualcamer.BuildConfig
import com.example.virtualcamer.provider.ConfigProvider

class InjectionConfig(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveConfig(
        rtmpUrl: String,
        targetCamera: Int,
        enabled: Boolean,
        allowedPackages: Set<String> = emptySet()
    ) {
        prefs.edit()
            .putString(KEY_RTMP_URL, rtmpUrl)
            .putInt(KEY_TARGET_CAMERA, targetCamera)
            .putBoolean(KEY_ENABLED, enabled)
            .putStringSet(KEY_ALLOWED_APPS, allowedPackages)
            .apply()

        broadcastConfigChange(context)
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun getTargetCamera(): Int = prefs.getInt(KEY_TARGET_CAMERA, BACK_CAMERA)

    fun getRtmpUrl(): String = prefs.getString(KEY_RTMP_URL, "") ?: ""
    
    fun getAllowedPackages(): Set<String> = prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()

    private fun broadcastConfigChange(context: Context) {
        val intent = Intent(ACTION_CONFIG_CHANGED)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "InjectionConfig"
        const val PREFS_NAME = "rtmp_injection_config"
        const val FRONT_CAMERA = 1
        const val BACK_CAMERA = 0
        const val ACTION_CONFIG_CHANGED = "com.example.virtualcamer.CONFIG_CHANGED"

        private const val KEY_RTMP_URL = "rtmp_url"
        private const val KEY_TARGET_CAMERA = "target_camera"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALLOWED_APPS = "allowed_packages"

        /**
         * Android 11+ compatible config access using ContentProvider
         */
        fun isEnabled(): Boolean {
            return getConfigBoolean(KEY_ENABLED, false)
        }

        fun getTargetCamera(): Int {
            return getConfigInt(KEY_TARGET_CAMERA, BACK_CAMERA)
        }

        fun getRtmpUrl(): String {
            return getConfigString(KEY_RTMP_URL, "")
        }

        fun shouldInject(packageName: String): Boolean {
            val allowed = getConfigStringSet(KEY_ALLOWED_APPS)
            // If no apps specified, inject into all
            return allowed.isEmpty() || allowed.contains(packageName)
        }

        /**
         * Get String value from ContentProvider (Android 11+ compatible)
         */
        private fun getConfigString(key: String, defaultValue: String): String {
            return try {
                val context = getSystemContext() ?: return defaultValue
                val uri = ConfigProvider.getConfigUri(BuildConfig.APPLICATION_ID, key)
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val valueIndex = it.getColumnIndex("value")
                        if (valueIndex >= 0) {
                            return it.getString(valueIndex) ?: defaultValue
                        }
                    }
                }
                defaultValue
            } catch (e: Exception) {
                Log.e(TAG, "Error reading config string: $key", e)
                defaultValue
            }
        }

        /**
         * Get Int value from ContentProvider
         */
        private fun getConfigInt(key: String, defaultValue: Int): Int {
            return try {
                val context = getSystemContext() ?: return defaultValue
                val uri = ConfigProvider.getConfigUri(BuildConfig.APPLICATION_ID, key)
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val valueIndex = it.getColumnIndex("value")
                        if (valueIndex >= 0) {
                            return it.getString(valueIndex)?.toIntOrNull() ?: defaultValue
                        }
                    }
                }
                defaultValue
            } catch (e: Exception) {
                Log.e(TAG, "Error reading config int: $key", e)
                defaultValue
            }
        }

        /**
         * Get Boolean value from ContentProvider
         */
        private fun getConfigBoolean(key: String, defaultValue: Boolean): Boolean {
            return try {
                val context = getSystemContext() ?: return defaultValue
                val uri = ConfigProvider.getConfigUri(BuildConfig.APPLICATION_ID, key)
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val valueIndex = it.getColumnIndex("value")
                        if (valueIndex >= 0) {
                            return it.getString(valueIndex)?.toBoolean() ?: defaultValue
                        }
                    }
                }
                defaultValue
            } catch (e: Exception) {
                Log.e(TAG, "Error reading config boolean: $key", e)
                defaultValue
            }
        }

        /**
         * Get StringSet value from ContentProvider
         */
        private fun getConfigStringSet(key: String): Set<String> {
            return try {
                val context = getSystemContext() ?: return emptySet()
                val uri = ConfigProvider.getConfigUri(BuildConfig.APPLICATION_ID, key)
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val valueIndex = it.getColumnIndex("value")
                        if (valueIndex >= 0) {
                            val value = it.getString(valueIndex) ?: return emptySet()
                            return value.split(",").filter { s -> s.isNotBlank() }.toSet()
                        }
                    }
                }
                emptySet()
            } catch (e: Exception) {
                Log.e(TAG, "Error reading config stringset: $key", e)
                emptySet()
            }
        }

        /**
         * Get system context for ContentResolver access
         */
        private fun getSystemContext(): Context? {
            return try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
                currentApplicationMethod.invoke(null) as? Context
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get system context", e)
                null
            }
        }
    }
}

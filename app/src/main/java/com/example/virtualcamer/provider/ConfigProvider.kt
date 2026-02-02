package com.example.virtualcamer.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log

/**
 * ContentProvider for cross-process configuration access on Android 11+
 * Replaces deprecated XSharedPreferences.makeWorldReadable()
 */
class ConfigProvider : ContentProvider() {

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "config/#", CONFIG_ITEM)
        addURI(AUTHORITY, "config", CONFIG_ALL)
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return null
        val cursor = MatrixCursor(arrayOf(KEY_NAME, KEY_VALUE, KEY_TYPE))

        return when (uriMatcher.match(uri)) {
            CONFIG_ALL -> {
                // Return all config values
                prefs.all.forEach { (key, value) ->
                    when (value) {
                        is String -> cursor.addRow(arrayOf(key, value, TYPE_STRING))
                        is Int -> cursor.addRow(arrayOf(key, value.toString(), TYPE_INT))
                        is Boolean -> cursor.addRow(arrayOf(key, value.toString(), TYPE_BOOLEAN))
                        is Long -> cursor.addRow(arrayOf(key, value.toString(), TYPE_LONG))
                        is Float -> cursor.addRow(arrayOf(key, value.toString(), TYPE_FLOAT))
                        is Set<*> -> cursor.addRow(arrayOf(key, value.joinToString(","), TYPE_STRING_SET))
                    }
                }
                cursor
            }
            CONFIG_ITEM -> {
                // Return specific config value
                val key = uri.lastPathSegment ?: return null
                val value = prefs.all[key]
                if (value != null) {
                    when (value) {
                        is String -> cursor.addRow(arrayOf(key, value, TYPE_STRING))
                        is Int -> cursor.addRow(arrayOf(key, value.toString(), TYPE_INT))
                        is Boolean -> cursor.addRow(arrayOf(key, value.toString(), TYPE_BOOLEAN))
                        is Long -> cursor.addRow(arrayOf(key, value.toString(), TYPE_LONG))
                        is Float -> cursor.addRow(arrayOf(key, value.toString(), TYPE_FLOAT))
                        is Set<*> -> cursor.addRow(arrayOf(key, value.joinToString(","), TYPE_STRING_SET))
                    }
                }
                cursor
            }
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        values ?: return null
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return null
        val key = values.getAsString(KEY_NAME) ?: return null
        val value = values.getAsString(KEY_VALUE) ?: return null
        val type = values.getAsString(KEY_TYPE) ?: TYPE_STRING

        prefs.edit().apply {
            when (type) {
                TYPE_STRING -> putString(key, value)
                TYPE_INT -> putInt(key, value.toIntOrNull() ?: 0)
                TYPE_BOOLEAN -> putBoolean(key, value.toBoolean())
                TYPE_LONG -> putLong(key, value.toLongOrNull() ?: 0L)
                TYPE_FLOAT -> putFloat(key, value.toFloatOrNull() ?: 0f)
                TYPE_STRING_SET -> putStringSet(key, value.split(",").toSet())
            }
            apply()
        }

        // Notify change
        context?.contentResolver?.notifyChange(uri, null)
        return uri
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        return if (insert(uri, values) != null) 1 else 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return 0
        val key = uri.lastPathSegment ?: return 0
        
        prefs.edit().remove(key).apply()
        context?.contentResolver?.notifyChange(uri, null)
        return 1
    }

    override fun getType(uri: Uri): String {
        return "vnd.android.cursor.dir/vnd.${AUTHORITY}.config"
    }

    companion object {
        const val AUTHORITY = "com.example.virtualcamer.config"
        const val PREFS_NAME = "rtmp_injection_config"
        
        private const val CONFIG_ALL = 1
        private const val CONFIG_ITEM = 2
        
        private const val KEY_NAME = "key"
        private const val KEY_VALUE = "value"
        private const val KEY_TYPE = "type"
        
        private const val TYPE_STRING = "string"
        private const val TYPE_INT = "int"
        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_LONG = "long"
        private const val TYPE_FLOAT = "float"
        private const val TYPE_STRING_SET = "stringset"

        fun getConfigUri(packageName: String): Uri {
            return Uri.parse("content://${packageName}.config/config")
        }

        fun getConfigUri(packageName: String, key: String): Uri {
            return Uri.parse("content://${packageName}.config/config/$key")
        }
    }
}

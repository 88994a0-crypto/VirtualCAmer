package com.example.virtualcamer.xposed

import android.util.Log

class RtmpStreamReader(private val rtmpUrl: String) {
    private var listener: ((ByteArray) -> Unit)? = null
    private var running = false
    private var worker: Thread? = null

    fun setFrameListener(listener: (ByteArray) -> Unit) {
        this.listener = listener
    }

    fun connect() {
        if (running) {
            return
        }
        running = true
        worker = Thread {
            Log.d("RTMP_INJECT", "RTMP reader started for $rtmpUrl")
            while (running) {
                try {
                    Thread.sleep(33)
                } catch (exception: InterruptedException) {
                    break
                }
            }
            Log.d("RTMP_INJECT", "RTMP reader stopped for $rtmpUrl")
        }.apply { start() }
    }

    fun disconnect() {
        running = false
        worker?.interrupt()
        worker = null
    }
}

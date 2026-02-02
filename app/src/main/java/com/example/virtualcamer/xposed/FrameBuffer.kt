package com.example.virtualcamer.xposed

import java.util.ArrayDeque

class FrameBuffer(private val capacity: Int) {
    private val queue = ArrayDeque<ByteArray>(capacity)

    @Synchronized
    fun addFrame(frame: ByteArray) {
        if (queue.size >= capacity) {
            queue.removeFirst()
        }
        queue.addLast(frame)
    }

    @Synchronized
    fun getLatestFrame(): ByteArray? {
        return queue.peekLast()
    }
}

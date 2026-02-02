package com.example.virtualcamer.xposed

import android.util.Log
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class FrameBuffer(private val capacity: Int) {
    private val frames = ArrayDeque<TimestampedFrame>(capacity)
    private val lock = ReentrantReadWriteLock()
    
    private var droppedFrames = 0L
    private var totalFrames = 0L
    
    data class TimestampedFrame(
        val data: ByteArray,
        val timestamp: Long,
        val width: Int,
        val height: Int,
        val sequenceNumber: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TimestampedFrame) return false
            return sequenceNumber == other.sequenceNumber
        }
        
        override fun hashCode(): Int = sequenceNumber.hashCode()
    }

    fun addFrame(frame: ByteArray, width: Int = 0, height: Int = 0) {
        lock.write {
            totalFrames++
            
            val timestamped = TimestampedFrame(
                data = frame,
                timestamp = System.nanoTime(),
                width = width,
                height = height,
                sequenceNumber = totalFrames
            )
            
            if (frames.size >= capacity) {
                val dropped = frames.removeFirst()
                droppedFrames++
                
                if (droppedFrames % 100 == 0L) {
                    val age = (timestamped.timestamp - dropped.timestamp) / 1_000_000
                    Log.w(TAG, "Dropped $droppedFrames frames, last frame age: ${age}ms")
                }
            }
            
            frames.addLast(timestamped)
        }
    }

    fun getLatestFrame(): ByteArray? {
        return lock.read {
            frames.lastOrNull()?.data
        }
    }
    
    fun getLatestFrameWithInfo(): TimestampedFrame? {
        return lock.read {
            frames.lastOrNull()
        }
    }
    
    fun getFrameForTimestamp(targetTimestamp: Long): TimestampedFrame? {
        return lock.read {
            if (frames.isEmpty()) return@read null
            
            // Find frame with timestamp closest to target
            frames.minByOrNull { Math.abs(it.timestamp - targetTimestamp) }
        }
    }
    
    fun clear() {
        lock.write {
            frames.clear()
        }
    }
    
    fun getStats(): BufferStats {
        return lock.read {
            BufferStats(
                currentSize = frames.size,
                capacity = capacity,
                totalFrames = totalFrames,
                droppedFrames = droppedFrames,
                dropRate = if (totalFrames > 0) droppedFrames.toFloat() / totalFrames else 0f
            )
        }
    }
    
    data class BufferStats(
        val currentSize: Int,
        val capacity: Int,
        val totalFrames: Long,
        val droppedFrames: Long,
        val dropRate: Float
    )
    
    companion object {
        private const val TAG = "FrameBuffer"
    }
}

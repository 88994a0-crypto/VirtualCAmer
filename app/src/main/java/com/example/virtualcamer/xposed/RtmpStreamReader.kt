package com.example.virtualcamer.xposed

import android.graphics.Bitmap
import android.util.Log
import org.bytedeco.javacv.AndroidFrameConverter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.util.concurrent.atomic.AtomicBoolean

class RtmpStreamReader(private val rtmpUrl: String) {
    private var listener: ((ByteArray) -> Unit)? = null
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var grabber: FFmpegFrameGrabber? = null
    private val converter = AndroidFrameConverter()
    
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelayMs = 2000L

    fun setFrameListener(listener: (ByteArray) -> Unit) {
        this.listener = listener
    }

    fun connect() {
        if (running.getAndSet(true)) {
            Log.w(TAG, "Already running")
            return
        }
        
        worker = Thread {
            Log.d(TAG, "RTMP reader thread started for $rtmpUrl")
            
            while (running.get() && reconnectAttempts < maxReconnectAttempts) {
                try {
                    initializeGrabber()
                    streamFrames()
                    reconnectAttempts = 0 // Reset on successful stream
                } catch (e: Exception) {
                    handleConnectionError(e)
                }
            }
            
            cleanup()
            Log.d(TAG, "RTMP reader thread stopped")
        }.apply { 
            name = "RtmpStreamReader-$rtmpUrl"
            start() 
        }
    }
    
    private fun initializeGrabber() {
        Log.d(TAG, "Initializing FFmpeg grabber for $rtmpUrl")
        
        grabber = FFmpegFrameGrabber(rtmpUrl).apply {
            format = "flv" // RTMP uses FLV container
            
            // Optimize for live streaming
            setOption("rtmp_live", "live")
            setOption("fflags", "nobuffer")
            setOption("flags", "low_delay")
            setOption("analyzeduration", "1000000") // 1 second
            setOption("probesize", "32768") // 32KB
            setOption("timeout", "5000000") // 5 second timeout
            
            // Video options
            imageWidth = 0 // Auto-detect
            imageHeight = 0
            pixelFormat = org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
            
            start()
        }
        
        Log.d(TAG, "FFmpeg grabber started: ${grabber?.imageWidth}x${grabber?.imageHeight} @ ${grabber?.frameRate}fps")
    }
    
    private fun streamFrames() {
        var frameCount = 0L
        val startTime = System.currentTimeMillis()
        var lastLogTime = startTime
        
        while (running.get()) {
            try {
                val frame: Frame? = grabber?.grab()
                
                if (frame == null) {
                    // End of stream or error
                    Log.w(TAG, "Received null frame, stream may have ended")
                    break
                }
                
                // Only process video frames
                if (frame.image != null) {
                    processVideoFrame(frame)
                    frameCount++
                    
                    // Log performance every 5 seconds
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 5000) {
                        val fps = frameCount / ((now - startTime) / 1000.0)
                        Log.d(TAG, "Streaming: %.2f fps, %d total frames".format(fps, frameCount))
                        lastLogTime = now
                    }
                }
                
            } catch (e: InterruptedException) {
                Log.d(TAG, "Stream interrupted")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error grabbing frame", e)
                throw e
            }
        }
    }
    
    private fun processVideoFrame(frame: Frame) {
        try {
            // Convert frame to Android Bitmap
            val bitmap: Bitmap = converter.convert(frame) ?: return
            
            // Convert bitmap to YUV420 (NV21 format for Android camera)
            val yuv = convertBitmapToNV21(bitmap)
            
            // Deliver to listener
            listener?.invoke(yuv)
            
            // Recycle bitmap to avoid memory leaks
            bitmap.recycle()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        }
    }
    
    private fun convertBitmapToNV21(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        
        // NV21 format: Y plane followed by interleaved VU
        val yuv = ByteArray(width * height * 3 / 2)
        encodeYUV420SP(yuv, argb, width, height)
        return yuv
    }
    
    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        
        for (j in 0 until height) {
            for (i in 0 until width) {
                val R = (argb[j * width + i] shr 16) and 0xff
                val G = (argb[j * width + i] shr 8) and 0xff
                val B = argb[j * width + i] and 0xff
                
                // RGB to YUV conversion (BT.601)
                val Y = ((66 * R + 129 * G + 25 * B + 128) shr 8) + 16
                val U = ((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128
                val V = ((112 * R - 94 * G - 18 * B + 128) shr 8) + 128
                
                // Clamp values
                yuv420sp[yIndex++] = Y.coerceIn(0, 255).toByte()
                
                // For NV21, interleave V and U (every other pixel in both dimensions)
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv420sp[uvIndex++] = V.coerceIn(0, 255).toByte()
                    yuv420sp[uvIndex++] = U.coerceIn(0, 255).toByte()
                }
            }
        }
    }
    
    private fun handleConnectionError(e: Exception) {
        reconnectAttempts++
        Log.e(TAG, "Connection error (attempt $reconnectAttempts/$maxReconnectAttempts)", e)
        
        cleanup()
        
        if (running.get() && reconnectAttempts < maxReconnectAttempts) {
            val delay = reconnectDelayMs * reconnectAttempts
            Log.d(TAG, "Reconnecting in ${delay}ms...")
            Thread.sleep(delay)
        }
    }
    
    private fun cleanup() {
        try {
            grabber?.stop()
            grabber?.release()
            grabber = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting RTMP stream")
        running.set(false)
        worker?.interrupt()
        cleanup()
        worker = null
    }
    
    companion object {
        private const val TAG = "RtmpStreamReader"
    }
}

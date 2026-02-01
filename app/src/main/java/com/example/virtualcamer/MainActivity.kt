package com.example.virtualcamer

import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.rtmp.RtmpDataSource
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private lateinit var serverInput: EditText
    private lateinit var streamKeyInput: EditText
    private lateinit var devicePathInput: EditText
    private lateinit var audioSwitch: SwitchMaterial
    private lateinit var videoSwitch: SwitchMaterial
    private lateinit var liveSwitch: SwitchMaterial
    private lateinit var softDecode: RadioButton
    private lateinit var hardDecode: RadioButton
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView
    private lateinit var videoPreview: TextureView

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var videoSurface: Surface? = null
    private val frameForwarder = ExoFrameForwarder(VirtualCameraBridge())
    private var lastStreamUrl: String? = null
    private var decoderMode: DecoderMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverInput = findViewById(R.id.serverInput)
        streamKeyInput = findViewById(R.id.streamKeyInput)
        devicePathInput = findViewById(R.id.devicePathInput)
        audioSwitch = findViewById(R.id.audioSwitch)
        videoSwitch = findViewById(R.id.videoSwitch)
        liveSwitch = findViewById(R.id.liveSwitch)
        softDecode = findViewById(R.id.softDecode)
        hardDecode = findViewById(R.id.hardDecode)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)
        videoPreview = findViewById(R.id.videoPreview)

        setupVirtualCamera()

        audioSwitch.isChecked = true
        videoSwitch.isChecked = true

        connectButton.setOnClickListener { applySettings() }
        audioSwitch.setOnCheckedChangeListener { _, _ -> updateTrackSelection() }
        videoSwitch.setOnCheckedChangeListener { _, _ -> updateTrackSelection() }
        liveSwitch.setOnCheckedChangeListener { _, isChecked -> togglePlayback(isChecked) }
        softDecode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchDecoderMode(DecoderMode.SOFTWARE)
            }
        }
        hardDecode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchDecoderMode(DecoderMode.HARDWARE)
            }
        }

        frameForwarder.setTextureView(videoPreview)
        videoPreview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                surfaceTexture.setOnFrameAvailableListener(
                    frameForwarder,
                    Handler(Looper.getMainLooper())
                )
                videoSurface = Surface(surfaceTexture)
                player?.setVideoSurface(videoSurface)
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) = Unit

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                surfaceTexture.setOnFrameAvailableListener(null as SurfaceTexture.OnFrameAvailableListener?)
                videoSurface?.release()
                videoSurface = null
                return true
            }
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun setupVirtualCamera() {
        val setupResult = RootCommandRunner.runAsRoot(
            "modprobe v4l2loopback devices=1 video_nr=0 card_label=\"Camera\" exclusive_caps=1"
        )
        if (!setupResult.success) {
            Log.w(TAG, "Virtual camera setup failed: ${setupResult.output}")
            statusText.text = getString(R.string.status_virtual_camera_failed)
            return
        }

        val devicePath = devicePathInput.text.toString().trim().ifBlank { "/dev/video0" }
        val deviceFile = File(devicePath)
        if (deviceFile.exists()) {
            Log.i(TAG, "Virtual camera device ready at $devicePath")
            statusText.text = getString(R.string.status_virtual_camera_ready, devicePath)
        } else {
            Log.w(TAG, "Virtual camera device missing at $devicePath")
            statusText.text = getString(R.string.status_virtual_camera_missing, devicePath)
        }
    }

    private fun applySettings() {
        val rtmpUrl = buildStreamUrl()
        if (rtmpUrl.isBlank()) {
            statusText.text = getString(R.string.status_missing_url)
            return
        }

        lastStreamUrl = rtmpUrl
        val requestedMode = if (hardDecode.isChecked) DecoderMode.HARDWARE else DecoderMode.SOFTWARE
        frameForwarder.updateDevicePath(devicePathInput.text.toString().trim())
        switchDecoderMode(requestedMode)
        updateTrackSelection()
        togglePlayback(liveSwitch.isChecked)
    }

    private fun buildPlayer(mode: DecoderMode, snapshot: PlaybackSnapshot?) {
        releasePlayer()

        val codecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoderInfos = MediaCodecUtil.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
            when (mode) {
                DecoderMode.HARDWARE -> decoderInfos.filterNot { info ->
                    info.name.contains("sw", ignoreCase = true) ||
                        info.name.contains("software", ignoreCase = true)
                }.ifEmpty { decoderInfos }
                DecoderMode.SOFTWARE -> decoderInfos.filter { info ->
                    info.name.contains("sw", ignoreCase = true) ||
                        info.name.contains("software", ignoreCase = true)
                }.ifEmpty { decoderInfos }
            }
        }

        val rendererFactory = DefaultRenderersFactory(this)
            .setMediaCodecSelector(codecSelector)
            .setEnableDecoderFallback(true)

        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector!!)
            .setRenderersFactory(rendererFactory)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        statusText.text = when (playbackState) {
                            Player.STATE_BUFFERING -> getString(R.string.status_buffering)
                            Player.STATE_READY -> getString(R.string.status_ready)
                            Player.STATE_ENDED -> getString(R.string.status_ended)
                            else -> getString(R.string.status_idle)
                        }
                    }
                })
            }

        if (videoPreview.isAvailable) {
            videoSurface = Surface(videoPreview.surfaceTexture)
            player?.setVideoSurface(videoSurface)
        }

        if (snapshot != null) {
            restorePlayback(snapshot)
        }
    }

    private fun prepareMedia(rtmpUrl: String) {
        val uri = Uri.parse(rtmpUrl)
        val mediaItem = MediaItem.fromUri(uri)

        prepareMedia(mediaItem)
    }

    private fun prepareMedia(mediaItem: MediaItem) {
        val dataSourceFactory: RtmpDataSourceFactory = RtmpDataSource.Factory()
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        player?.setMediaSource(mediaSource)
        player?.prepare()
        statusText.text = getString(R.string.status_connecting)
    }

    private fun restorePlayback(snapshot: PlaybackSnapshot) {
        prepareMedia(snapshot.mediaItem)
        if (snapshot.positionMs > 0) {
            player?.seekTo(snapshot.positionMs)
        }
        player?.playWhenReady = snapshot.playWhenReady
    }

    private fun switchDecoderMode(requestedMode: DecoderMode) {
        if (decoderMode == requestedMode && player != null) {
            return
        }
        val snapshot = capturePlaybackSnapshot()
        decoderMode = requestedMode
        buildPlayer(requestedMode, snapshot)
        if (snapshot == null) {
            val url = lastStreamUrl
            if (!url.isNullOrBlank()) {
                prepareMedia(url)
            }
        }
    }

    private fun capturePlaybackSnapshot(): PlaybackSnapshot? {
        val currentPlayer = player ?: return null
        val mediaItem = currentPlayer.currentMediaItem ?: return null
        return PlaybackSnapshot(
            mediaItem = mediaItem,
            positionMs = currentPlayer.currentPosition,
            playWhenReady = currentPlayer.playWhenReady
        )
    }

    private fun updateTrackSelection() {
        val selector = trackSelector ?: return
        val parameters = selector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !audioSwitch.isChecked)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !videoSwitch.isChecked)
            .build()

        selector.setParameters(parameters)
        player?.volume = if (audioSwitch.isChecked) 1f else 0f
    }

    private fun togglePlayback(shouldPlay: Boolean) {
        val currentPlayer = player ?: return
        if (shouldPlay) {
            currentPlayer.play()
            statusText.text = getString(R.string.status_playing)
        } else {
            currentPlayer.pause()
            statusText.text = getString(R.string.status_paused)
        }
    }

    private fun buildStreamUrl(): String {
        val baseUrl = serverInput.text.toString().trim()
        val streamKey = streamKeyInput.text.toString().trim()
        if (streamKey.isBlank()) {
            return baseUrl
        }
        return baseUrl.trimEnd('/') + "/" + streamKey
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        trackSelector = null
        videoSurface?.release()
        videoSurface = null
        frameForwarder.clear()
    }

    private class ExoFrameForwarder(
        private val virtualCameraBridge: VirtualCameraBridge
    ) : SurfaceTexture.OnFrameAvailableListener {
        private var textureView: TextureView? = null

        fun setTextureView(view: TextureView) {
            textureView = view
        }

        fun updateDevicePath(path: String) {
            virtualCameraBridge.configureDevice(path)
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
            val timestampNs = surfaceTexture.timestamp
            val bitmap = textureView?.bitmap
            if (bitmap == null) {
                Log.w(TAG, "Frame available but no bitmap to forward")
                return
            }
            virtualCameraBridge.sendFrame(bitmap, timestampNs)
        }

        fun clear() {
            textureView = null
        }
    }

    private class VirtualCameraBridge {
        private var devicePath: String = "/dev/video0"
        private var outputStream: FileOutputStream? = null

        fun configureDevice(path: String) {
            if (path.isNotBlank()) {
                devicePath = path
            }
            reconnect()
        }

        private fun reconnect() {
            outputStream?.closeQuietly()
            outputStream = null
            val deviceFile = File(devicePath)
            if (!deviceFile.exists()) {
                Log.w(TAG, "Virtual camera device not found at $devicePath")
                return
            }
            try {
                outputStream = FileOutputStream(deviceFile)
            } catch (error: IOException) {
                Log.e(TAG, "Failed to open virtual camera device at $devicePath", error)
            }
        }

        fun sendFrame(bitmap: android.graphics.Bitmap, timestampNs: Long) {
            Log.d(TAG, "Forwarding frame to virtual camera at $timestampNs for $devicePath")
            if (outputStream == null) {
                reconnect()
            }
            val stream = outputStream ?: return
            val width = bitmap.width
            val height = bitmap.height
            val argb = IntArray(width * height)
            bitmap.getPixels(argb, 0, width, 0, 0, width, height)

            val yuv = YuvConverter.convertArgbToI420(argb, width, height)
            try {
                stream.write(yuv)
            } catch (error: IOException) {
                Log.e(TAG, "Failed to write frame to virtual camera", error)
            }
        }

        private fun FileOutputStream.closeQuietly() {
            try {
                close()
            } catch (_: IOException) {
                Unit
            }
        }
    }

    private data class PlaybackSnapshot(
        val mediaItem: MediaItem,
        val positionMs: Long,
        val playWhenReady: Boolean
    )

    private enum class DecoderMode {
        HARDWARE,
        SOFTWARE
    }

    private typealias RtmpDataSourceFactory = RtmpDataSource.Factory

    companion object {
        private const val TAG = "MainActivity"
    }
}

private object YuvConverter {
    fun convertArgbToI420(argb: IntArray, width: Int, height: Int): ByteArray {
        val frameSize = width * height
        val yuv = ByteArray(frameSize + (frameSize / 2))
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + frameSize / 4

        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = argb[j * width + i]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = clampToByte(y)
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uIndex++] = clampToByte(u)
                    yuv[vIndex++] = clampToByte(v)
                }
            }
        }
        return yuv
    }

    private fun clampToByte(value: Int): Byte {
        return when {
            value < 0 -> 0
            value > 255 -> 255
            else -> value
        }.toByte()
    }
}

private object RootCommandRunner {
    data class Result(val success: Boolean, val output: String)

    fun runAsRoot(command: String): Result {
        return runCommand(listOf("su", "-c", command))
    }

    private fun runCommand(command: List<String>): Result {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }
            val exitCode = process.waitFor()
            Result(exitCode == 0, output.trim())
        } catch (error: IOException) {
            Result(false, error.message ?: "Unknown error")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Result(false, "Interrupted while running command")
        }
    }
}

package com.example.virtualcamer

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.TextureView
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.material.switchmaterial.SwitchMaterial
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var serverInput: EditText
    private lateinit var streamKeyInput: EditText
    private lateinit var devicePathInput: EditText
    private lateinit var audioSwitch: SwitchMaterial
    private lateinit var videoSwitch: SwitchMaterial
    private lateinit var liveSwitch: SwitchMaterial
    private lateinit var decoderGroup: RadioGroup
    private lateinit var softDecode: RadioButton
    private lateinit var hardDecode: RadioButton
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView
    private lateinit var videoPreview: TextureView

    private val rootExecutor = Executors.newSingleThreadExecutor()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameThread = HandlerThread("FrameCapture")
    private lateinit var frameHandler: Handler

    private var player: ExoPlayer? = null
    private val bridge = VirtualCameraBridge()
    private val frameWriter = FrameWriter(bridge)
    private lateinit var setupManager: DeviceSetupManager
    private var decoderMode: DecoderMode = DecoderMode.SOFTWARE
    private var activeDecoderMode: DecoderMode? = null
    private var frameWriteFailed = false
    private var frameIntervalMs = 66L
    private var lastFrameCaptureTime = 0L

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.filterValues { !it }.keys
            if (denied.isNotEmpty()) {
                updateStatus("Missing permissions: ${denied.joinToString()}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverInput = findViewById(R.id.serverInput)
        streamKeyInput = findViewById(R.id.streamKeyInput)
        devicePathInput = findViewById(R.id.devicePathInput)
        audioSwitch = findViewById(R.id.audioSwitch)
        videoSwitch = findViewById(R.id.videoSwitch)
        liveSwitch = findViewById(R.id.liveSwitch)
        decoderGroup = findViewById(R.id.decoderGroup)
        softDecode = findViewById(R.id.softDecode)
        hardDecode = findViewById(R.id.hardDecode)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)
        videoPreview = findViewById(R.id.videoPreview)

        frameThread.start()
        frameHandler = Handler(frameThread.looper)

        setupManager = DeviceSetupManager(rootExecutor) { message -> updateStatus(message) }
        setupManager.ensureLoopbackInstalled()
        setupManager.detectDevicePath()?.let { devicePathInput.setText(it) }

        requestRuntimePermissions()

        connectButton.setOnClickListener { applySettings() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFrameCapture()
        player?.release()
        player = null
        bridge.closeDevice()
        frameThread.quitSafely()
        rootExecutor.shutdown()
        ioExecutor.shutdown()
    }

    private fun requestRuntimePermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        )
    }

    private fun applySettings() {
        setupManager.ensureLoopbackInstalled()
        val serverUrl = serverInput.text.toString().trim()
        val streamKey = streamKeyInput.text.toString().trim()
        val devicePath = devicePathInput.text.toString().trim().ifEmpty {
            setupManager.detectDevicePath() ?: "/dev/video0"
        }
        devicePathInput.setText(devicePath)

        decoderMode = if (decoderGroup.checkedRadioButtonId == hardDecode.id) {
            DecoderMode.HARDWARE
        } else {
            DecoderMode.SOFTWARE
        }

        val rtmpUrl = buildRtmpUrl(serverUrl, streamKey)
        updateStatus(
            "Applying settings for $rtmpUrl (decoder=${decoderMode.label}, audio=${audioSwitch.isChecked}, " +
                "video=${videoSwitch.isChecked}, live=${liveSwitch.isChecked})"
        )

        if (!ensurePermissionsGranted()) {
            stopFrameCapture()
            stopPlayback()
            return
        }

        val deviceIssue = setupManager.getDeviceIssue(devicePath)
        if (deviceIssue != null) {
            val available = setupManager.listDevicePaths()
            val detail = if (available.isNotEmpty()) {
                "Available devices: ${available.joinToString()}"
            } else {
                "No /dev/video* devices found"
            }
            updateStatus("$deviceIssue. $detail")
            stopFrameCapture()
            bridge.closeDevice()
            return
        }

        if (!bridge.openDevice(devicePath)) {
            updateStatus("Unable to open $devicePath (check permissions and path)")
            stopFrameCapture()
            return
        }

        val rtmpIssue = if (liveSwitch.isChecked) validateRtmpInputs(serverUrl, streamKey) else null
        if (rtmpIssue != null) {
            updateStatus(rtmpIssue)
            stopPlayback()
        } else if (liveSwitch.isChecked && rtmpUrl.isNotBlank()) {
            startPlayback(rtmpUrl)
        } else {
            stopPlayback()
        }

        if (videoSwitch.isChecked) {
            frameWriteFailed = false
            startFrameCapture()
        } else {
            stopFrameCapture()
        }
    }

    private fun buildRtmpUrl(serverUrl: String, streamKey: String): String {
        if (serverUrl.isBlank()) {
            return ""
        }
        val separator = if (serverUrl.endsWith("/")) "" else "/"
        return if (streamKey.isBlank()) serverUrl else "$serverUrl$separator$streamKey"
    }

    private fun validateRtmpInputs(serverUrl: String, streamKey: String): String? {
        if (serverUrl.isBlank()) {
            return "RTMP server URL is required when live streaming is enabled"
        }
        return try {
            val uri = URI(serverUrl)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "rtmp" && scheme != "rtmps") {
                "RTMP server URL must start with rtmp:// or rtmps://"
            } else if (uri.host.isNullOrBlank()) {
                "RTMP server URL is missing a host"
            } else if (streamKey.isBlank() && !serverUrl.endsWith("/")) {
                "Stream key is empty. Add a stream key or include it in the RTMP URL"
            } else {
                null
            }
        } catch (exception: Exception) {
            "RTMP server URL is invalid: ${exception.message ?: "unknown error"}"
        }
    }

    private fun startPlayback(rtmpUrl: String) {
        val playerInstance = if (player == null || activeDecoderMode != decoderMode) {
            player?.release()
            buildPlayer(decoderMode).also {
                player = it
                activeDecoderMode = decoderMode
            }
        } else {
            player!!
        }

        val audioAttributes =
            AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()

        playerInstance.setAudioAttributes(audioAttributes, audioSwitch.isChecked)
        playerInstance.setVideoTextureView(videoPreview)
        playerInstance.setMediaItem(MediaItem.fromUri(rtmpUrl))
        playerInstance.prepare()
        playerInstance.playWhenReady = liveSwitch.isChecked
        checkRtmpReachability(rtmpUrl)
    }

    private fun buildPlayer(mode: DecoderMode): ExoPlayer {
        val trackSelector = DefaultTrackSelector(this)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                1500,
                2000
            )
            .build()
        val renderersFactory = DefaultRenderersFactory(this).setMediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
            val infos = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
            when (mode) {
                DecoderMode.SOFTWARE -> infos.filter { it.isSoftwareOnly }.ifEmpty { infos }
                DecoderMode.HARDWARE -> infos.filter { it.isHardwareAccelerated }.ifEmpty { infos }
            }
        }
        return ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .also { it.addListener(playerListener) }
    }

    private fun stopPlayback() {
        player?.stop()
    }

    private fun startFrameCapture() {
        frameHandler.removeCallbacksAndMessages(null)
        frameHandler.post(frameCaptureRunnable)
    }

    private fun stopFrameCapture() {
        frameHandler.removeCallbacksAndMessages(null)
    }

    private val frameCaptureRunnable = object : Runnable {
        override fun run() {
            if (videoSwitch.isChecked && videoPreview.isAvailable) {
                captureFrame()
            }
            frameHandler.postDelayed(this, frameIntervalMs)
        }
    }

    private fun captureFrame() {
        val now = SystemClock.uptimeMillis()
        if (now - lastFrameCaptureTime < frameIntervalMs) {
            return
        }
        lastFrameCaptureTime = now
        val width = videoPreview.width
        val height = videoPreview.height
        if (width <= 0 || height <= 0) {
            return
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        android.view.PixelCopy.request(
            videoPreview,
            bitmap,
            { result ->
                if (result == android.view.PixelCopy.SUCCESS) {
                    val success = frameWriter.writeBitmap(bitmap)
                    if (!success && !frameWriteFailed) {
                        frameWriteFailed = true
                        updateStatus("Failed to forward frame to virtual camera")
                    } else if (success && frameWriteFailed) {
                        frameWriteFailed = false
                        updateStatus("Virtual camera streaming recovered")
                    }
                }
                bitmap.recycle()
            },
            mainHandler
        )
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = "Status: $message"
        }
    }

    private fun ensurePermissionsGranted(): Boolean {
        val missing = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        ).filter { ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }

        if (missing.isNotEmpty()) {
            updateStatus("Missing permissions: ${missing.joinToString()}")
            return false
        }
        return true
    }

    private fun checkRtmpReachability(rtmpUrl: String) {
        val host = try {
            URI(rtmpUrl).host
        } catch (exception: Exception) {
            null
        }
        if (host.isNullOrBlank()) {
            return
        }
        ioExecutor.execute {
            try {
                InetAddress.getByName(host)
            } catch (exception: Exception) {
                updateStatus("Unable to resolve RTMP host: $host")
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            updateStatus("Playback error: ${error.errorCodeName}")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> updateStatus("Buffering RTMP stream...")
                Player.STATE_READY -> updateStatus("RTMP stream ready")
                Player.STATE_ENDED -> updateStatus("RTMP stream ended")
            }
        }

        override fun onTracksChanged(tracks: Player.Tracks) {
            val frameRate = player?.videoFormat?.frameRate ?: 0f
            if (frameRate > 0f) {
                frameIntervalMs = max(16L, (1000f / frameRate).toLong())
            }
        }
    }
}

private enum class DecoderMode(val label: String) {
    SOFTWARE("software"),
    HARDWARE("hardware")
}

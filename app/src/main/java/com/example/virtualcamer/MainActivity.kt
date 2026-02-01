package com.example.virtualcamer

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.TextureView
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.concurrent.Executors

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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameThread = HandlerThread("FrameCapture")
    private lateinit var frameHandler: Handler

    private var player: ExoPlayer? = null
    private val bridge = VirtualCameraBridge()
    private val frameWriter = FrameWriter(bridge)
    private lateinit var setupManager: DeviceSetupManager

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

        val decoderMode = if (decoderGroup.checkedRadioButtonId == hardDecode.id) {
            "hardware"
        } else {
            "software"
        }

        val rtmpUrl = buildRtmpUrl(serverUrl, streamKey)
        updateStatus(
            "Applying settings for $rtmpUrl (decoder=$decoderMode, audio=${audioSwitch.isChecked}, " +
                "video=${videoSwitch.isChecked}, live=${liveSwitch.isChecked})"
        )

        if (!bridge.openDevice(devicePath)) {
            updateStatus("Unable to open $devicePath")
        }

        if (liveSwitch.isChecked && rtmpUrl.isNotBlank()) {
            startPlayback(rtmpUrl)
        } else {
            stopPlayback()
        }

        if (videoSwitch.isChecked) {
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

    private fun startPlayback(rtmpUrl: String) {
        val playerInstance = player ?: ExoPlayer.Builder(this).build().also {
            player = it
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
            frameHandler.postDelayed(this, 200)
        }
    }

    private fun captureFrame() {
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
                    frameWriter.writeBitmap(bitmap)
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
}

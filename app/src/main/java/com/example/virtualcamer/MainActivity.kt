package com.example.virtualcamer

import android.os.Bundle
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.virtualcamer.xposed.InjectionConfig
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    private lateinit var rtmpUrlInput: EditText
    private lateinit var cameraSelection: RadioGroup
    private lateinit var frontCamera: RadioButton
    private lateinit var backCamera: RadioButton
    private lateinit var injectionToggle: SwitchMaterial
    private lateinit var statusText: TextView
    private lateinit var previewSurface: SurfaceView
    private lateinit var connectButton: Button
    private lateinit var injectionConfig: InjectionConfig

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rtmpUrlInput = findViewById(R.id.rtmpUrlInput)
        cameraSelection = findViewById(R.id.cameraSelection)
        frontCamera = findViewById(R.id.frontCamera)
        backCamera = findViewById(R.id.backCamera)
        injectionToggle = findViewById(R.id.injectionToggle)
        statusText = findViewById(R.id.statusText)
        previewSurface = findViewById(R.id.previewSurface)
        connectButton = findViewById(R.id.connectButton)

        injectionConfig = InjectionConfig(this)
        hydrateConfig()

        connectButton.setOnClickListener { applySettings() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    private fun hydrateConfig() {
        rtmpUrlInput.setText(injectionConfig.getRtmpUrl())
        when (injectionConfig.getTargetCamera()) {
            InjectionConfig.FRONT_CAMERA -> frontCamera.isChecked = true
            InjectionConfig.BACK_CAMERA -> backCamera.isChecked = true
        }
        injectionToggle.isChecked = injectionConfig.isEnabled()
    }

    private fun applySettings() {
        val rtmpUrl = rtmpUrlInput.text.toString().trim()
        val targetCamera =
            if (cameraSelection.checkedRadioButtonId == frontCamera.id) {
                InjectionConfig.FRONT_CAMERA
            } else {
                InjectionConfig.BACK_CAMERA
            }
        val enabled = injectionToggle.isChecked

        injectionConfig.saveConfig(rtmpUrl, targetCamera, enabled)

        if (rtmpUrl.isBlank()) {
            updateStatus("RTMP URL is required")
            stopPlayback()
            return
        }

        startPlayback(rtmpUrl)
    }

    private fun startPlayback(rtmpUrl: String) {
        val playerInstance = player ?: ExoPlayer.Builder(this).build().also {
            player = it
            it.addListener(playerListener)
        }

        playerInstance.setVideoSurfaceView(previewSurface)
        playerInstance.setMediaItem(MediaItem.fromUri(rtmpUrl))
        playerInstance.prepare()
        playerInstance.playWhenReady = true
        updateStatus("Connecting to RTMP stream...")
    }

    private fun stopPlayback() {
        player?.release()
        player = null
    }

    private fun updateStatus(message: String) {
        statusText.text = "Status: $message"
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val detail = error.message ?: error.cause?.message
            val suffix = if (detail.isNullOrBlank()) "" else " ($detail)"
            updateStatus("Playback error: ${error.errorCodeName}$suffix")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> updateStatus("Buffering RTMP stream...")
                Player.STATE_READY -> updateStatus("Stream connected")
                Player.STATE_ENDED -> updateStatus("Stream ended")
                Player.STATE_IDLE -> updateStatus("Stream idle")
            }
        }
    }
}

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
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.rtmp.RtmpDataSource

class MainActivity : AppCompatActivity() {
    private lateinit var serverInput: EditText
    private lateinit var streamKeyInput: EditText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverInput = findViewById(R.id.serverInput)
        streamKeyInput = findViewById(R.id.streamKeyInput)
        audioSwitch = findViewById(R.id.audioSwitch)
        videoSwitch = findViewById(R.id.videoSwitch)
        liveSwitch = findViewById(R.id.liveSwitch)
        softDecode = findViewById(R.id.softDecode)
        hardDecode = findViewById(R.id.hardDecode)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)
        videoPreview = findViewById(R.id.videoPreview)

        audioSwitch.isChecked = true
        videoSwitch.isChecked = true

        connectButton.setOnClickListener { applySettings() }
        audioSwitch.setOnCheckedChangeListener { _, _ -> updateTrackSelection() }
        videoSwitch.setOnCheckedChangeListener { _, _ -> updateTrackSelection() }
        liveSwitch.setOnCheckedChangeListener { _, isChecked -> togglePlayback(isChecked) }

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

    private fun applySettings() {
        val rtmpUrl = buildStreamUrl()
        if (rtmpUrl.isBlank()) {
            statusText.text = getString(R.string.status_missing_url)
            return
        }

        val useHardwareDecoder = hardDecode.isChecked
        buildPlayer(useHardwareDecoder)
        prepareMedia(rtmpUrl)
        updateTrackSelection()
        togglePlayback(liveSwitch.isChecked)
    }

    private fun buildPlayer(useHardwareDecoder: Boolean) {
        releasePlayer()

        val rendererFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(!useHardwareDecoder)

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
    }

    private fun prepareMedia(rtmpUrl: String) {
        val uri = Uri.parse(rtmpUrl)
        val mediaItem = MediaItem.fromUri(uri)

        val dataSourceFactory = RtmpDataSource.Factory()
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        player?.setMediaSource(mediaSource)
        player?.prepare()
        statusText.text = getString(R.string.status_connecting)
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
    }

    private class ExoFrameForwarder(
        private val virtualCameraBridge: VirtualCameraBridge
    ) : SurfaceTexture.OnFrameAvailableListener {
        override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
            val timestampNs = surfaceTexture.timestamp
            virtualCameraBridge.sendFrame(surfaceTexture, timestampNs)
        }
    }

    private class VirtualCameraBridge {
        fun sendFrame(surfaceTexture: SurfaceTexture, timestampNs: Long) {
            Log.d(TAG, "Forwarding frame to virtual camera at $timestampNs")
            // TODO: Use OpenGL or native APIs to read the SurfaceTexture and push the frame
            // into the virtual camera pipeline for external apps (Zoom, Meet, etc.).
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

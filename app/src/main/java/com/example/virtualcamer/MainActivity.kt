package com.example.virtualcamer

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceView
import android.widget.*
import androidx.appcompat.app.AlertDialog
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
    private lateinit var selectAppsButton: Button
    private lateinit var selectedAppsText: TextView
    private lateinit var injectionConfig: InjectionConfig

    private var player: ExoPlayer? = null
    private var selectedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        injectionConfig = InjectionConfig(this)
        hydrateConfig()

        connectButton.setOnClickListener { applySettings() }
        selectAppsButton.setOnClickListener { showAppSelector() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    private fun initializeViews() {
        rtmpUrlInput = findViewById(R.id.rtmpUrlInput)
        cameraSelection = findViewById(R.id.cameraSelection)
        frontCamera = findViewById(R.id.frontCamera)
        backCamera = findViewById(R.id.backCamera)
        injectionToggle = findViewById(R.id.injectionToggle)
        statusText = findViewById(R.id.statusText)
        previewSurface = findViewById(R.id.previewSurface)
        connectButton = findViewById(R.id.connectButton)
        selectAppsButton = findViewById(R.id.selectAppsButton)
        selectedAppsText = findViewById(R.id.selectedAppsText)
    }

    private fun hydrateConfig() {
        rtmpUrlInput.setText(injectionConfig.getRtmpUrl())
        when (injectionConfig.getTargetCamera()) {
            InjectionConfig.FRONT_CAMERA -> frontCamera.isChecked = true
            InjectionConfig.BACK_CAMERA -> backCamera.isChecked = true
        }
        injectionToggle.isChecked = injectionConfig.isEnabled()
        selectedPackages = injectionConfig.getAllowedPackages().toMutableSet()
        updateSelectedAppsText()
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

        injectionConfig.saveConfig(rtmpUrl, targetCamera, enabled, selectedPackages)

        if (rtmpUrl.isBlank()) {
            updateStatus("RTMP URL is required")
            stopPlayback()
            return
        }

        if (enabled) {
            updateStatus("Injection enabled. Restart target apps to apply.")
        } else {
            updateStatus("Injection disabled")
        }

        startPlayback(rtmpUrl)
    }

    private fun showAppSelector() {
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 } // User apps only
            .sortedBy { packageManager.getApplicationLabel(it).toString() }

        val appNames = installedApps.map { 
            packageManager.getApplicationLabel(it).toString() 
        }.toTypedArray()
        
        val appPackages = installedApps.map { it.packageName }
        val checkedItems = BooleanArray(appNames.size) { index ->
            selectedPackages.contains(appPackages[index])
        }

        AlertDialog.Builder(this)
            .setTitle("Select Apps to Inject")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedPackages.add(appPackages[which])
                } else {
                    selectedPackages.remove(appPackages[which])
                }
            }
            .setPositiveButton("OK") { dialog, _ ->
                updateSelectedAppsText()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Clear All") { _, _ ->
                selectedPackages.clear()
                updateSelectedAppsText()
            }
            .create()
            .show()
    }

    private fun updateSelectedAppsText() {
        if (selectedPackages.isEmpty()) {
            selectedAppsText.text = "All apps (default)"
        } else {
            val appNames = selectedPackages.mapNotNull { packageName ->
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
            selectedAppsText.text = "Selected: ${appNames.joinToString(", ")}"
        }
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
                Player.STATE_READY -> updateStatus("Stream connected ✓")
                Player.STATE_ENDED -> updateStatus("Stream ended")
                Player.STATE_IDLE -> updateStatus("Stream idle")
            }
        }
    }
}

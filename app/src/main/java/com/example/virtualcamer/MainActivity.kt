package com.example.virtualcamer

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val serverInput = findViewById<EditText>(R.id.serverInput)
        val streamKeyInput = findViewById<EditText>(R.id.streamKeyInput)
        val audioSwitch = findViewById<SwitchMaterial>(R.id.audioSwitch)
        val videoSwitch = findViewById<SwitchMaterial>(R.id.videoSwitch)
        val liveSwitch = findViewById<SwitchMaterial>(R.id.liveSwitch)
        val decoderGroup = findViewById<RadioGroup>(R.id.decoderGroup)
        val softDecode = findViewById<RadioButton>(R.id.softDecode)
        val hardDecode = findViewById<RadioButton>(R.id.hardDecode)
        val connectButton = findViewById<Button>(R.id.connectButton)
        val statusText = findViewById<TextView>(R.id.statusText)

        connectButton.setOnClickListener {
            val server = serverInput.text.toString().trim()
            val streamKey = streamKeyInput.text.toString().trim()
            val audioEnabled = audioSwitch.isChecked
            val videoEnabled = videoSwitch.isChecked
            val liveEnabled = liveSwitch.isChecked
            val decoderMode = if (decoderGroup.checkedRadioButtonId == softDecode.id) {
                "Soft decoding"
            } else {
                "Hard decoding"
            }

            statusText.text = buildString {
                append("Status: configured\n")
                append("Server: ").append(if (server.isBlank()) "(not set)" else server).append("\n")
                append("Stream key: ").append(if (streamKey.isBlank()) "(not set)" else "••••").append("\n")
                append("Audio: ").append(if (audioEnabled) "on" else "off").append("\n")
                append("Video: ").append(if (videoEnabled) "on" else "off").append("\n")
                append("Live: ").append(if (liveEnabled) "on" else "off").append("\n")
                append("Decoder: ").append(decoderMode)
            }
        }
    }
}

package com.puja.ai.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.puja.ai.R
import com.puja.ai.data.SettingsRepository
import com.puja.ai.voice.VoiceManager
import com.puja.ai.voice.VoiceState
import kotlinx.coroutines.launch

class VoiceActivity : AppCompatActivity() {
    private val vm: ChatViewModel by viewModels()
    private lateinit var voice: VoiceManager
    private lateinit var settings: SettingsRepository
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var reply: TextView
    private lateinit var ring: View
    private lateinit var switchSpeak: MaterialSwitch
    private var pulse: ObjectAnimator? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            voice.startListening()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("Mic permission chahiye")
                .setMessage("Voice assistant ke liye mic permission zaroori hai. App settings me jaake Microphone allow karo.")
                .setPositiveButton("App settings") { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    )
                }
                .setNegativeButton("Baad me", null)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice)
        setupTopBar("Voice assistant")

        settings = SettingsRepository(this)
        voice = VoiceManager(this, settings)
        status = findViewById(R.id.voiceStatus)
        transcript = findViewById(R.id.voiceTranscript)
        reply = findViewById(R.id.voiceReply)
        ring = findViewById(R.id.pulseRing)
        switchSpeak = findViewById(R.id.switchSpeak)
        switchSpeak.isChecked = settings.voiceReply
        switchSpeak.setOnCheckedChangeListener { _, checked ->
            settings.voiceReply = checked
            if (!checked) voice.stopSpeaking()
        }

        voice.onFinalResult = { text ->
            transcript.text = "“$text”"
            reply.text = ""
            vm.send(text)
        }

        findViewById<View>(R.id.btnMic).setOnClickListener { onMicTap() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { voice.state.collect { renderState(it) } }
                launch { voice.partial.collect { if (voice.state.value == VoiceState.LISTENING) transcript.text = it } }
                launch { voice.message.collect { renderState(voice.state.value) } }
                launch { vm.events.collect { handleEvent(it) } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.reload()
    }

    private fun onMicTap() {
        when (voice.state.value) {
            VoiceState.LISTENING -> voice.stopListening()
            VoiceState.SPEAKING -> voice.stopSpeaking()
            VoiceState.PROCESSING -> {
                vm.cancel()
                voice.reset()
            }
            else -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) voice.startListening()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun handleEvent(e: UiEvent) {
        when (e) {
            is UiEvent.Reply -> {
                reply.text = e.text
                if (switchSpeak.isChecked) voice.speak(e.text) else voice.reset()
            }
            is UiEvent.Failure -> {
                reply.text = "⚠️ ${e.message}"
                voice.reset()
            }
            is UiEvent.Confirm -> showActionConfirm(e.action,
                onYes = { vm.confirmAction(e.action) },
                onNo = { vm.cancelAction(); voice.reset() })
            is UiEvent.Ui -> {
                Toast.makeText(this, "File wale kaam chat screen se karo.", Toast.LENGTH_LONG).show()
                voice.reset()
            }
        }
    }

    private fun renderState(s: VoiceState) {
        status.text = when (s) {
            VoiceState.IDLE -> "Mic dabao aur bolo"
            VoiceState.LISTENING -> "Sun rahi hoon…"
            VoiceState.PROCESSING -> "Soch rahi hoon…"
            VoiceState.SPEAKING -> "Bol rahi hoon… (rokne ke liye mic dabao)"
            VoiceState.ERROR -> voice.message.value ?: "Kuch gadbad ho gayi"
        }
        if (s == VoiceState.IDLE && voice.message.value != null && reply.text.isEmpty()) {
            reply.text = voice.message.value ?: ""
        }
        if (s == VoiceState.LISTENING || s == VoiceState.SPEAKING) startPulse() else stopPulse()
    }

    private fun startPulse() {
        if (pulse != null) return
        ring.visibility = View.VISIBLE
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            ring,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1.5f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1.5f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0.1f)
        ).apply {
            duration = 1100
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        ring.visibility = View.INVISIBLE
    }

    override fun onStop() {
        if (voice.state.value == VoiceState.LISTENING) voice.stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        stopPulse()
        voice.shutdown()
        super.onDestroy()
    }
}

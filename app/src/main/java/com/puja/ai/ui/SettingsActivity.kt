package com.puja.ai.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.puja.ai.R
import com.puja.ai.ai.AiRepository
import com.puja.ai.data.SettingsRepository
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var s: SettingsRepository
    private lateinit var etKey: TextInputEditText
    private lateinit var etModel: TextInputEditText
    private lateinit var etPrompt: TextInputEditText
    private lateinit var swVoice: MaterialSwitch
    private lateinit var swMemory: MaterialSwitch
    private lateinit var rgLang: RadioGroup
    private lateinit var rgTheme: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupTopBar("Settings")
        s = SettingsRepository(this)

        etKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        etPrompt = findViewById(R.id.etPrompt)
        swVoice = findViewById(R.id.switchVoice)
        swMemory = findViewById(R.id.switchMemory)
        rgLang = findViewById(R.id.rgLang)
        rgTheme = findViewById(R.id.rgTheme)

        etKey.setText(s.apiKey)
        etModel.setText(s.model)
        etPrompt.setText(s.systemPrompt)
        swVoice.isChecked = s.voiceReply
        swMemory.isChecked = s.useMemory
        rgLang.check(
            when (s.language) {
                "en-IN" -> R.id.rbEnIn
                "en-US" -> R.id.rbEnUs
                else -> R.id.rbHi
            }
        )
        rgTheme.check(
            when (s.theme) {
                "light" -> R.id.rbLight
                "system" -> R.id.rbSystem
                else -> R.id.rbDark
            }
        )

        findViewById<MaterialButton>(R.id.btnGetKey).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
            } catch (e: Exception) {
                Toast.makeText(this, "Browser nahi mila. aistudio.google.com/apikey kholo.", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnResetPrompt).setOnClickListener {
            etPrompt.setText(SettingsRepository.DEFAULT_PROMPT)
        }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            save()
            Toast.makeText(this, "Save ho gaya ✅", Toast.LENGTH_SHORT).show()
        }
        val btnTest = findViewById<MaterialButton>(R.id.btnTest)
        btnTest.setOnClickListener {
            save()
            btnTest.isEnabled = false
            btnTest.text = "Check ho raha hai…"
            lifecycleScope.launch {
                val err = AiRepository(this@SettingsActivity).testConnection()
                btnTest.isEnabled = true
                btnTest.text = "✔ Test karo"
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(if (err == null) "Connection theek hai ✅" else "Problem ⚠️")
                    .setMessage(err ?: "Gemini se baat ho gayi. Ab chat kar sakte ho.")
                    .setPositiveButton("Theek hai", null)
                    .show()
            }
        }
    }

    private fun save() {
        s.apiKey = etKey.text?.toString().orEmpty()
        s.model = etModel.text?.toString().orEmpty()
        s.systemPrompt = etPrompt.text?.toString().orEmpty()
        s.voiceReply = swVoice.isChecked
        s.useMemory = swMemory.isChecked
        s.language = when (rgLang.checkedRadioButtonId) {
            R.id.rbEnIn -> "en-IN"
            R.id.rbEnUs -> "en-US"
            else -> "hi-IN"
        }
        s.theme = when (rgTheme.checkedRadioButtonId) {
            R.id.rbLight -> "light"
            R.id.rbSystem -> "system"
            else -> "dark"
        }
        s.applyTheme()
    }
}

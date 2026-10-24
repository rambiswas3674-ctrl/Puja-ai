package com.puja.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.puja.ai.R
import com.puja.ai.data.ChatRepository
import com.puja.ai.data.LocalMemoryRepository
import com.puja.ai.data.SettingsRepository

class AutomationActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var privacy: TextView

    private val micPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_automation)
        setupTopBar("Automation & privacy")
        list = findViewById(R.id.featureList)
        privacy = findViewById(R.id.privacyText)

        findViewById<android.view.View>(R.id.btnMicPerm).setOnClickListener {
            if (micGranted()) openAppInfo() else micPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
        findViewById<android.view.View>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<android.view.View>(R.id.btnAppInfo).setOnClickListener { openAppInfo() }
        findViewById<android.view.View>(R.id.btnWipe).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Saara data delete?")
                .setMessage("Chat history, memory, tasks aur saari settings (API key samet) hat jayengi.")
                .setPositiveButton("Delete") { _, _ ->
                    ChatRepository(this).clear()
                    LocalMemoryRepository(this).clearAll()
                    SettingsRepository(this).clearAll()
                    refresh()
                }
                .setNegativeButton("Nahi", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun micGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun a11yEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.contains("com.puja.ai")
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun refresh() {
        list.removeAllViews()
        row("🎤 Microphone", micGranted(), "Voice ke liye zaroori. Sirf aap mic dabate ho tabhi sunta hai.")
        row("🗣️ Speech recognition engine", SpeechRecognizer.isRecognitionAvailable(this),
            "Google app / Speech Services chahiye.")
        row("📱 App kholna", true, "Koi special permission nahi. Installed apps ke naam se.")
        row("🌐 Website / YouTube", true, "Browser ya YouTube app intent se.")
        row("⏰ Alarm / 📅 Calendar", true, "Clock/Calendar app khulti hai; final save aap karte ho.")
        row("📞 Call / ✉️ SMS", true, "Sirf dialer ya SMS compose khulta hai; bhejna/call aap dabate ho.")
        row("📁 File padhna/save karna", true, "Har baar aap khud file chunte ho (Storage Access Framework).")
        row("⚙️ Settings pages", true, "Allowlisted Android settings pages.")
        row("📝 Tasks & 🧠 Memory", true, "Sirf local phone me.")
        row("♿ Screen control (Accessibility)", a11yEnabled(),
            "Home/Back/click/scroll. Aapko Android Settings me khud ON karna padta hai.")
        row("🔔 Notification access", null, "Implement nahi kiya (future).")
        row("👂 Wake word / background mic", null, "Reliable aur transparent nahi ban sakta, isliye nahi banaya.")
        updatePrivacy()
    }

    private fun updatePrivacy() {
        val chats = ChatRepository(this).load().size
        val mem = LocalMemoryRepository(this)
        val st = SettingsRepository(this)
        privacy.text = buildString {
            append("• Chat messages: $chats\n")
            append("• Yaad rakhi baatein: ${mem.facts().size}\n")
            append("• Tasks: ${mem.tasks().size}\n")
            append("• Gemini API key: ${if (st.apiKey.isNotBlank()) "saved (sirf is phone me)" else "nahi hai"}\n")
            append("• Mic permission: ${if (micGranted()) "mili hui" else "nahi"}\n")
            append("• Accessibility: ${if (a11yEnabled()) "ON" else "OFF"}\n\n")
            append("Data sirf app-private storage me hai (backup band). Gemini ko sirf aapka sawal, recent chat context aur approved memory bheji jaati hai. Passwords/private data collect nahi hota.")
        }
    }

    private fun row(title: String, ok: Boolean?, note: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.puja_text))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = when (ok) {
                true -> "✅ Chalu / available"
                false -> "❌ Band — upar ke button se enable karo"
                null -> "🚧 Implement nahi hua"
            }
            textSize = 13f
            setTextColor(
                ContextCompat.getColor(
                    context,
                    when (ok) {
                        true -> R.color.puja_success
                        false -> R.color.puja_error
                        null -> R.color.puja_text_dim
                    }
                )
            )
        })
        box.addView(TextView(this).apply {
            text = note
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.puja_text_dim))
        })
        list.addView(box)
    }
}

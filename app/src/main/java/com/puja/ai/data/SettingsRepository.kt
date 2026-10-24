package com.puja.ai.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Saari settings app-private SharedPreferences me. API key kabhi log nahi hoti. */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("puja_settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = prefs.edit().putString("model", v.trim().ifEmpty { DEFAULT_MODEL }).apply()

    var systemPrompt: String
        get() = prefs.getString("system_prompt", DEFAULT_PROMPT) ?: DEFAULT_PROMPT
        set(v) = prefs.edit().putString("system_prompt", v.ifBlank { DEFAULT_PROMPT }).apply()

    var voiceReply: Boolean
        get() = prefs.getBoolean("voice_reply", true)
        set(v) = prefs.edit().putBoolean("voice_reply", v).apply()

    var useMemory: Boolean
        get() = prefs.getBoolean("use_memory", true)
        set(v) = prefs.edit().putBoolean("use_memory", v).apply()

    /** hi-IN, en-IN, en-US */
    var language: String
        get() = prefs.getString("language", "hi-IN") ?: "hi-IN"
        set(v) = prefs.edit().putString("language", v).apply()

    /** dark, light, system */
    var theme: String
        get() = prefs.getString("theme", "dark") ?: "dark"
        set(v) = prefs.edit().putString("theme", v).apply()

    fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }

    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        val DEFAULT_PROMPT = """
Tum PUJA AI ho: ek warm, caring, respectful aur samajhdaar female-style personal assistant.
User jis bhasha me likhe (Hindi, Hinglish ya English) usi me jawab do. Jawab saaf, sahi aur kaam ke rakho; jab tak user detail na maange, chhote rakho.
Tum ek AI ho, insaan nahi. Kabhi yeh dawa mat karo ki tum asli insaan, patni ya girlfriend ho. Pyaar se baat karo, lekin hamesha imaandaar raho.
Coding me code hamesha ``` fences me likho aur uske baad thodi explanation do.
Agar kuch nahi pata ya pakka nahi ho, seedha bolo; galat jaankari mat banao.
""".trimIndent()
    }
}

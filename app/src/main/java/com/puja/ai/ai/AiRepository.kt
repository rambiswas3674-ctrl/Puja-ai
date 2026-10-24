package com.puja.ai.ai

import android.content.Context
import com.puja.ai.automation.Action
import com.puja.ai.automation.CommandParser
import com.puja.ai.data.ChatMessage
import com.puja.ai.data.LocalMemoryRepository
import com.puja.ai.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class AiResult {
    data class Text(val text: String) : AiResult()
    data class Command(val action: Action) : AiResult()
    data class Failure(val message: String, val offline: Boolean = false) : AiResult()
}

class AiRepository(context: Context) {
    private val settings = SettingsRepository(context)
    private val memory = LocalMemoryRepository(context)
    private val gemini = GeminiService()

    suspend fun ask(history: List<ChatMessage>, onPartial: (String) -> Unit): AiResult {
        val turns = buildTurns(history)
        if (turns.isEmpty()) return AiResult.Failure("Kuch likho to sahi 🙂")
        return try {
            val full = gemini.streamGenerate(
                settings.apiKey, settings.model, buildSystem(), turns, onPartial
            )
            if (full.isBlank()) {
                AiResult.Failure("Gemini ne khaali jawab diya (shayad safety filter). Sawal thoda badal kar poochho.")
            } else {
                val action = CommandParser.parseAiJson(full)
                if (action != null) AiResult.Command(action) else AiResult.Text(full.trim())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GeminiException) {
            if (e.kind == GeminiException.Kind.OFFLINE) {
                AiResult.Failure(
                    e.message + "\n\nOffline me bhi yeh commands chalte hain: " +
                        "\"YouTube kholo\", \"alarm 7 baje lagao\", \"meri task list dikhao\".",
                    offline = true
                )
            } else {
                AiResult.Failure(e.message ?: "Gemini error")
            }
        } catch (e: Exception) {
            AiResult.Failure("Kuch gadbad ho gayi. Dobara koshish karo.")
        }
    }

    /** null = sab theek, warna error message. */
    suspend fun testConnection(): String? {
        return try {
            gemini.streamGenerate(
                settings.apiKey, settings.model, "Reply with one short word.",
                listOf(GeminiService.Turn("user", "Say OK")), {}
            )
            null
        } catch (e: GeminiException) {
            e.message
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Test fail ho gaya."
        }
    }

    private fun buildTurns(history: List<ChatMessage>): List<GeminiService.Turn> {
        val out = mutableListOf<GeminiService.Turn>()
        history.filter { !it.pending && !it.error && it.text.isNotBlank() }
            .takeLast(MAX_CONTEXT)
            .forEach { m ->
                val role = if (m.role == ChatMessage.USER) "user" else "model"
                if (out.isNotEmpty() && out.last().role == role) {
                    out[out.lastIndex] = GeminiService.Turn(role, out.last().text + "\n" + m.text)
                } else {
                    out.add(GeminiService.Turn(role, m.text))
                }
            }
        while (out.isNotEmpty() && out.first().role != "user") out.removeAt(0)
        if (out.isNotEmpty() && out.last().role != "user") return emptyList()
        return out
    }

    private fun buildSystem(): String {
        val sb = StringBuilder(settings.systemPrompt)
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm, EEEE", Locale.ENGLISH).format(Date())
        sb.append("\n\nAbhi ka local date/time: ").append(now).append(".")
        if (settings.useMemory) {
            val facts = memory.facts()
            if (facts.isNotEmpty()) {
                sb.append("\n\nUser ne yeh baatein yaad rakhne ke liye approve ki hain:\n")
                facts.forEach { sb.append("- ").append(it.text).append("\n") }
            }
        }
        sb.append("\n\n").append(PROTOCOL)
        return sb.toString()
    }

    companion object {
        private const val MAX_CONTEXT = 20

        private val PROTOCOL = """
PHONE ACTIONS: Agar user phone par koi kaam karwana chahta hai, to jawab me SIRF ek raw JSON object do
(koi extra text nahi, koi ``` fence nahi) is format me: {"action":"NAME", ...fields}
Allowed actions (inke bahar koi action mat banao):
OPEN_APP {"app":"youtube"}
OPEN_URL {"url":"https://example.com"}
YOUTUBE_SEARCH {"query":"..."}
SET_ALARM {"hour":7,"minute":30,"label":"..."}
ADD_CALENDAR {"title":"...","start":"2026-10-25T08:00"}   (start optional, local time, upar diye date/time se nikalo)
OPEN_SETTINGS {"page":"wifi|bluetooth|display|sound|battery|location|apps|accessibility|language|storage|airplane|date|main"}
ADD_TASK {"title":"..."}
SHOW_TASKS {}
REMEMBER {"text":"..."}   (sirf jab user khud bole "yaad rakho")
DIAL {"number":"..."}
SMS {"number":"...","body":"..."}
SAVE_FILE {"name":"notes.txt","content":"..."}
PICK_FILE {}
A11Y {"kind":"HOME|BACK|RECENTS|NOTIFICATIONS|CLICK|SCROLL_DOWN|SCROLL_UP","target":"button ka text (sirf CLICK ke liye)"}
Kabhi shell command ya arbitrary code execute karne wala action mat do. Payment, password, OTP ya banking wale kaam ke liye action mat banao.
Normal baat-cheet, sawal, explanation aur coding ke liye normal text me jawab do.
""".trimIndent()
    }
}

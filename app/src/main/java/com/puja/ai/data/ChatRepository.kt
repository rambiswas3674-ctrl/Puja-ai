package com.puja.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Chat history local JSON file me (app-private). */
class ChatRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "chat_history.json")

    @Synchronized
    fun load(): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    id = o.optLong("id", i.toLong()),
                    role = o.optString("role", ChatMessage.USER),
                    text = o.optString("text"),
                    time = o.optLong("time")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(list: List<ChatMessage>) {
        val arr = JSONArray()
        list.takeLast(MAX_MESSAGES).forEach {
            arr.put(
                JSONObject().put("id", it.id).put("role", it.role)
                    .put("text", it.text).put("time", it.time)
            )
        }
        try {
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    fun exportText(list: List<ChatMessage>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder("PUJA AI chat export\n\n")
        list.forEach {
            val who = if (it.role == ChatMessage.USER) "Aap" else "PUJA"
            sb.append("[").append(fmt.format(Date(it.time))).append("] ")
                .append(who).append(":\n").append(it.text).append("\n\n")
        }
        return sb.toString()
    }

    companion object {
        const val MAX_MESSAGES = 500
    }
}

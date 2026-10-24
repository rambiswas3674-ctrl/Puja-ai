package com.puja.ai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class GeminiException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NO_KEY, INVALID_KEY, QUOTA, OFFLINE, SERVER, BAD_REQUEST, UNKNOWN }
}

/**
 * Gemini REST (v1beta) streaming client. Koi extra library nahi.
 * API key sirf HTTP header me jaati hai; kisi error message ya log me nahi.
 */
class GeminiService {

    data class Turn(val role: String, val text: String) // role: "user" ya "model"

    suspend fun streamGenerate(
        apiKey: String,
        model: String,
        system: String,
        turns: List<Turn>,
        onPartial: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GeminiException(
                GeminiException.Kind.NO_KEY,
                "Pehle Settings me Gemini API key daalo."
            )
        }
        val cleanModel = model.trim().removePrefix("models/")
        if (!Regex("[A-Za-z0-9._-]{2,80}").matches(cleanModel)) {
            throw GeminiException(GeminiException.Kind.BAD_REQUEST, "Model ka naam sahi nahi hai.")
        }

        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
            val contents = JSONArray()
            turns.forEach { t ->
                contents.put(
                    JSONObject().put("role", t.role)
                        .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
                )
            }
            put("contents", contents)
            put("generationConfig", JSONObject().put("temperature", 0.8))
        }.toString()

        val conn = URL("$BASE$cleanModel:streamGenerateContent?alt=sse")
            .openConnection() as HttpURLConnection
        val full = StringBuilder()
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) throw httpError(conn, code)

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    ensureActive()
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val piece = extractText(payload)
                    if (piece.isNotEmpty()) {
                        full.append(piece)
                        onPartial(full.toString())
                    }
                }
            }
        } catch (e: GeminiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "Internet nahi mil raha. Network check karo.")
        } catch (e: ConnectException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "Server se connect nahi ho paya. Network check karo.")
        } catch (e: SocketTimeoutException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "Server ne der se jawab diya (timeout). Dobara koshish karo.")
        } catch (e: IOException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "Network error aaya. Dobara koshish karo.")
        } finally {
            conn.disconnect()
        }
        full.toString()
    }

    private fun extractText(payload: String): String {
        return try {
            val parts = JSONObject(payload).optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text") ?: "")
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun httpError(conn: HttpURLConnection, code: Int): GeminiException {
        val body = try {
            conn.errorStream?.bufferedReader()?.use { it.readText().take(2000) } ?: ""
        } catch (e: Exception) {
            ""
        }
        return when {
            code == 401 || code == 403 || body.contains("API_KEY_INVALID") || body.contains("API key not valid") ->
                GeminiException(GeminiException.Kind.INVALID_KEY, "API key galat ya band hai. Settings me sahi key daalo.")
            code == 429 ->
                GeminiException(GeminiException.Kind.QUOTA, "Gemini ki limit/quota khatam ho gayi. Thodi der baad try karo ya plan check karo.")
            code == 404 ->
                GeminiException(GeminiException.Kind.BAD_REQUEST, "Yeh model nahi mila. Settings me model ka naam badlo.")
            code == 400 ->
                GeminiException(GeminiException.Kind.BAD_REQUEST, "Request galat gayi (400). Model ka naam ya sawal check karo.")
            code in 500..599 ->
                GeminiException(GeminiException.Kind.SERVER, "Gemini server abhi dikkat me hai ($code). Thodi der baad try karo.")
            else ->
                GeminiException(GeminiException.Kind.UNKNOWN, "Gemini se error aaya (HTTP $code).")
        }
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
    }
}

package com.puja.ai.automation

import android.net.Uri
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 1) parseLocal: simple Hindi/Hinglish commands bina AI ke (offline bhi chalte hain).
 * 2) parseAiJson: Gemini ka JSON jawab validate karke Action me badalta hai.
 */
object CommandParser {

    val SETTINGS_PAGES = setOf(
        "wifi", "bluetooth", "display", "sound", "battery", "location", "apps",
        "accessibility", "language", "storage", "airplane", "date", "main"
    )
    private val A11Y_KINDS = setOf("HOME", "BACK", "RECENTS", "NOTIFICATIONS", "CLICK", "SCROLL_DOWN", "SCROLL_UP")

    private val IC = RegexOption.IGNORE_CASE
    private const val OPEN_VERBS =
        "kholo|kholiye|khol do|khol de|khol dijiye|open karo|open kro|open|launch karo|start karo|chalu karo|chalao|खोलो|खोलिए|खोल दो|खोलें|चालू करो|चलाओ"
    private const val SEARCH_VERBS =
        "search karo|search kro|search|dhundo|dhoondo|chalao|play karo|play|sunao|dikhao|सर्च करो|खोजो|चलाओ|सुनाओ|दिखाओ"

    private val wake = Regex("""^(?:hey |hi |ok |okay )?(?:puja|pooja|पूजा)[,!.:\s]+""", IC)
    private val showTasks = Regex("""(task|tasks|टास्क)""", IC)
    private val showVerbs = Regex("""(dikhao|dikha do|dikhaiye|show|batao|दिखाओ|बताओ)""", IC)
    private val addTask1 = Regex("""(?:task|टास्क)\s*(?:add|jodo|joddo|banao|जोड़ो)\s*(?:karo|kro)?\s*[:\-]?\s*(.+)""", IC)
    private val addTask2 = Regex("""(.+?)\s*(?:ko\s*)?(?:task|टास्क)(?:\s*list)?\s*(?:me|mein|में)\s*(?:add karo|jodo|daalo|dalo|जोड़ो)""", IC)
    private val remember = Regex("""(?:yaad rakho|yaad rakhna|याद रखो|remember that|remember)\s*(?:ki|that|कि)?\s*(.+)""", IC)
    private val numberRe = Regex("""(\d{1,2})(?:[:.](\d{2}))?""")
    private val pmRe = Regex("""(shaam|sham|raat|dopahar|evening|night|\bpm\b|शाम|रात|दोपहर)""", IC)
    private val amRe = Regex("""(subah|savere|morning|\bam\b|सुबह)""", IC)
    private val ytSearch1 = Regex("""^(?:youtube|yt|यूट्यूब)\s*(?:par|pe|पर|me|mein|में)\s+(.+?)\s*(?:$SEARCH_VERBS)$""", IC)
    private val ytSearch2 = Regex("""^(.+?)\s*(?:youtube|yt|यूट्यूब)\s*(?:par|pe|पर|me|mein|में)\s*(?:$SEARCH_VERBS)$""", IC)
    private val settingsWord = Regex("""(settings|setting|सेटिंग)""", IC)
    private val openVerbRe = Regex("""(?:$OPEN_VERBS)""", IC)
    private val dial1 = Regex("""(?:call|phone|कॉल)\s*(?:karo|kro|lagao)?\s*(\+?\d[\d ]{5,14}\d)""", IC)
    private val dial2 = Regex("""(\+?\d[\d ]{5,14}\d)\s*(?:ko\s*)?(?:call|phone|कॉल)""", IC)
    private val openApp1 = Regex("""^(?:mujhe |mere liye |please )?(.+?)\s*(?:app |ko |ka )?\s*(?:$OPEN_VERBS)$""", IC)
    private val openApp2 = Regex("""^(?:open|launch|start|खोलो|खोलिए)\s+(.+)$""", IC)
    private val domain = Regex("""^[\w-]+(\.[\w-]+)+$""")

    private val pageKeywords = listOf(
        "wifi" to "wifi", "wi-fi" to "wifi", "वाईफाई" to "wifi",
        "bluetooth" to "bluetooth", "ब्लूटूथ" to "bluetooth",
        "display" to "display", "brightness" to "display",
        "sound" to "sound", "volume" to "sound", "awaaz" to "sound",
        "battery" to "battery", "location" to "location", "gps" to "location",
        "accessibility" to "accessibility", "language" to "language", "bhasha" to "language",
        "storage" to "storage", "airplane" to "airplane", "flight mode" to "airplane",
        "date" to "date", "time" to "date"
    )

    fun parseLocal(raw: String): Action? {
        var t = raw.trim().replace(Regex("\\s+"), " ")
        t = wake.replace(t, "").trim().trimEnd('.', '!', '?', '।')
        if (t.isEmpty() || t.length > 120) return null
        val lower = t.lowercase()

        // Task list dikhao
        if (showTasks.containsMatchIn(lower) && showVerbs.containsMatchIn(lower) &&
            !addTask1.containsMatchIn(t)
        ) return Action.ShowTasks

        addTask1.find(t)?.let { return Action.AddTask(it.groupValues[1].trim()) }
        addTask2.matchEntire(t)?.let { return Action.AddTask(it.groupValues[1].trim()) }
        remember.find(t)?.let { return Action.Remember(it.groupValues[1].trim()) }

        // Alarm
        if (lower.contains("alarm") || t.contains("अलार्म")) {
            numberRe.find(lower)?.let { m ->
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                if (pmRe.containsMatchIn(lower) && h in 1..11) h += 12
                if (amRe.containsMatchIn(lower) && h == 12) h = 0
                if (h in 0..23 && min in 0..59) return Action.SetAlarm(h, min, "PUJA AI alarm")
            }
        }

        // YouTube search
        (ytSearch1.matchEntire(t) ?: ytSearch2.matchEntire(t))?.let {
            val q = it.groupValues[1].trim()
            if (q.isNotEmpty()) return Action.YoutubeSearch(q)
        }

        // Call
        (dial1.find(t) ?: dial2.find(t))?.let {
            return Action.Dial(it.groupValues[1].filter { c -> c.isDigit() || c == '+' })
        }

        // Settings pages
        if (settingsWord.containsMatchIn(lower) && openVerbRe.containsMatchIn(lower)) {
            val page = pageKeywords.firstOrNull { lower.contains(it.first) }?.second ?: "main"
            return Action.OpenSettings(page)
        }

        // App / website kholna
        val name = (openApp1.matchEntire(t) ?: openApp2.matchEntire(t))?.groupValues?.get(1)
            ?.replace(Regex("""\b(app|ko|ka)\b""", IC), "")?.trim()
        if (!name.isNullOrEmpty() && name.length <= 30 && name.split(" ").size <= 3) {
            return if (domain.matches(name)) Action.OpenUrl("https://$name") else Action.OpenApp(name)
        }
        return null
    }

    fun parseAiJson(raw: String): Action? {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        if (!t.startsWith("{") || !t.endsWith("}")) return null
        val o = try {
            JSONObject(t)
        } catch (e: JSONException) {
            return null
        }
        return fromJson(o)
    }

    private fun fromJson(o: JSONObject): Action? {
        fun s(k: String, max: Int = 300) = o.optString(k).trim().take(max)
        return when (o.optString("action").uppercase()) {
            "OPEN_APP" -> s("app", 60).takeIf { it.isNotEmpty() }?.let { Action.OpenApp(it) }
            "OPEN_URL" -> safeUrl(s("url", 500))?.let { Action.OpenUrl(it) }
            "YOUTUBE_SEARCH" -> s("query", 150).takeIf { it.isNotEmpty() }?.let { Action.YoutubeSearch(it) }
            "SET_ALARM" -> {
                val h = o.optInt("hour", -1)
                val m = o.optInt("minute", 0)
                if (h in 0..23 && m in 0..59) Action.SetAlarm(h, m, s("label", 80).ifEmpty { "PUJA AI alarm" }) else null
            }
            "ADD_CALENDAR" -> s("title", 150).takeIf { it.isNotEmpty() }
                ?.let { Action.AddCalendar(it, parseTime(s("start", 40))) }
            "OPEN_SETTINGS" -> Action.OpenSettings(s("page", 30).lowercase().takeIf { it in SETTINGS_PAGES } ?: "main")
            "ADD_TASK" -> s("title", 200).takeIf { it.isNotEmpty() }?.let { Action.AddTask(it) }
            "SHOW_TASKS" -> Action.ShowTasks
            "REMEMBER" -> s("text", 300).takeIf { it.isNotEmpty() }?.let { Action.Remember(it) }
            "DIAL" -> s("number", 25).filter { it.isDigit() || it == '+' }
                .takeIf { it.length >= 3 }?.let { Action.Dial(it) }
            "SMS" -> {
                val n = s("number", 25).filter { it.isDigit() || it == '+' }
                if (n.length >= 3) Action.ComposeSms(n, s("body", 500)) else null
            }
            "SAVE_FILE" -> {
                val name = s("name", 80).replace(Regex("""[\\/:*?"<>|]"""), "_").ifEmpty { "puja-note.txt" }
                Action.SaveFile(name, o.optString("content").take(50_000))
            }
            "PICK_FILE" -> Action.PickFile
            "A11Y" -> {
                val kind = s("kind", 20).uppercase()
                val target = s("target", 80).ifEmpty { null }
                if (kind !in A11Y_KINDS) null
                else if (kind == "CLICK" && target == null) null
                else Action.Accessibility(kind, target)
            }
            else -> null
        }
    }

    private fun safeUrl(u: String): String? {
        if (u.isEmpty()) return null
        val withScheme = if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
        val uri = Uri.parse(withScheme)
        return if (uri.host.isNullOrEmpty()) null else withScheme
    }

    private fun parseTime(s: String): Long? {
        if (s.isEmpty()) return null
        return try {
            LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}

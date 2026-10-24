package com.puja.ai.automation

/** Structured, allowlisted actions. AI/User text inme convert hota hai; shell kabhi nahi chalta. */
sealed class Action {
    data class OpenApp(val app: String) : Action()
    data class OpenUrl(val url: String) : Action()
    data class YoutubeSearch(val query: String) : Action()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : Action()
    data class AddCalendar(val title: String, val startMillis: Long?) : Action()
    data class OpenSettings(val page: String) : Action()
    data class AddTask(val title: String) : Action()
    object ShowTasks : Action()
    data class Remember(val text: String) : Action()
    data class Dial(val number: String) : Action()
    data class ComposeSms(val number: String, val body: String) : Action()
    data class SaveFile(val name: String, val content: String) : Action()
    object PickFile : Action()
    data class Accessibility(val kind: String, val target: String?) : Action()
}

/** Kin kaamon se pehle user ki confirmation chahiye. */
val Action.needsConfirm: Boolean
    get() = when (this) {
        is Action.ComposeSms, is Action.SaveFile -> true
        is Action.Accessibility -> kind == "CLICK"
        else -> false
    }

fun Action.describe(): String = when (this) {
    is Action.OpenApp -> "App kholna: $app"
    is Action.OpenUrl -> "Website kholna: $url"
    is Action.YoutubeSearch -> "YouTube par search: $query"
    is Action.SetAlarm -> "Alarm: %02d:%02d".format(hour, minute)
    is Action.AddCalendar -> "Calendar event: $title"
    is Action.OpenSettings -> "Settings page kholna: $page"
    is Action.AddTask -> "Task add karna: $title"
    is Action.ShowTasks -> "Task list dikhana"
    is Action.Remember -> "Yaad rakhna: $text"
    is Action.Dial -> "Dialer kholna: $number"
    is Action.ComposeSms -> "SMS likhna: $number ko \"$body\"\n(Sirf compose screen khulegi, bhejna aapko dabana hoga.)"
    is Action.SaveFile -> "File save karna: $name\n(Save ki jagah aap chunoge.)"
    is Action.PickFile -> "File chunna aur padhna"
    is Action.Accessibility -> if (kind == "CLICK") "Screen par \"$target\" par click karna" else "Screen action: $kind"
}

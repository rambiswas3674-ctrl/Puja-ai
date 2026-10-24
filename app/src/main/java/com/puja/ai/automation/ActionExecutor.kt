package com.puja.ai.automation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.puja.ai.data.LocalMemoryRepository

sealed class UiRequest {
    object PickFile : UiRequest()
    data class SaveFile(val name: String, val content: String) : UiRequest()
}

data class ExecResult(val ok: Boolean, val message: String, val ui: UiRequest? = null)

/** Allowlisted Action ko asli Android intent/kaam me badalta hai. Shell kabhi nahi. */
class ActionExecutor(private val ctx: Context, private val memory: LocalMemoryRepository) {
    private val launcher = AppLauncher(ctx)

    fun execute(a: Action): ExecResult = try {
        doRun(a)
    } catch (e: ActivityNotFoundException) {
        ExecResult(false, "Is kaam ke liye phone me koi app nahi mili.")
    } catch (e: SecurityException) {
        ExecResult(false, "Android ne is kaam ki permission nahi di.")
    } catch (e: Exception) {
        ExecResult(false, "Yeh kaam nahi ho paya.")
    }

    private fun doRun(a: Action): ExecResult = when (a) {
        is Action.OpenApp ->
            if (launcher.launch(a.app)) ExecResult(true, "✅ ${a.app} khol diya.")
            else ExecResult(false, "❌ \"${a.app}\" naam ka app nahi mila. English naam try karo (jaise WhatsApp).")

        is Action.OpenUrl -> {
            start(Intent(Intent.ACTION_VIEW, Uri.parse(a.url)))
            ExecResult(true, "✅ Website khol di: ${a.url}")
        }

        is Action.YoutubeSearch -> {
            try {
                start(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", a.query))
            } catch (e: ActivityNotFoundException) {
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(a.query))))
            }
            ExecResult(true, "✅ YouTube par \"${a.query}\" search kiya.")
        }

        is Action.SetAlarm -> {
            start(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, a.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, a.label)
            )
            ExecResult(true, "⏰ Alarm %02d:%02d ke liye Clock app khul gayi. Wahan confirm kar lo.".format(a.hour, a.minute))
        }

        is Action.AddCalendar -> {
            val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, a.title)
            a.startMillis?.let {
                i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it)
                i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it + 3_600_000L)
            }
            start(i)
            ExecResult(true, "📅 Calendar me \"${a.title}\" ka event khul gaya. Save dabao.")
        }

        is Action.OpenSettings -> {
            start(Intent(settingsAction(a.page)))
            ExecResult(true, "⚙️ Settings khol di (${a.page}).")
        }

        is Action.AddTask -> {
            memory.addTask(a.title)
            ExecResult(true, "✅ Task add ho gaya: ${a.title}")
        }

        is Action.ShowTasks -> {
            val tasks = memory.tasks()
            if (tasks.isEmpty()) ExecResult(true, "📝 Abhi koi task nahi hai. \"task add <kaam>\" bolo.")
            else ExecResult(
                true,
                "📝 Aapki tasks:\n" + tasks.mapIndexed { i, t -> "${i + 1}. ${if (t.done) "✅" else "⬜"} ${t.title}" }.joinToString("\n")
            )
        }

        is Action.Remember -> {
            memory.addFact(a.text)
            ExecResult(true, "🧠 Yaad rakh liya: ${a.text}\n(Memory screen se dekh/delete kar sakte ho.)")
        }

        is Action.Dial -> {
            start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(a.number))))
            ExecResult(true, "📞 Dialer khul gaya: ${a.number} (call aapko dabana hoga).")
        }

        is Action.ComposeSms -> {
            start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(a.number))).putExtra("sms_body", a.body))
            ExecResult(true, "✉️ SMS draft khul gaya. Bhejna aapko dabana hoga.")
        }

        is Action.SaveFile -> ExecResult(true, "", UiRequest.SaveFile(a.name, a.content))
        is Action.PickFile -> ExecResult(true, "", UiRequest.PickFile)

        is Action.Accessibility -> {
            val svc = AutomationAccessibilityService.instance
            if (svc == null) {
                ExecResult(false, "♿ Accessibility service chalu nahi hai. Menu > Automation se enable karo.")
            } else if (svc.perform(a.kind, a.target)) {
                ExecResult(true, "✅ Screen action ho gaya (${a.kind}).")
            } else {
                ExecResult(false, "❌ Screen action nahi ho paya. Button dikh raha hai? (Password fields skip hote hain.)")
            }
        }
    }

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun settingsAction(page: String): String = when (page) {
        "wifi" -> Settings.ACTION_WIFI_SETTINGS
        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
        "display" -> Settings.ACTION_DISPLAY_SETTINGS
        "sound" -> Settings.ACTION_SOUND_SETTINGS
        "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
        "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        "apps" -> Settings.ACTION_APPLICATION_SETTINGS
        "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
        "language" -> Settings.ACTION_LOCALE_SETTINGS
        "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        "date" -> Settings.ACTION_DATE_SETTINGS
        else -> Settings.ACTION_SETTINGS
    }
}

package com.puja.ai.automation

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings

/** Installed app kholta hai: pehle known naam, phir phone ke installed apps ke label se. */
class AppLauncher(private val ctx: Context) {

    private val known = mapOf(
        "youtube" to "com.google.android.youtube", "यूट्यूब" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp", "व्हाट्सएप" to "com.whatsapp", "वाट्सऐप" to "com.whatsapp",
        "chrome" to "com.android.chrome", "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps", "google maps" to "com.google.android.apps.maps",
        "instagram" to "com.instagram.android", "facebook" to "com.facebook.katana",
        "telegram" to "org.telegram.messenger", "spotify" to "com.spotify.music",
        "play store" to "com.android.vending", "playstore" to "com.android.vending",
        "photos" to "com.google.android.apps.photos", "drive" to "com.google.android.apps.docs",
        "snapchat" to "com.snapchat.android", "twitter" to "com.twitter.android", "x" to "com.twitter.android"
    )

    fun launch(name: String): Boolean {
        val key = name.trim().lowercase().removeSuffix(" app").trim()
        if (key.isEmpty()) return false

        when (key) {
            "settings", "setting", "सेटिंग", "सेटिंग्स" -> return start(Intent(Settings.ACTION_SETTINGS))
            "camera", "कैमरा" -> return start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            "dialer", "phone", "call" -> return start(Intent(Intent.ACTION_DIAL))
        }

        known[key]?.let { pkg ->
            ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { if (start(it)) return true }
        }
        return findByLabel(key)?.let { start(it) } ?: false
    }

    private fun findByLabel(q: String): Intent? {
        if (q.length < 2) return null
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(main, 0)
        val hit = list.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: list.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return null
        return Intent(main).setClassName(hit.activityInfo.packageName, hit.activityInfo.name)
    }

    private fun start(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }
}

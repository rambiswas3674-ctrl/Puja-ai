package com.puja.ai

import android.app.Application
import com.puja.ai.data.SettingsRepository

class PujaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRepository(this).applyTheme()
    }
}

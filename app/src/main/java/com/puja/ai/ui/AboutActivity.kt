package com.puja.ai.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.puja.ai.R

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        setupTopBar("About & troubleshooting")
        findViewById<TextView>(R.id.aboutText).text = getString(R.string.about_text)
    }
}

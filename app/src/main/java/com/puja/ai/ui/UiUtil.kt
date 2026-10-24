package com.puja.ai.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.puja.ai.R
import com.puja.ai.automation.Action
import com.puja.ai.automation.describe

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Activity.setupTopBar(title: String) {
    findViewById<TextView>(R.id.topTitle).text = title
    findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
}

fun Context.copyToClipboard(label: String, text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "Copy ho gaya ✅", Toast.LENGTH_SHORT).show()
}

fun Context.showActionConfirm(action: Action, onYes: () -> Unit, onNo: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setTitle("Confirm karo")
        .setMessage(action.describe())
        .setCancelable(false)
        .setPositiveButton("Haan, karo") { _, _ -> onYes() }
        .setNegativeButton("Nahi") { _, _ -> onNo() }
        .show()
}

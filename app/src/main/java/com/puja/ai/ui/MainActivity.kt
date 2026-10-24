package com.puja.ai.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.puja.ai.R
import com.puja.ai.automation.UiRequest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val vm: ChatViewModel by viewModels()
    private val adapter = ChatAdapter()
    private var pendingSave: UiRequest.SaveFile? = null
    private var busyNow = false

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onFilePicked(uri)
    }
    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val p = pendingSave
        if (uri != null && p != null) vm.saveFile(uri, p.content)
        pendingSave = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        val input = findViewById<EditText>(R.id.input)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)
        val progress = findViewById<LinearProgressIndicator>(R.id.progress)
        val empty = findViewById<View>(R.id.emptyState)
        val retry = findViewById<View>(R.id.btnRetry)

        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = false }
        recycler.adapter = adapter

        fun sendNow() {
            val t = input.text.toString()
            if (t.isNotBlank()) {
                vm.send(t)
                input.setText("")
            }
        }
        btnSend.setOnClickListener { if (busyNow) vm.cancel() else sendNow() }
        retry.setOnClickListener { vm.retry() }
        findViewById<View>(R.id.btnVoice).setOnClickListener {
            startActivity(Intent(this, VoiceActivity::class.java))
        }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMenu(it) }

        listOf(R.id.chip1, R.id.chip2, R.id.chip3, R.id.chip4).forEach { id ->
            findViewById<android.widget.TextView>(id).setOnClickListener {
                vm.send((it as android.widget.TextView).text.toString())
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.messages.collect { list ->
                        adapter.submit(list)
                        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        if (list.isNotEmpty()) recycler.scrollToPosition(list.size - 1)
                    }
                }
                launch {
                    vm.busy.collect { b ->
                        busyNow = b
                        progress.visibility = if (b) View.VISIBLE else View.GONE
                        btnSend.setImageResource(if (b) R.drawable.ic_stop else R.drawable.ic_send)
                        btnSend.contentDescription = if (b) "Rokho" else "Bhejo"
                    }
                }
                launch { vm.showRetry.collect { retry.visibility = if (it) View.VISIBLE else View.GONE } }
                launch { vm.events.collect { handleEvent(it) } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.reload()
    }

    private fun handleEvent(e: UiEvent) {
        when (e) {
            is UiEvent.Confirm -> showActionConfirm(e.action,
                onYes = { vm.confirmAction(e.action) }, onNo = { vm.cancelAction() })
            is UiEvent.Ui -> when (val r = e.request) {
                is UiRequest.PickFile -> pickFile.launch(arrayOf("text/*", "application/json", "application/xml"))
                is UiRequest.SaveFile -> {
                    pendingSave = r
                    saveFile.launch(r.name)
                }
            }
            is UiEvent.Reply, is UiEvent.Failure -> Unit
        }
    }

    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "🎙️ Voice assistant")
        pm.menu.add(0, 2, 1, "🕘 Conversation history")
        pm.menu.add(0, 3, 2, "🧠 Memory & tasks")
        pm.menu.add(0, 4, 3, "⚙️ Automation & privacy")
        pm.menu.add(0, 5, 4, "🔧 Settings / Gemini API")
        pm.menu.add(0, 6, 5, "ℹ️ About & troubleshooting")
        pm.menu.add(0, 7, 6, "🆕 Nayi chat (history clear)")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, VoiceActivity::class.java))
                2 -> startActivity(Intent(this, HistoryActivity::class.java))
                3 -> startActivity(Intent(this, MemoryActivity::class.java))
                4 -> startActivity(Intent(this, AutomationActivity::class.java))
                5 -> startActivity(Intent(this, SettingsActivity::class.java))
                6 -> startActivity(Intent(this, AboutActivity::class.java))
                7 -> MaterialAlertDialogBuilder(this)
                    .setTitle("Nayi chat?")
                    .setMessage("Saari chat history delete ho jayegi.")
                    .setPositiveButton("Haan") { _, _ ->
                        vm.clearHistory()
                        Toast.makeText(this, "History clear ho gayi", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Nahi", null)
                    .show()
            }
            true
        }
        pm.show()
    }
}

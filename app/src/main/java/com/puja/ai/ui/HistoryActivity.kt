package com.puja.ai.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.puja.ai.R
import com.puja.ai.data.ChatRepository

class HistoryActivity : AppCompatActivity() {
    private lateinit var repo: ChatRepository
    private val adapter = ChatAdapter()
    private lateinit var empty: View

    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(repo.exportText(repo.load()).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Export ho gaya ✅", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export nahi ho paya", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        setupTopBar("Conversation history")
        repo = ChatRepository(this)
        empty = findViewById(R.id.emptyText)
        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
        }
        findViewById<View>(R.id.btnExport).setOnClickListener {
            if (repo.load().isEmpty()) Toast.makeText(this, "Export karne ko kuch nahi hai", Toast.LENGTH_SHORT).show()
            else export.launch("puja-ai-chat.txt")
        }
        findViewById<View>(R.id.btnClear).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("History delete karein?")
                .setMessage("Yeh wapas nahi aayegi.")
                .setPositiveButton("Delete") { _, _ -> repo.clear(); refresh() }
                .setNegativeButton("Nahi", null)
                .show()
        }
        refresh()
    }

    private fun refresh() {
        val list = repo.load()
        adapter.submit(list)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}

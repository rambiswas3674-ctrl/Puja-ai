package com.puja.ai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.puja.ai.R
import com.puja.ai.data.LocalMemoryRepository

class MemoryActivity : AppCompatActivity() {
    private lateinit var repo: LocalMemoryRepository
    private lateinit var factList: LinearLayout
    private lateinit var taskList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory)
        setupTopBar("Memory & tasks")
        repo = LocalMemoryRepository(this)
        factList = findViewById(R.id.factList)
        taskList = findViewById(R.id.taskList)
        val etFact = findViewById<EditText>(R.id.etFact)
        val etTask = findViewById<EditText>(R.id.etTask)

        findViewById<View>(R.id.btnAddFact).setOnClickListener {
            val t = etFact.text.toString()
            if (t.isNotBlank()) {
                repo.addFact(t); etFact.setText(""); render()
            }
        }
        findViewById<View>(R.id.btnAddTask).setOnClickListener {
            val t = etTask.text.toString()
            if (t.isNotBlank()) {
                repo.addTask(t); etTask.setText(""); render()
            }
        }
        findViewById<View>(R.id.btnClearFacts).setOnClickListener {
            confirm("Saari memory delete?") { repo.clearFacts(); render() }
        }
        findViewById<View>(R.id.btnClearTasks).setOnClickListener {
            confirm("Saare tasks delete?") { repo.clearTasks(); render() }
        }
        render()
    }

    private fun confirm(title: String, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(title)
            .setPositiveButton("Haan") { _, _ -> onYes() }
            .setNegativeButton("Nahi", null).show()
    }

    private fun render() {
        factList.removeAllViews()
        val facts = repo.facts()
        if (facts.isEmpty()) factList.addView(emptyHint("Abhi kuch yaad nahi rakha. \"Yaad rakho ki ...\" bolo ya upar likho."))
        facts.forEach { f ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_row, factList, false)
            v.findViewById<TextView>(R.id.rowText).text = f.text
            v.findViewById<View>(R.id.rowDelete).setOnClickListener { repo.deleteFact(f.id); render() }
            factList.addView(v)
        }

        taskList.removeAllViews()
        val tasks = repo.tasks()
        if (tasks.isEmpty()) taskList.addView(emptyHint("Koi task nahi. Upar likho ya bolo: \"task add dudh lana\"."))
        tasks.forEach { t ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_row, taskList, false)
            val cb = v.findViewById<CheckBox>(R.id.rowCheck)
            cb.visibility = View.VISIBLE
            cb.isChecked = t.done
            cb.setOnCheckedChangeListener { _, checked -> repo.setDone(t.id, checked) }
            v.findViewById<TextView>(R.id.rowText).text = t.title
            v.findViewById<View>(R.id.rowDelete).setOnClickListener { repo.deleteTask(t.id); render() }
            taskList.addView(v)
        }
    }

    private fun emptyHint(msg: String) = TextView(this).apply {
        text = msg
        textSize = 13f
        setTextColor(getColor(R.color.puja_text_dim))
        setPadding(dp(4), dp(6), dp(4), dp(6))
    }
}

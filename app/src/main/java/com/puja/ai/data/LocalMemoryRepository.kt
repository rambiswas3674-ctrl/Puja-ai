package com.puja.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** User-approved memory (facts) aur task list. Sab kuch local file me. */
class LocalMemoryRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "memory.json")

    private class Store(val facts: MutableList<MemoryItem>, val tasks: MutableList<TaskItem>)

    @Synchronized
    private fun read(): Store {
        val facts = mutableListOf<MemoryItem>()
        val tasks = mutableListOf<TaskItem>()
        if (file.exists()) {
            try {
                val root = JSONObject(file.readText())
                root.optJSONArray("facts")?.let { a ->
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)
                        facts.add(MemoryItem(o.optLong("id"), o.optString("text")))
                    }
                }
                root.optJSONArray("tasks")?.let { a ->
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)
                        tasks.add(TaskItem(o.optLong("id"), o.optString("title"), o.optBoolean("done")))
                    }
                }
            } catch (_: Exception) {
            }
        }
        return Store(facts, tasks)
    }

    @Synchronized
    private fun write(s: Store) {
        val fa = JSONArray()
        s.facts.forEach { fa.put(JSONObject().put("id", it.id).put("text", it.text)) }
        val ta = JSONArray()
        s.tasks.forEach {
            ta.put(JSONObject().put("id", it.id).put("title", it.title).put("done", it.done))
        }
        try {
            file.writeText(JSONObject().put("facts", fa).put("tasks", ta).toString())
        } catch (_: Exception) {
        }
    }

    fun facts(): List<MemoryItem> = read().facts

    fun addFact(text: String) {
        val s = read()
        s.facts.add(MemoryItem(System.currentTimeMillis(), text.trim().take(300)))
        write(s)
    }

    fun deleteFact(id: Long) {
        val s = read()
        s.facts.removeAll { it.id == id }
        write(s)
    }

    fun clearFacts() {
        val s = read()
        s.facts.clear()
        write(s)
    }

    fun tasks(): List<TaskItem> = read().tasks

    fun addTask(title: String) {
        val s = read()
        s.tasks.add(TaskItem(System.currentTimeMillis(), title.trim().take(200)))
        write(s)
    }

    fun setDone(id: Long, done: Boolean) {
        val s = read()
        val i = s.tasks.indexOfFirst { it.id == id }
        if (i >= 0) s.tasks[i] = s.tasks[i].copy(done = done)
        write(s)
    }

    fun deleteTask(id: Long) {
        val s = read()
        s.tasks.removeAll { it.id == id }
        write(s)
    }

    fun clearTasks() {
        val s = read()
        s.tasks.clear()
        write(s)
    }

    fun clearAll() {
        file.delete()
    }
}

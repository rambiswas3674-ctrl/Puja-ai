package com.puja.ai.data

data class ChatMessage(
    val id: Long,
    val role: String,
    val text: String,
    val time: Long = System.currentTimeMillis(),
    val pending: Boolean = false,
    val error: Boolean = false
) {
    companion object {
        const val USER = "user"
        const val AI = "assistant"
    }
}

data class MemoryItem(val id: Long, val text: String)

data class TaskItem(val id: Long, val title: String, val done: Boolean = false)

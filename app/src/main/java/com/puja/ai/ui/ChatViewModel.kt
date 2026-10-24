package com.puja.ai.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puja.ai.ai.AiRepository
import com.puja.ai.ai.AiResult
import com.puja.ai.automation.Action
import com.puja.ai.automation.ActionExecutor
import com.puja.ai.automation.CommandParser
import com.puja.ai.automation.UiRequest
import com.puja.ai.automation.needsConfirm
import com.puja.ai.data.ChatMessage
import com.puja.ai.data.ChatRepository
import com.puja.ai.data.LocalMemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UiEvent {
    data class Confirm(val action: Action) : UiEvent()
    data class Ui(val request: UiRequest) : UiEvent()
    data class Reply(val text: String) : UiEvent()
    data class Failure(val message: String) : UiEvent()
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val chatRepo = ChatRepository(app)
    private val memory = LocalMemoryRepository(app)
    private val ai = AiRepository(app)
    private val executor = ActionExecutor(app, memory)

    private val _messages = MutableStateFlow(chatRepo.load())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _showRetry = MutableStateFlow(false)
    val showRetry: StateFlow<Boolean> = _showRetry.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var idCounter = maxOf(System.currentTimeMillis(), (_messages.value.maxOfOrNull { it.id } ?: 0L) + 1)
    private var lastUserText: String? = null
    private var job: Job? = null

    private fun nextId(): Long = idCounter++

    /** Doosri screen (History) ne data badla ho to disk se dobara padho. */
    fun reload() {
        if (!_busy.value) _messages.value = chatRepo.load()
    }

    fun send(text: String, retry: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty() || _busy.value) return
        lastUserText = t
        _showRetry.value = false
        _messages.update { l -> l.filterNot { it.error } }
        if (!retry) append(ChatMessage(nextId(), ChatMessage.USER, t))
        _busy.value = true
        job = viewModelScope.launch {
            try {
                process(t)
            } finally {
                _messages.update { l -> l.filterNot { it.pending } }
                _busy.value = false
                persist()
            }
        }
    }

    fun retry() {
        lastUserText?.let { send(it, retry = true) }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun process(t: String) {
        val local = CommandParser.parseLocal(t)
        if (local != null) {
            handleAction(local)
            return
        }
        val pid = nextId()
        append(ChatMessage(pid, ChatMessage.AI, "", pending = true))
        val history = _messages.value.filter { !it.pending && !it.error }
        when (val r = ai.ask(history) { partial ->
            val shown = if (partial.trimStart().startsWith("{")) "⏳ Kaam samajh rahi hoon…" else partial
            updateText(pid, shown)
        }) {
            is AiResult.Text -> {
                _messages.update { l -> l.map { if (it.id == pid) it.copy(text = r.text, pending = false) else it } }
                _events.tryEmit(UiEvent.Reply(r.text))
            }
            is AiResult.Command -> {
                removeMessage(pid)
                handleAction(r.action)
            }
            is AiResult.Failure -> {
                removeMessage(pid)
                append(ChatMessage(nextId(), ChatMessage.AI, "⚠️ ${r.message}", error = true))
                _showRetry.value = true
                _events.tryEmit(UiEvent.Failure(r.message))
            }
        }
    }

    private fun handleAction(a: Action) {
        if (a.needsConfirm) {
            _events.tryEmit(UiEvent.Confirm(a))
        } else {
            runAction(a)
        }
    }

    fun confirmAction(a: Action) = runAction(a)

    fun cancelAction() {
        append(ChatMessage(nextId(), ChatMessage.AI, "Theek hai, cancel kar diya 👍"))
        persist()
    }

    private fun runAction(a: Action) {
        val r = executor.execute(a)
        val ui = r.ui
        if (ui != null) {
            _events.tryEmit(UiEvent.Ui(ui))
            return
        }
        if (r.message.isNotBlank()) {
            append(ChatMessage(nextId(), ChatMessage.AI, r.message, error = !r.ok))
            _events.tryEmit(UiEvent.Reply(r.message))
        }
        persist()
    }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { r ->
                            val buf = CharArray(8000)
                            val n = r.read(buf)
                            if (n > 0) String(buf, 0, n) else ""
                        }
                } catch (e: Exception) {
                    null
                }
            }
            if (content == null) {
                append(ChatMessage(nextId(), ChatMessage.AI, "⚠️ File padh nahi payi.", error = true))
            } else {
                send("Is file ka content (pehle 8000 characters):\n```\n$content\n```\nIs file ko simple Hindi me samjhao.")
            }
        }
    }

    fun saveFile(uri: Uri, content: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            val msg = if (ok) "💾 File save ho gayi." else "⚠️ File save nahi ho payi."
            append(ChatMessage(nextId(), ChatMessage.AI, msg, error = !ok))
            _events.tryEmit(UiEvent.Reply(msg))
            persist()
        }
    }

    fun clearHistory() {
        chatRepo.clear()
        _messages.value = emptyList()
        _showRetry.value = false
    }

    private fun append(m: ChatMessage) = _messages.update { it + m }

    private fun removeMessage(id: Long) = _messages.update { l -> l.filterNot { it.id == id } }

    private fun updateText(id: Long, text: String) =
        _messages.update { l -> l.map { if (it.id == id) it.copy(text = text) else it } }

    private fun persist() {
        val snapshot = _messages.value.filter { !it.pending && !it.error }
        viewModelScope.launch(Dispatchers.IO) { chatRepo.save(snapshot) }
    }
}

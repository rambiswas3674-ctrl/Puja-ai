package com.puja.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.puja.ai.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceState { IDLE, LISTENING, PROCESSING, SPEAKING, ERROR }

/** SpeechRecognizer (STT) + TextToSpeech (TTS). Main thread par use karo. */
class VoiceManager(context: Context, private val settings: SettingsRepository) {
    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    var onFinalResult: ((String) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: Pair<String, (() -> Unit)?>? = null
    private var onSpeakDone: (() -> Unit)? = null

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                applyLanguage()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = finishSpeaking()
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finishSpeaking()
                })
                pendingSpeech?.let { speak(it.first, it.second) }
                pendingSpeech = null
            } else {
                _message.value = "Text-to-speech engine nahi mila. Phone Settings me TTS engine check karo."
            }
        }
    }

    private fun applyLanguage() {
        val r = tts?.setLanguage(Locale.forLanguageTag(settings.language))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.getDefault())
            _message.value = "Is bhasha ki awaaz phone me nahi hai. Settings > Text-to-speech me voice data download karo."
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            if (_state.value == VoiceState.LISTENING) _state.value = VoiceState.PROCESSING
        }

        override fun onError(error: Int) {
            _message.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "Kuch sunai nahi diya. Mic dabake dobara bolo."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission nahi mili."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Speech recognition ko internet chahiye ho sakta hai. Network check karo."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy hai. Thodi der baad try karo."
                SpeechRecognizer.ERROR_AUDIO -> "Audio record karne me dikkat aayi."
                else -> "Voice error ($error). Dobara koshish karo."
            }
            _state.value = VoiceState.ERROR
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            if (text.isNullOrEmpty()) {
                _message.value = "Kuch sunai nahi diya. Dobara bolo."
                _state.value = VoiceState.ERROR
            } else {
                _partial.value = text
                _state.value = VoiceState.PROCESSING
                onFinalResult?.invoke(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.let { _partial.value = it }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (!isRecognitionAvailable) {
            _message.value = "Is phone par speech recognition (Google app / Speech Services) available nahi hai."
            _state.value = VoiceState.ERROR
            return
        }
        stopSpeaking()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(listener) }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings.language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        _partial.value = ""
        _message.value = null
        _state.value = VoiceState.LISTENING
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.cancel()
        if (_state.value == VoiceState.LISTENING || _state.value == VoiceState.PROCESSING) {
            _state.value = VoiceState.IDLE
        }
    }

    fun speak(text: String, done: (() -> Unit)? = null) {
        val clean = cleanForSpeech(text)
        if (clean.isBlank()) {
            _state.value = VoiceState.IDLE
            done?.invoke()
            return
        }
        if (!ttsReady) {
            pendingSpeech = clean to done
            return
        }
        onSpeakDone = done
        _state.value = VoiceState.SPEAKING
        val limit = TextToSpeech.getMaxSpeechInputLength() - 1
        tts?.speak(clean.take(limit), TextToSpeech.QUEUE_FLUSH, null, "puja_utt")
    }

    fun stopSpeaking() {
        tts?.stop()
        onSpeakDone = null
        if (_state.value == VoiceState.SPEAKING) _state.value = VoiceState.IDLE
    }

    fun reset() {
        _state.value = VoiceState.IDLE
    }

    fun shutdown() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun finishSpeaking() {
        main.post {
            if (_state.value == VoiceState.SPEAKING) _state.value = VoiceState.IDLE
            val cb = onSpeakDone
            onSpeakDone = null
            cb?.invoke()
        }
    }

    private fun cleanForSpeech(text: String): String =
        text.replace(Regex("```[\\s\\S]*?(```|\\z)"), " Code chat screen par dekh lo. ")
            .replace(Regex("https?://\\S+"), " link ")
            .replace(Regex("[*_#`>|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}

package com.phonosassist.voice

import android.content.Context
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FINALIZE_TIMEOUT_MS = 3_000L

/**
 * Orchestrates one voice turn: capture ([SpeechRecognizerEngine]) → final
 * transcript → (host sends it to an LLM) → speak ([SpeechSynthesizer]). A
 * [VoiceSessionKeepAlive] keeps the process alive meanwhile.
 *
 * All collaborators are interfaces with Android defaults, so a different host
 * app (e.g. Cobaltium) can inject its own recognizer/synthesizer/keep-alive
 * without changing this class.
 */
class VoiceAssistantController(
    context: Context,
    private val scope: CoroutineScope,
    private val synthesizer: SpeechSynthesizer = AndroidTtsSynthesizer(context),
    private val keepAlive: VoiceSessionKeepAlive = ForegroundSessionKeepAlive(context),
    private val recognizerFactory: (useWhisper: Boolean) -> SpeechRecognizerEngine = { useWhisper ->
        if (useWhisper) WhisperSpeechRecognizer(context, scope) else PlatformSpeechRecognizer(context)
    },
) {
    // ── Callbacks ────────────────────────────────────────────────────────
    var onPartialTranscript: (String) -> Unit = {}
    var onFinalTranscript: (String) -> Unit = {}
    var onListeningChanged: (Boolean) -> Unit = {}
    var onFinalizingChanged: (Boolean) -> Unit = {}
    var onSpeakingChanged: (Boolean) -> Unit = {}
    /** [message] null means a generic "no speech recognized" condition. */
    var onError: (String?) -> Unit = {}

    // ── Configuration (kept in sync from settings) ───────────────────────
    var useWhisper: Boolean = false

    /** ISO-639-1 code the user speaks (used by Whisper). */
    var inputLanguageCode: String = "en"

    /** BCP-47 tag the user speaks (used by the platform recognizer). */
    var inputSttTag: String = "en-US"

    var preferOffline: Boolean = true
    var ttsEnabled: Boolean = true

    /** True when the offline Whisper engine is installed and loadable. */
    var whisperAvailable: Boolean = false

    /** BCP-47 tag for the reply language; null = auto (mixed-language TTS). */
    var responseLanguageTag: String? = null
        set(value) {
            field = value
            synthesizer.languageTag = value
        }

    private var recognizer: SpeechRecognizerEngine? = null
    private var sessionId = 0
    private var partial = ""
    private var capturing = false
    private var spokeThisTurn = false
    private var finalizeJob: Job? = null
    private var usedWhisper = false
    private var forceWhisper = false

    /** Wires collaborators. Call once, e.g. from a ViewModel init. */
    fun start() {
        synthesizer.onComplete = {
            onSpeakingChanged(false)
            keepAlive.endSession()
        }
        keepAlive.onStopRequested = {
            cancel()
            stopSpeaking()
        }
    }

    fun beginCapture() {
        resetCapture()
        partial = ""
        spokeThisTurn = false
        synthesizer.stop()
        onSpeakingChanged(false)
        keepAlive.startSession()
        capturing = true
        val id = sessionId

        val whisper = (useWhisper || forceWhisper) && whisperAvailable
        usedWhisper = whisper
        val engine = recognizerFactory(whisper)
        recognizer = engine
        engine.start(
            language = if (whisper) inputLanguageCode else inputSttTag,
            preferOffline = preferOffline,
            callbacks = object : SpeechRecognizerEngine.Callbacks {
                override fun onReady() {
                    if (id == sessionId) onListeningChanged(true)
                }

                override fun onPartial(text: String) {
                    if (id != sessionId) return
                    partial = text
                    onPartialTranscript(text)
                }

                override fun onFinal(text: String) {
                    if (id == sessionId) finalize(text)
                }

                override fun onError(code: Int, message: String?) {
                    if (id == sessionId) handleCaptureError(code, message)
                }
            },
        )
    }

    /** Stops capture; the final transcript (or a timeout fallback) ends it. */
    fun stopCapture() {
        if (!capturing) return
        onListeningChanged(false)
        onFinalizingChanged(true)
        recognizer?.stop()
        finalizeJob?.cancel()
        finalizeJob = scope.launch {
            delay(FINALIZE_TIMEOUT_MS)
            if (capturing) finalize(partial)
        }
    }

    /** Speaks a complete reply in one go (non-streaming fallback). */
    fun speak(text: String) {
        if (!ttsEnabled || text.isBlank() || !synthesizer.speak(text)) {
            // Nothing will play, so never leave callers waiting for a
            // completion callback (which also keeps the record button enabled).
            endSession()
            return
        }
        spokeThisTurn = true
        onSpeakingChanged(true)
    }

    /** Queues a sentence behind what is already speaking (streaming TTS). */
    fun enqueueSpeech(text: String) {
        if (!ttsEnabled || text.isBlank()) return
        if (!synthesizer.enqueue(text)) return
        if (!spokeThisTurn) {
            spokeThisTurn = true
            onSpeakingChanged(true)
        }
    }

    /** Called once the reply finished streaming; ends the session if silent. */
    fun finishSpeaking() {
        if (!spokeThisTurn) endSession()
    }

    fun stopSpeaking() {
        synthesizer.stop()
        onSpeakingChanged(false)
    }

    /** Stops the keep-alive once the whole turn is finished. */
    fun endSession() {
        keepAlive.endSession()
    }

    /** Aborts the current turn (new chat / permission change). */
    fun cancel() {
        forceWhisper = false
        resetCapture()
        onSpeakingChanged(false)
        endSession()
    }

    fun shutdown() {
        resetCapture()
        synthesizer.stop()
        synthesizer.release()
        endSession()
    }

    private fun resetCapture() {
        capturing = false
        spokeThisTurn = false
        sessionId++
        finalizeJob?.cancel()
        finalizeJob = null
        partial = ""
        onListeningChanged(false)
        onFinalizingChanged(false)
        recognizer?.release()
        recognizer = null
    }

    private fun handleCaptureError(code: Int, message: String?) {
        onListeningChanged(false)
        if (partial.isNotBlank()) {
            finalize(partial)
            return
        }

        // The platform recognizer can't handle this language on this device:
        // retry once with the offline Whisper engine when it's installed.
        if (!usedWhisper && whisperAvailable &&
            (code == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                code == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        ) {
            forceWhisper = true
            recognizer?.release()
            recognizer = null
            beginCapture()
            onError("Device speech lacks this language; using offline Whisper")
            return
        }

        // Fully reset so the finalize timeout can't also fire and show a
        // second, misleading "speech error" after the real one.
        capturing = false
        finalizeJob?.cancel()
        finalizeJob = null
        partial = ""
        onFinalizingChanged(false)
        recognizer?.release()
        recognizer = null
        onError(message)
        endSession()
    }

    private fun finalize(text: String) {
        if (!capturing) return
        capturing = false
        finalizeJob?.cancel()
        finalizeJob = null
        onFinalizingChanged(false)
        onListeningChanged(false)
        recognizer?.release()
        recognizer = null
        val trimmed = text.trim()
        partial = ""
        if (trimmed.isEmpty()) {
            onError(null)
            endSession()
        } else {
            onFinalTranscript(trimmed)
        }
    }
}

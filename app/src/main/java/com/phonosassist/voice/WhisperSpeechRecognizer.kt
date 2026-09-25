package com.phonosassist.voice

import android.content.Context
import android.util.Log
import com.phonosassist.service.SherpaOnnxService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "WhisperRecognizer"

/**
 * [SpeechRecognizerEngine] backed by the offline sherpa-onnx Whisper model.
 * Initialization (loading the model) happens on a background thread, so
 * [Callbacks.onReady] fires only once capture has actually started.
 */
class WhisperSpeechRecognizer(
    context: Context,
    private val scope: CoroutineScope,
) : SpeechRecognizerEngine {

    private val app = context.applicationContext
    private var service: SherpaOnnxService? = null
    private var sessionId = 0

    override fun start(
        language: String,
        preferOffline: Boolean,
        callbacks: SpeechRecognizerEngine.Callbacks,
    ) {
        val id = ++sessionId
        scope.launch {
            try {
                val recognizer = service ?: SherpaOnnxService(app).also { service = it }
                recognizer.language = language.take(2)

                // Wire callbacks *before* initialize so its failure reason (e.g.
                // "model files not found") is reported instead of a generic error.
                val reported = AtomicBoolean(false)
                recognizer.onResultCallback = { text -> if (id == sessionId) callbacks.onFinal(text) }
                recognizer.onErrorCallback = { error ->
                    reported.set(true)
                    if (id == sessionId) callbacks.onError(-1, error)
                }

                val initialized = withContext(Dispatchers.IO) { recognizer.initialize() }
                if (id != sessionId) return@launch
                if (!initialized) {
                    if (!reported.get()) callbacks.onError(-1, "Failed to initialize Whisper")
                    return@launch
                }
                val started = withContext(Dispatchers.IO) { recognizer.startRecording() }
                if (id != sessionId) return@launch
                if (started) callbacks.onReady() else callbacks.onError(-1, "Failed to start recording")
            } catch (e: Exception) {
                Log.e(TAG, "Whisper capture failed", e)
                if (id == sessionId) callbacks.onError(-1, e.message)
            }
        }
    }

    override fun stop() {
        service?.stopRecording()
    }

    override fun release() {
        sessionId++
        service?.destroy()
        service = null
    }
}

package com.phonosassist.voice

import android.content.Context
import android.speech.SpeechRecognizer
import android.util.Log
import com.phonosassist.service.SpeechService

/** [SpeechRecognizerEngine] backed by the platform Android `SpeechRecognizer`. */
class PlatformSpeechRecognizer(context: Context) : SpeechRecognizerEngine {

    private val app = context.applicationContext
    private var service: SpeechService? = null

    override fun start(
        language: String,
        preferOffline: Boolean,
        callbacks: SpeechRecognizerEngine.Callbacks,
    ) {
        val recognizer = SpeechService(app)
        service = recognizer
        recognizer.startListening(
            language = language,
            preferOffline = preferOffline,
            onResult = { result ->
                if (result.isFinal) callbacks.onFinal(result.text) else callbacks.onPartial(result.text)
            },
            onStarted = { callbacks.onReady() },
            onEnded = { /* one-shot: the final result ends it */ },
            onError = { code, message ->
                Log.w(TAG, "SpeechRecognizer error code=$code (available=${SpeechRecognizer.isRecognitionAvailable(app)}) $message")
                callbacks.onError(code, message)
            },
            onResultReceived = { /* handled by the controller */ },
        )
    }

    override fun stop() {
        service?.stopListening()
    }

    override fun release() {
        service?.destroy()
        service = null
    }

    private companion object {
        const val TAG = "PlatformSTT"
    }
}

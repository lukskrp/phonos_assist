package com.phonosassist.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

data class SpeechResult(
    val text: String,
    val isFinal: Boolean,
)

class SpeechService(context: Context) {
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    private var onResultCallback: ((SpeechResult) -> Unit)? = null
    private var onStartedCallback: (() -> Unit)? = null
    private var onEndedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((Int, String?) -> Unit)? = null
    private var onResultReceivedCallback: (() -> Unit)? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onStartedCallback?.invoke()
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            onEndedCallback?.invoke()
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported"
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language unavailable"
                else -> "Unknown error: $error"
            }
            onErrorCallback?.invoke(error, message)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            onResultCallback?.invoke(SpeechResult(text = text, isFinal = true))
            onResultReceivedCallback?.invoke()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotEmpty()) {
                onResultCallback?.invoke(SpeechResult(text = text, isFinal = false))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        speechRecognizer.setRecognitionListener(listener)
    }

    fun startListening(
        language: String = "en-US",
        preferOffline: Boolean = false,
        onResult: (SpeechResult) -> Unit,
        onStarted: () -> Unit = {},
        onEnded: () -> Unit = {},
        onError: (Int, String?) -> Unit = { _, _ -> },
        onResultReceived: () -> Unit = {},
    ) {
        onResultCallback = onResult
        onStartedCallback = onStarted
        onEndedCallback = onEnded
        onErrorCallback = onError
        onResultReceivedCallback = onResultReceived

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)

            if (preferOffline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra("android.speech.extra.PREFER_OFFLINE", true)
            }
        }
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun destroy() {
        speechRecognizer.destroy()
    }
}

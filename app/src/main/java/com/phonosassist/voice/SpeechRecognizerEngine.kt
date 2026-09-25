package com.phonosassist.voice

/**
 * Speech-to-text abstraction for the [VoiceAssistantController]. The default
 * implementations are [PlatformSpeechRecognizer] (Android `SpeechRecognizer`)
 * and [WhisperSpeechRecognizer] (offline sherpa-onnx Whisper).
 *
 * A different host app can provide its own engine (e.g. Cobaltium's existing STT)
 * without changing the controller.
 */
interface SpeechRecognizerEngine {

    fun start(
        language: String,
        preferOffline: Boolean,
        callbacks: Callbacks,
    )

    /** Requests the final result; [Callbacks.onFinal] should follow. */
    fun stop()

    /** Releases native/listener resources. */
    fun release()

    interface Callbacks {
        fun onReady()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(code: Int, message: String?)
    }
}

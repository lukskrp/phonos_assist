package com.phonosassist.voice

import android.content.Context
import com.phonosassist.service.TtsService

/**
 * Text-to-speech abstraction for the [VoiceAssistantController]. The default is
 * [AndroidTtsSynthesizer]; another app (e.g. Cobaltium) can supply an adapter
 * over its own engine (e.g. PiperTtsEngine) instead.
 */
interface SpeechSynthesizer {
    /** Fired when every queued utterance has finished (or errored). */
    var onComplete: (() -> Unit)?

    /**
     * BCP-47 tag to speak in, or null to auto-detect mixed languages. Set from
     * the user's chosen response language.
     */
    var languageTag: String?

    /** Speaks [text], replacing anything currently queued. Returns false if the
     *  engine could not queue it (e.g. not initialized). */
    fun speak(text: String): Boolean

    /** Queues [text] after what is already speaking (streaming replies).
     *  Returns false if the engine could not queue it. */
    fun enqueue(text: String): Boolean

    /** True when this engine can speak [tag] (BCP-47) natively. */
    fun supportsLanguage(tag: String?): Boolean = true

    fun stop()

    fun release()
}

/** Default [SpeechSynthesizer] backed by the Android TTS wrapper. */
class AndroidTtsSynthesizer(context: Context) : SpeechSynthesizer {

    private val tts = TtsService(context)

    override var onComplete: (() -> Unit)? = null
        set(value) {
            field = value
            tts.onSpeakingComplete = { field?.invoke() }
        }

    override var languageTag: String? = null
        set(value) {
            field = value
            tts.responseLanguageTag = value
        }

    override fun supportsLanguage(tag: String?): Boolean = tts.isLanguageAvailable(tag)

    override fun speak(text: String) = tts.speakMixedLanguage(text)

    override fun enqueue(text: String) = tts.enqueueMixedLanguage(text)

    override fun stop() = tts.stopSpeaking()

    override fun release() = tts.shutdown()
}

package com.phonosassist.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.phonosassist.domain.MixedLanguageSegmenter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android TTS wrapper that speaks mixed English/Finnish replies with the right
 * voice per segment. Holds a transient audio focus while speaking so other audio
 * ducks/stops, and reports completion only when every queued utterance is done.
 */
class TtsService(context: Context) : TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var finnishSupported = false
    private val ttsGeneration = AtomicInteger(0)

    /**
     * When set, the whole reply is spoken in this BCP-47 locale and the EN/FI
     * mixed-language segmentation is bypassed. Null keeps the segmentation
     * (used when the response language is "auto").
     */
    var responseLanguageTag: String? = null
    private val audioFocus = AudioFocusHelper(context)

    // Ids of utterances currently queued/speaking. Completion fires only when the
    // set drains, so overlapping calls and QUEUE_ADD don't report completion early.
    private val pendingUtterances: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun utteranceFinished(utteranceId: String) {
        pendingUtterances.remove(utteranceId)
        if (pendingUtterances.isEmpty()) {
            audioFocus.abandon()
            onSpeakingComplete?.invoke()
        }
    }

    var onInitComplete: (() -> Unit)? = null
    var onInitError: (() -> Unit)? = null
    var onSpeakingComplete: (() -> Unit)? = null

    init {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val enResult = textToSpeech?.setLanguage(Locale.UK)
            if (enResult == null || enResult == TextToSpeech.LANG_MISSING_DATA || enResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.setLanguage(Locale.ENGLISH)
            }
            val fiResult = textToSpeech?.setLanguage(Locale.forLanguageTag("fi-FI"))
            finnishSupported = fiResult != null &&
                fiResult != TextToSpeech.LANG_MISSING_DATA &&
                fiResult != TextToSpeech.LANG_NOT_SUPPORTED
            textToSpeech?.setLanguage(Locale.UK)

            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(utteranceId: String) = utteranceFinished(utteranceId)

                override fun onError(utteranceId: String) = utteranceFinished(utteranceId)

                @Deprecated("Deprecated in API 26", ReplaceWith("onDone(utteranceId)"))
                override fun onError(utteranceId: String, errorCode: Int) = utteranceFinished(utteranceId)

                @Deprecated("Deprecated in API 26", ReplaceWith("onStart(utteranceId)"))
                override fun onStart(utteranceId: String) {}

                @Suppress("OVERRIDE_DEPRECATION")
                @Deprecated("Deprecated in API 26", ReplaceWith("onDone(utteranceId)"))
                override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {}
            })
            isInitialized = true
            onInitComplete?.invoke()
        } else {
            isInitialized = false
            onInitError?.invoke()
        }
    }

    /** True when the engine reports [tag] (BCP-47) as available/installed. */
    fun isLanguageAvailable(tag: String?): Boolean {
        if (tag.isNullOrBlank()) return true
        val engine = textToSpeech ?: return false
        val result = engine.isLanguageAvailable(Locale.forLanguageTag(tag))
        return result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun speak(text: String) {
        if (!isInitialized || text.isBlank()) return
        audioFocus.request()
        ttsGeneration.incrementAndGet()
        pendingUtterances.clear()
        val utteranceId = UUID.randomUUID().toString()
        pendingUtterances.add(utteranceId)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    /**
     * Speaks [text], switching to the Finnish voice for Finnish segments.
     * Returns true when audio was actually queued (false when TTS is
     * unavailable, so callers don't wait forever for a completion callback).
     */
    fun speakMixedLanguage(text: String): Boolean {
        if (!isInitialized || text.isBlank()) return false
        audioFocus.request()
        val gen = ttsGeneration.incrementAndGet()
        pendingUtterances.clear()

        // Explicit response language: speak the whole reply in that locale.
        responseLanguageTag?.let { tag ->
            textToSpeech?.setLanguage(Locale.forLanguageTag(tag))
            val utteranceId = UUID.randomUUID().toString()
            pendingUtterances.add(utteranceId)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            return true
        }

        val segments = MixedLanguageSegmenter.segments(text)
        if (segments.isEmpty()) return false

        for (segment in segments) {
            if (ttsGeneration.get() != gen) return true
            val locale = if (segment.isFinnish && finnishSupported) {
                Locale.forLanguageTag("fi-FI")
            } else {
                Locale.UK
            }
            textToSpeech?.setLanguage(locale)
            val utteranceId = UUID.randomUUID().toString()
            pendingUtterances.add(utteranceId)
            textToSpeech?.speak(segment.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
        return true
    }

    /**
     * Queues a chunk *behind* whatever is already speaking, used to speak
     * sentences as an LLM reply streams in. Does not reset the queue.
     */
    fun enqueueMixedLanguage(text: String): Boolean {
        if (!isInitialized || text.isBlank()) return false
        audioFocus.request()

        responseLanguageTag?.let { tag ->
            textToSpeech?.setLanguage(Locale.forLanguageTag(tag))
            val utteranceId = UUID.randomUUID().toString()
            pendingUtterances.add(utteranceId)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            return true
        }

        val segments = MixedLanguageSegmenter.segments(text)
        if (segments.isEmpty()) return false

        val gen = ttsGeneration.get()
        for (segment in segments) {
            if (ttsGeneration.get() != gen) return true
            val locale = if (segment.isFinnish && finnishSupported) {
                Locale.forLanguageTag("fi-FI")
            } else {
                Locale.UK
            }
            textToSpeech?.setLanguage(locale)
            val utteranceId = UUID.randomUUID().toString()
            pendingUtterances.add(utteranceId)
            textToSpeech?.speak(segment.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
        return true
    }

    fun stopSpeaking() {
        ttsGeneration.incrementAndGet()
        pendingUtterances.clear()
        textToSpeech?.stop()
        audioFocus.abandon()
    }

    fun shutdown() {
        ttsGeneration.incrementAndGet()
        pendingUtterances.clear()
        audioFocus.abandon()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }
}

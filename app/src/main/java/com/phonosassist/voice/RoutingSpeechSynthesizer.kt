package com.phonosassist.voice

/**
 * Picks the best engine per language: the offline [piper] engine when it has a
 * bundled voice for the requested language, otherwise the device TTS. If neither
 * supports the language, it warns and falls back to English.
 */
class RoutingSpeechSynthesizer(
    private val piper: SpeechSynthesizer,
    private val device: SpeechSynthesizer,
    private val onWarn: (String) -> Unit,
) : SpeechSynthesizer {

    private var current: String? = null

    override var onComplete: (() -> Unit)? = null
        set(value) {
            field = value
            piper.onComplete = value
            device.onComplete = value
        }

    override var languageTag: String?
        get() = current
        set(value) {
            current = value
            piper.languageTag = value
            device.languageTag = value
        }

    override fun supportsLanguage(tag: String?): Boolean =
        piper.supportsLanguage(tag) || device.supportsLanguage(tag)

    override fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        val tag = current
        if (piper.supportsLanguage(tag)) {
            if (piper.speak(text)) return true
            onWarn("Offline voice unavailable; using device speech")
        }
        if (device.supportsLanguage(tag)) {
            device.languageTag = tag
            return device.speak(text)
        }
        onWarn("No ${tag ?: "selected"} voice installed; speaking English")
        device.languageTag = ENGLISH
        return device.speak(text)
    }

    override fun enqueue(text: String): Boolean {
        if (text.isBlank()) return false
        val tag = current
        if (piper.supportsLanguage(tag)) {
            if (piper.enqueue(text)) return true
            onWarn("Offline voice unavailable; using device speech")
        }
        if (device.supportsLanguage(tag)) {
            device.languageTag = tag
            return device.enqueue(text)
        }
        onWarn("No ${tag ?: "selected"} voice installed; speaking English")
        device.languageTag = ENGLISH
        return device.enqueue(text)
    }

    override fun stop() {
        piper.stop()
        device.stop()
    }

    override fun release() {
        piper.release()
        device.release()
    }

    private companion object {
        const val ENGLISH = "en-US"
    }
}

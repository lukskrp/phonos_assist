package com.phonosassist.domain

/**
 * A language the assistant can record in / reply in. [code] is the ISO-639-1
 * code (used by Whisper), [sttTag] a BCP-47 tag for the platform recognizer, and
 * [ttsTag] a BCP-47 tag for text-to-speech.
 */
data class AssistantLanguage(
    val code: String,
    val label: String,
    val endonym: String,
    val sttTag: String,
    val ttsTag: String,
)

/** Curated set of languages offered in the header language bar. */
object AssistantLanguages {

    const val AUTO = "auto"

    val all: List<AssistantLanguage> = listOf(
        AssistantLanguage("en", "English", "English", "en-US", "en-US"),
        AssistantLanguage("fi", "Finnish", "suomi", "fi-FI", "fi-FI"),
        AssistantLanguage("sv", "Swedish", "Svenska", "sv-SE", "sv-SE"),
        AssistantLanguage("de", "German", "Deutsch", "de-DE", "de-DE"),
        AssistantLanguage("fr", "French", "Français", "fr-FR", "fr-FR"),
        AssistantLanguage("es", "Spanish", "Español", "es-ES", "es-ES"),
        AssistantLanguage("pt", "Portuguese", "Português", "pt-BR", "pt-BR"),
        AssistantLanguage("it", "Italian", "Italiano", "it-IT", "it-IT"),
        AssistantLanguage("nl", "Dutch", "Nederlands", "nl-NL", "nl-NL"),
        AssistantLanguage("da", "Danish", "Dansk", "da-DK", "da-DK"),
        AssistantLanguage("nb", "Norwegian", "Norsk", "nb-NO", "nb-NO"),
        AssistantLanguage("pl", "Polish", "Polski", "pl-PL", "pl-PL"),
        AssistantLanguage("ru", "Russian", "Русский", "ru-RU", "ru-RU"),
        AssistantLanguage("uk", "Ukrainian", "Українська", "uk-UA", "uk-UA"),
        AssistantLanguage("cs", "Czech", "Čeština", "cs-CZ", "cs-CZ"),
        AssistantLanguage("tr", "Turkish", "Türkçe", "tr-TR", "tr-TR"),
        AssistantLanguage("ar", "Arabic", "العربية", "ar-SA", "ar-SA"),
        AssistantLanguage("he", "Hebrew", "עברית", "he-IL", "he-IL"),
        AssistantLanguage("hi", "Hindi", "हिन्दी", "hi-IN", "hi-IN"),
        AssistantLanguage("ja", "Japanese", "日本語", "ja-JP", "ja-JP"),
        AssistantLanguage("ko", "Korean", "한국어", "ko-KR", "ko-KR"),
        AssistantLanguage("zh", "Chinese", "中文", "zh-CN", "zh-CN"),
        AssistantLanguage("id", "Indonesian", "Bahasa Indonesia", "id-ID", "id-ID"),
        AssistantLanguage("vi", "Vietnamese", "Tiếng Việt", "vi-VN", "vi-VN"),
    )

    fun byCode(code: String): AssistantLanguage? =
        all.firstOrNull { it.code.equals(code, ignoreCase = true) }

    fun label(code: String): String = byCode(code)?.label ?: code

    fun sttTag(code: String, fallback: String = "en-US"): String =
        byCode(code)?.sttTag ?: fallback

    fun ttsTag(code: String): String? = byCode(code)?.ttsTag
}

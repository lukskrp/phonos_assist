package com.phonosassist.data.settings

import com.phonosassist.domain.InputMode

/** Persisted assistant preferences. */
data class AssistantSettings(
    val llmHost: String = DEFAULT_HOST,
    val llmPort: String = DEFAULT_PORT,
    val llmModel: String = DEFAULT_MODEL,
    /** Max completion tokens. Reasoning models need a large budget. */
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val ttsEnabled: Boolean = true,
    val preferOfflineStt: Boolean = true,
    /** Use the offline Whisper recognizer instead of the platform one. */
    val useWhisper: Boolean = false,
    /** Language the user speaks (ISO-639-1, see AssistantLanguages). */
    val inputLanguage: String = "en",
    /** Language the assistant replies in; "auto" = match the input language. */
    val responseLanguage: String = "auto",
    /**
     * Persisted SAF tree URI of the folder last used for transcript exports, so
     * exports keep landing in the same directory without re-picking it. Empty
     * until the user chooses a folder for the first time.
     */
    val exportTreeUri: String = "",
    /** How the user provides prompts: voice recording or typed text. */
    val inputMode: InputMode = InputMode.VOICE,
) {
    companion object {
        const val DEFAULT_HOST = "100.76.124.106"
        const val DEFAULT_PORT = "3500"
        const val DEFAULT_MODEL = "Qwen3.6 35B A3B NVFP4 (FreeToken)"
        const val DEFAULT_MAX_TOKENS = 32_768
        const val MAX_TOKENS_LIMIT = 131_072
        val Default = AssistantSettings()
    }
}

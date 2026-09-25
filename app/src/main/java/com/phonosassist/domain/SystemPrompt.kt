package com.phonosassist.domain

/**
 * Builds the voice-assistant system prompt. When [responseLanguageCode] is null
 * or [AssistantLanguages.AUTO], the model matches the user's language; otherwise
 * it is told to always answer in that language.
 */
object SystemPrompt {

    fun build(responseLanguageCode: String?): String {
        val intro = """
            You are a helpful, concise voice assistant. The user is speaking to you naturally.

            Infer the topic and the user's intent from the conversation itself. Do not assume any
            particular subject or field, and do not steer the conversation toward one.
        """.trimIndent()

        val language = if (responseLanguageCode.isNullOrBlank() ||
            responseLanguageCode.equals(AssistantLanguages.AUTO, ignoreCase = true)
        ) {
            "Reply in the same language the user is using."
        } else {
            "Always reply in ${AssistantLanguages.label(responseLanguageCode)}."
        }

        val style = """
            Keep answers focused and easy to read aloud: short sentences, no code blocks, tables, or
            markup, and no content unrelated to what the user asked.
        """.trimIndent()

        return "$intro\n\n$language\n\n$style"
    }
}

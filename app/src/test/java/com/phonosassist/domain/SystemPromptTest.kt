package com.phonosassist.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptTest {

    @Test
    fun `auto matches the user language`() {
        val prompt = SystemPrompt.build(AssistantLanguages.AUTO)
        assertTrue(prompt.contains("same language"))
    }

    @Test
    fun `null behaves like auto`() {
        assertTrue(SystemPrompt.build(null).contains("same language"))
    }

    @Test
    fun `explicit language is named in the prompt`() {
        val prompt = SystemPrompt.build("fi")
        assertTrue(prompt.contains("Finnish"))
    }
}

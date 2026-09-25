package com.phonosassist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantLanguagesTest {

    @Test
    fun `codes are unique`() {
        val codes = AssistantLanguages.all.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `lookup by code`() {
        assertEquals("suomi", AssistantLanguages.byCode("fi")?.endonym)
        assertEquals("fi-FI", AssistantLanguages.sttTag("fi"))
        assertEquals("en-US", AssistantLanguages.ttsTag("en"))
    }

    @Test
    fun `unknown code has no tts tag`() {
        assertNull(AssistantLanguages.ttsTag("zz"))
        assertEquals("en-US", AssistantLanguages.sttTag("zz"))
    }
}

package com.phonosassist.domain

import com.phonosassist.data.ChatMessageEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextTrimmerTest {

    private fun msg(content: String) = ChatMessageEntry(role = "user", content = content, timestamp = 0L)

    @Test
    fun `keeps everything under the budget`() {
        val messages = listOf(msg("a"), msg("bb"), msg("ccc"))
        assertEquals(messages, trimContextForModel(messages, charBudget = 100))
    }

    @Test
    fun `drops oldest once over budget`() {
        val messages = listOf(msg("aaaaa"), msg("bbbbb"), msg("ccccc"))
        val trimmed = trimContextForModel(messages, charBudget = 11)
        assertEquals(listOf("bbbbb", "ccccc"), trimmed.map { it.content })
    }

    @Test
    fun `always keeps at least the last message`() {
        val messages = listOf(msg("x".repeat(100)))
        assertEquals(1, trimContextForModel(messages, charBudget = 1).size)
    }

    @Test
    fun `empty in empty out`() {
        assertEquals(emptyList<ChatMessageEntry>(), trimContextForModel(emptyList(), charBudget = 10))
    }
}

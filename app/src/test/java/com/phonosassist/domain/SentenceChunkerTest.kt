package com.phonosassist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceChunkerTest {

    @Test
    fun `splits a complete sentence and keeps the remainder`() {
        val (sentences, remainder) = SentenceChunker.split("Hello there. How are you")
        assertEquals(listOf("Hello there."), sentences)
        assertEquals(" How are you", remainder)
    }

    @Test
    fun `splits multiple sentences`() {
        val (sentences, remainder) = SentenceChunker.split("One. Two! Three? Four")
        assertEquals(listOf("One.", "Two!", "Three?"), sentences)
        assertEquals(" Four", remainder)
    }

    @Test
    fun `no boundary keeps everything as remainder`() {
        val (sentences, remainder) = SentenceChunker.split("no punctuation yet")
        assertTrue(sentences.isEmpty())
        assertEquals("no punctuation yet", remainder)
    }

    @Test
    fun `newline is a boundary`() {
        val (sentences, remainder) = SentenceChunker.split("line one\nline two")
        assertEquals(listOf("line one"), sentences)
        assertEquals("line two", remainder)
    }
}

package com.phonosassist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedLanguageSegmenterTest {

    @Test
    fun `plain english is one segment`() {
        val segments = MixedLanguageSegmenter.segments("hello world")
        assertEquals(1, segments.size)
        assertFalse(segments[0].isFinnish)
    }

    @Test
    fun `finnish special characters split the segment`() {
        val segments = MixedLanguageSegmenter.segments("hello tämä")
        assertTrue(segments.any { it.isFinnish })
        assertTrue(segments.any { !it.isFinnish })
    }

    @Test
    fun `dictionary catches finnish words without special characters`() {
        val segments = MixedLanguageSegmenter.segments("kiitos")
        assertEquals(1, segments.size)
        assertTrue(segments[0].isFinnish)
    }

    @Test
    fun `blank text yields no segments`() {
        assertTrue(MixedLanguageSegmenter.segments("   ").isEmpty())
    }
}

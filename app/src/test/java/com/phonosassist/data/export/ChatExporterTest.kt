package com.phonosassist.data.export

import com.google.gson.JsonParser
import com.phonosassist.data.ChatMessageEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExporterTest {

    @Test
    fun emitsCobaltiumSchema() {
        val json = ChatExporter.buildExportJson(
            title = "My Chat",
            createdAt = 100,
            updatedAt = 200,
            messages = listOf(
                ChatMessageEntry("user", "Hello", 100),
                ChatMessageEntry("assistant", "Hei", 200),
            ),
        )
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals("cobaltium.chat", root["format"].asString)
        assertEquals(1, root["version"].asInt)
        assertEquals("phonos-assist", root["source"].asString)

        val thread = root["thread"].asJsonObject
        assertEquals("My Chat", thread["title"].asString)
        assertEquals("conversation", thread["mode"].asString)
        assertEquals(100L, thread["createdAt"].asLong)
        assertEquals(200L, thread["updatedAt"].asLong)

        val messages = root["messages"].asJsonArray
        assertEquals(2, messages.size())
        assertEquals("user", messages[0].asJsonObject["role"].asString)
        assertEquals("Hello", messages[0].asJsonObject["content"].asString)
        assertEquals(100L, messages[0].asJsonObject["createdAt"].asLong)
        assertEquals("assistant", messages[1].asJsonObject["role"].asString)
        assertEquals("Hei", messages[1].asJsonObject["content"].asString)
    }

    @Test
    fun suggestedFileNameSanitized() {
        val name = ChatExporter.suggestedFileName("Hello / world: test?", 0L)
        assertTrue(name.startsWith("transcript-Hello-world-test-"))
        assertTrue(name.endsWith(".json"))
    }
}

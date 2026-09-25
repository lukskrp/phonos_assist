package com.phonosassist.data.export

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.phonosassist.data.ChatMessageEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serializes a conversation into the cross-app `cobaltium.chat` v1 JSON schema so
 * it can be imported into Cobaltium as a new chat. The wire field names are fixed
 * here (never derived from the internal models) to keep the format stable.
 *
 * PhonosAssist stores no per-message language, translation, or gloss data, so an
 * imported conversation carries text + roles + timestamps only.
 */
object ChatExporter {

    const val FORMAT = "cobaltium.chat"
    const val VERSION = 1

    private const val SOURCE = "phonos-assist"

    /** PhonosAssist has no chat modes; imported conversations open as Conversation. */
    private const val DEFAULT_MODE = "conversation"

    private const val NAME_MAX = 40
    private const val FALLBACK_NAME = "transcript"

    private val gson = Gson()
    private val illegalNameChars = Regex("[^A-Za-z0-9._-]+")

    /** Builds the full export document for one conversation. */
    fun buildExportJson(
        title: String,
        createdAt: Long,
        updatedAt: Long,
        messages: List<ChatMessageEntry>,
    ): String {
        val thread = JsonObject().apply {
            addProperty("title", title)
            addProperty("mode", DEFAULT_MODE)
            addProperty("createdAt", createdAt)
            addProperty("updatedAt", updatedAt)
        }
        val messageArray = JsonArray().apply {
            messages.forEach { entry ->
                add(
                    JsonObject().apply {
                        addProperty("role", entry.role)
                        addProperty("content", entry.content)
                        addProperty("createdAt", entry.timestamp)
                    },
                )
            }
        }
        val root = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            addProperty("exportedAt", System.currentTimeMillis())
            addProperty("source", SOURCE)
            add("thread", thread)
            add("messages", messageArray)
        }
        return gson.toJson(root)
    }

    /** A filesystem-safe `.json` file name derived from the session title + timestamp. */
    fun suggestedFileName(title: String, timestamp: Long): String {
        val safe = title.trim()
            .replace(illegalNameChars, "-")
            .trim('-')
            .take(NAME_MAX)
            .ifBlank { FALLBACK_NAME }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestamp))
        return "transcript-$safe-$stamp.json"
    }
}

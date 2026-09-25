package com.phonosassist.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for chat sessions. Messages are stored as one JSON blob per
 * session, so reads are defensive: a malformed/older blob yields an empty list
 * instead of crashing.
 */
class ChatRepository(
    private val dao: ChatSessionDao,
    private val gson: Gson = Gson(),
) {
    private val messagesType = object : TypeToken<List<ChatMessageEntry>>() {}.type

    val sessions: Flow<List<ChatSession>> = dao.getAll()

    /**
     * Upsert [messages] for [existingId] (or create a new session), returning the
     * session id. Callers should capture the id/messages before clearing state so
     * an in-flight save can never resurrect a session they just replaced.
     */
    suspend fun saveSession(existingId: Long?, messages: List<ChatMessageEntry>): Long {
        val json = gson.toJson(messages)
        val title = titleFor(messages)
        val now = System.currentTimeMillis()
        return try {
            if (existingId != null) {
                // Upsert: the DAO uses REPLACE, so re-using the id updates the row.
                dao.insert(
                    ChatSession(
                        id = existingId,
                        title = title,
                        messagesJson = json,
                        timestamp = now,
                    ),
                )
                existingId
            } else {
                dao.insert(
                    ChatSession(
                        title = title,
                        messagesJson = json,
                        timestamp = now,
                    ),
                )
            }
        } catch (e: Exception) {
            // Persistence must never crash a voice turn.
            Log.e(TAG, "Failed to persist session $existingId", e)
            existingId ?: 0L
        }
    }

    /** Decoded messages for [session]; empty when the blob cannot be parsed. */
    fun messages(session: ChatSession): List<ChatMessageEntry> =
        runCatching { gson.fromJson<List<ChatMessageEntry>>(session.messagesJson, messagesType) }
            .getOrNull()
            ?.filter { it.content.isNotBlank() }
            ?: emptyList()

    /** Display title for a conversation, derived from its first message. */
    fun titleFor(messages: List<ChatMessageEntry>): String {
        val first = messages.firstOrNull()?.content?.trim().orEmpty()
        return when {
            first.isEmpty() -> FALLBACK_TITLE
            first.length > TITLE_MAX -> first.take(TITLE_MAX).trimEnd() + "..."
            else -> first
        }
    }

    private companion object {
        const val TAG = "ChatRepository"
        const val TITLE_MAX = 50
        const val FALLBACK_TITLE = "Chat"
    }
}

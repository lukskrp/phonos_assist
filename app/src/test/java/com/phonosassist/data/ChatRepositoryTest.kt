package com.phonosassist.data

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryTest {

    private class FakeDao : ChatSessionDao {
        private val items = LinkedHashMap<Long, ChatSession>()
        private var nextId = 1L
        private val state = MutableStateFlow<List<ChatSession>>(emptyList())

        fun stored(id: Long): ChatSession? = items[id]

        override fun getAll(): Flow<List<ChatSession>> = state

        override suspend fun getById(id: Long): ChatSession? = items[id]

        override suspend fun insert(session: ChatSession): Long {
            val id = if (session.id == 0L) nextId++ else session.id
            items[id] = session.copy(id = id)
            state.value = items.values.sortedByDescending { it.timestamp }
            return id
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            state.value = items.values.sortedByDescending { it.timestamp }
        }

        override suspend fun deleteAll() {
            items.clear()
            state.value = emptyList()
        }
    }

    @Test
    fun `save then read round-trips the messages`() = runBlocking {
        val dao = FakeDao()
        val repo = ChatRepository(dao, Gson())
        val messages = listOf(
            ChatMessageEntry(role = "user", content = "hello", timestamp = 1L),
            ChatMessageEntry(role = "assistant", content = "hi", timestamp = 2L),
        )

        val id = repo.saveSession(null, messages)
        val stored = dao.stored(id)

        assertEquals(messages, repo.messages(stored!!))
    }

    @Test
    fun `upsert with an existing id keeps that id`() = runBlocking {
        val repo = ChatRepository(FakeDao(), Gson())
        val id = repo.saveSession(null, listOf(ChatMessageEntry("user", "a", 1L)))
        val id2 = repo.saveSession(
            id,
            listOf(ChatMessageEntry("user", "a", 1L), ChatMessageEntry("assistant", "b", 2L)),
        )
        assertEquals(id, id2)
    }

    @Test
    fun `malformed json yields an empty list instead of crashing`() {
        val repo = ChatRepository(FakeDao(), Gson())
        val session = ChatSession(id = 1, title = "x", messagesJson = "{not valid json", timestamp = 0L)
        assertTrue(repo.messages(session).isEmpty())
    }
}

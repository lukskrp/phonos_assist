package com.phonosassist.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val messagesJson: String,
    val timestamp: Long,
)

data class ChatMessageEntry(
    val role: String,
    val content: String,
    val timestamp: Long,
)

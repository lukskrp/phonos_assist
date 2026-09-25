package com.phonosassist.domain

import com.phonosassist.data.ChatMessageEntry

/**
 * Keeps the most recent [messages] within a rough character budget, so long
 * conversations don't overflow the model's context window. At least the final
 * message is always kept.
 */
fun trimContextForModel(
    messages: List<ChatMessageEntry>,
    charBudget: Int = 12_000,
): List<ChatMessageEntry> {
    if (messages.isEmpty()) return emptyList()
    val recent = ArrayDeque<ChatMessageEntry>()
    var chars = 0
    for (message in messages.asReversed()) {
        chars += message.content.length
        if (chars > charBudget && recent.isNotEmpty()) break
        recent.addFirst(message)
    }
    return recent.toList()
}

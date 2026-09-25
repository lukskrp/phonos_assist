package com.phonosassist.domain

/**
 * Splits streaming text into complete sentences so TTS can start speaking before
 * the whole reply has arrived. Pure Kotlin, so it is unit-testable.
 */
object SentenceChunker {

    // A sentence ends at . ! ? … followed by whitespace/end, or at a newline.
    private val boundary = Regex("""[.!?…](?=\s|$)|[\n]""")

    /**
     * Returns the complete sentences in [text] and the trailing fragment that has
     * not yet reached a boundary.
     */
    fun split(text: String): Pair<List<String>, String> {
        if (text.isBlank()) return emptyList<String>() to text
        val sentences = mutableListOf<String>()
        var start = 0
        for (match in boundary.findAll(text)) {
            val end = match.range.last + 1
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotEmpty()) sentences.add(sentence)
            start = end
        }
        return sentences to text.substring(start)
    }
}

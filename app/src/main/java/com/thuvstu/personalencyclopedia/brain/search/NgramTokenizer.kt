package com.thuvstu.personalencyclopedia.brain.search

/**
 * Bi-gram tokenizer for Japanese FTS4.
 * Since SQLite FTS4 doesn't have a Japanese morphological analyzer,
 * we split text into overlapping 2-character grams (Knowledge OS v10 TokenNgram strategy).
 */
object NgramTokenizer {

    fun tokenize(text: String): String {
        val cleaned = text
            .lowercase()
            .replace(Regex("[\\s\\u3000]+"), " ")
            .trim()

        if (cleaned.length <= 2) return cleaned

        val grams = mutableListOf<String>()
        for (i in 0 until cleaned.length - 1) {
            val gram = cleaned.substring(i, i + 2)
            if (gram.isNotBlank()) {
                grams.add(gram)
            }
        }
        return grams.joinToString(" ")
    }

    /**
     * Build FTS4 MATCH query from user input.
     * Splits into bi-grams and joins with OR for partial matching.
     */
    fun buildFtsQuery(userQuery: String): String {
        val cleaned = userQuery.lowercase().replace(Regex("[\\s\\u3000]+"), "").trim()
        if (cleaned.isEmpty()) return ""
        if (cleaned.length <= 2) return "\"$cleaned\""

        val grams = mutableListOf<String>()
        for (i in 0 until cleaned.length - 1) {
            grams.add("\"${cleaned.substring(i, i + 2)}\"")
        }
        return grams.joinToString(" OR ")
    }
}
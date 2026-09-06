package com.thuvstu.personalencyclopedia.brain.quiz

/**
 * 並べ替え・複数穴埋めの共通契約。
 * 空欄マーカーは全角「＿＿＿」、解答の区切りは `>`（全角・読点も受理）。
 */
object QuizFormatSupport {
    const val BLANK = "＿＿＿"

    private val SEQUENCE_SPLIT = Regex("[>＞、,，/|]+")

    fun blankCount(question: String): Int {
        if (!question.contains(BLANK)) return 0
        return question.split(BLANK).size - 1
    }

    fun splitSequence(text: String): List<String> =
        SEQUENCE_SPLIT.split(text).map { it.trim() }.filter { it.isNotEmpty() }

    fun joinSequence(parts: List<String>): String = parts.joinToString(">")

    /**
     * 設定に保存された出題形式。未設定、または旧既定（3種/4種の「全部オン」）なら現行 SUPPORTED に拡張する。
     * ユーザーが明示的に減らした集合はそのまま。
     */
    fun resolveEnabledTypes(storedCsv: String?, supported: Set<String>): Set<String> {
        if (storedCsv.isNullOrBlank()) return supported
        val parsed = storedCsv.split(",").map { it.trim() }.filter { it in supported }.toSet()
        if (parsed.isEmpty()) return supported
        val old3 = setOf("qa", "mcq", "fill_blank")
        val old4 = setOf("qa", "mcq", "fill_blank", "sort")
        return if (parsed == old3 || parsed == old4) supported else parsed
    }
}

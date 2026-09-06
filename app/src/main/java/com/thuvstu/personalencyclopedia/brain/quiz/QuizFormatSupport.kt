package com.thuvstu.personalencyclopedia.brain.quiz

/**
 * 並べ替え・複数穴埋め・複数選択・対応づけの共通契約。
 * 空欄マーカーは全角「＿＿＿」、解答の区切りは `>`（全角・読点も受理）。
 * 対応づけの選択肢は `左|右`、解答は `左=右` を `>` でつなぐ。
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

    fun parseMatchPairs(choices: List<String>): List<Pair<String, String>> =
        choices.mapNotNull { raw ->
            val i = raw.indexOf('|')
            if (i <= 0 || i == raw.lastIndex) null
            else {
                val left = raw.substring(0, i).trim()
                val right = raw.substring(i + 1).trim()
                if (left.isEmpty() || right.isEmpty()) null else left to right
            }
        }

    fun joinMatchAnswer(pairs: List<Pair<String, String>>): String =
        joinSequence(pairs.map { "${it.first}=${it.second}" })

    /**
     * 対応づけ解答の分割。`>` が右辺の不等式（e>1）に含まれる場合は直前トークンへ戻す。
     */
    fun splitMatchSequence(text: String): List<String> {
        val raw = Regex("[>＞]+").split(text).map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        for (part in raw) {
            if ('=' in part || '⇔' in part || out.isEmpty()) out += part
            else out[out.lastIndex] = out.last() + ">" + part
        }
        return out
    }

    /** 正規化済みトークンを「正しい」「誤り」へ畳む。ボタン以外の表記ゆれ用。 */
    fun canonicalizeTf(normalized: String): String? = when (normalized) {
        "正しい", "正", "true", "t", "o", "○", "yes", "1", "まる" -> "正しい"
        "誤り", "誤", "false", "f", "x", "×", "no", "0", "間違い", "不正" -> "誤り"
        else -> null
    }

    fun canonicalizeMatchToken(token: String, normalize: (String) -> String): String {
        val eq = token.indexOf('=').takeIf { it > 0 }
            ?: token.indexOf('⇔').takeIf { it > 0 }
            ?: -1
        if (eq <= 0) return normalize(token)
        return normalize(token.substring(0, eq)) + "=" + normalize(token.substring(eq + 1))
    }

    /**
     * 設定に保存された出題形式。未設定、または旧既定（3種/4種/5種の「全部オン」）なら現行 SUPPORTED に拡張する。
     * ユーザーが明示的に減らした集合はそのまま。
     */
    fun resolveEnabledTypes(storedCsv: String?, supported: Set<String>): Set<String> {
        if (storedCsv.isNullOrBlank()) return supported
        val parsed = storedCsv.split(",").map { it.trim() }.filter { it in supported }.toSet()
        if (parsed.isEmpty()) return supported
        val old3 = setOf("qa", "mcq", "fill_blank")
        val old4 = setOf("qa", "mcq", "fill_blank", "sort")
        val old5 = setOf("qa", "mcq", "fill_blank", "sort", "cloze")
        return if (parsed == old3 || parsed == old4 || parsed == old5) supported else parsed
    }
}

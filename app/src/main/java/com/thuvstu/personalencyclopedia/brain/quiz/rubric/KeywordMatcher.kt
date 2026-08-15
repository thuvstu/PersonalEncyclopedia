package com.thuvstu.personalencyclopedia.brain.quiz.rubric

/**
 * キーワード / フレーズの明示的一致判定(新採点システム.txt「Keyword / phrase」)。
 * 正規化部分一致 + 誤字脱字許容(類似度0.85以上)の2段構え。
 */
object KeywordMatcher {

    /** 正規化後にキーワードが部分一致するか */
    fun contains(text: String, keyword: String): Boolean {
        if (keyword.isBlank()) return false
        val nt = TextNorm.normalize(text)
        val nk = TextNorm.normalize(keyword)
        return nk.isNotEmpty() && nt.contains(nk)
    }

    /**
     * キーワードの達成度(0..1)。
     * 1.0 = 正規化部分一致 / 0.85以上 = 誤字脱字許容での近傍一致 / 0.0 = 未検出
     */
    fun match(text: String, keyword: String): Float {
        if (keyword.isBlank()) return 0f
        val nt = TextNorm.normalize(text)
        val nk = TextNorm.normalize(keyword)
        if (nk.isEmpty()) return 0f
        if (nt.contains(nk)) return 1.0f
        // 誤字脱字許容: 回答の空白区切りトークンとキーワードの類似度の最大値
        val best = TextNorm.tokens(text).maxOfOrNull { TextNorm.similarity(it, nk) } ?: 0f
        return if (best >= 0.85f) best else 0f
    }

    /** 全必須キーワードが含まれるか */
    fun allContained(text: String, keywords: List<String>): Boolean =
        keywords.all { contains(text, it) }

    /** どのキーワードが欠けているか(採点根拠用) */
    fun missingKeywords(text: String, keywords: List<String>): List<String> =
        keywords.filter { match(text, it) < 0.85f }
}

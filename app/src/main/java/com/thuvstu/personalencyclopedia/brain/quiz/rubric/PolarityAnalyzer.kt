package com.thuvstu.personalencyclopedia.brain.quiz.rubric

/**
 * 否定・極性・否定スコープ解析(新採点システム.txt「特に重要：否定・極性」)。
 *
 * Embeddingやキーワード一致では区別できない
 *   「日本はプレート境界に位置する。」 vs 「日本はプレート境界に位置しない。」
 * を独立した評価軸で判定する。単純な「否定語があるか」ではなく「どの命題に否定が
 * 作用しているか」(否定スコープ)を抽出する。
 *
 * 制約: 形態素解析(Kuromoji等)は導入しないルールベース実装。二重否定・複数節にまたがる
 * 否定・「〜ないわけではない」等の複雑なケースは LLM judge へ defer する。
 */
object PolarityAnalyzer {

    enum class PolarityLabel { POSITIVE, NEGATIVE }

    /** 1つの節に対する極性・スコープ解析結果 */
    data class PolarityResult(
        val clause: String,
        val negated: Boolean,
        /** 否定が作用する命題(否定語を除去し、可能な範囲で肯定形へ復元したテキスト) */
        val scopeText: String,
        /** 検出した否定パターン */
        val pattern: String,
        /** 〜ではなくX の X(対比表現の後半)。無ければ null */
        val alternative: String? = null
    )

    data class PolarityComparison(
        val matched: Boolean,
        val reversed: Boolean,
        val userNegated: Boolean,
        val expectedNegated: Boolean,
        val detail: String
    )

    /** 否定パターン(長い順に照合する) */
    private val NEGATION_PATTERNS = listOf(
        "というわけではない",
        "わけではない",
        "わけがない",
        "とは限らない",
        "とは思えない",
        "とはいえない",
        "ではありません",
        "べきではない",
        "てはならない",
        "する必要はない",
        "ではない",
        "ではなくて",
        "じゃない",
        "必要はない",
        "必要がない",
        "することができない",
        "ではない",
        "ではなく",
        "以外",
        "できない",
        "ません",
        "ない",
        "ぬ",
        "ず"
    )

    /** 否定を含むが「肯定」を意味する表現(照合前にマスクする) */
    private val NON_NEGATIVE_PATTERNS = listOf(
        "ではないか",
        "ではないだろうか",
        "なければならない",
        "なくてはならない",
        "ないと",
        "ないため"
    )

    /** テキストを節(文末・読点)に分割する */
    fun splitClauses(text: String): List<String> =
        text.split(Regex("[。．.!?！？、\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** 節内で否定パターンを検出する。戻り値は (パターン, 位置) */
    fun detectNegation(clause: String): Pair<String, Int>? {
        if (clause.isBlank()) return null
        val masked = NON_NEGATIVE_PATTERNS.fold(clause) { acc, p ->
            acc.replace(p, "□".repeat(p.length))
        }
        for (pattern in NEGATION_PATTERNS.sortedByDescending { it.length }) {
            val idx = masked.indexOf(pattern)
            if (idx >= 0) return pattern to idx
        }
        return null
    }

    /**
     * 否定スコープの肯定形への復元。
     * 「位置しない」→「位置する」(し→する) / 「高くない」→「高い」(く→い)
     * 句点が後ろに残る場合は除去する。
     */
    fun reconstructScope(clause: String, pattern: String, patternIndex: Int): String {
        var before = clause.substring(0, patternIndex).trim()
        if (before.endsWith("し")) before = before.dropLast(1) + "する"
        else if (before.endsWith("く")) before = before.dropLast(1) + "い"
        before = before.trimEnd()
            .trimEnd('と', 'は', 'が', 'を', 'に', 'で', 'の', '、', '，', ' ')
        return before
    }

    /** テキスト全体を解析し、節ごとの極性結果を返す */
    fun analyze(text: String): List<PolarityResult> {
        return splitClauses(text).mapNotNull { clause ->
            val detected = detectNegation(clause) ?: return@mapNotNull null
            val (pattern, idx) = detected
            val scope = reconstructScope(clause, pattern, idx)
            val alternative = if (pattern == "ではなく" || pattern == "ではなくて") {
                clause.substring(idx + pattern.length).trim().takeIf { it.isNotEmpty() }
            } else null
            PolarityResult(
                clause = clause,
                negated = true,
                scopeText = scope,
                pattern = pattern,
                alternative = alternative
            )
        }
    }

    /** テキスト全体の極性(否定節が1つでもあれば NEGATIVE) */
    fun polarityOf(text: String): PolarityLabel =
        if (analyze(text).any { it.negated }) PolarityLabel.NEGATIVE else PolarityLabel.POSITIVE

    /**
     * 模範解答とユーザー回答の極性を比較する。
     * 極性が異なり、かつ否定スコープが模範の命題と意味的に重なる場合を「反転」とみなす
     * (これは矛盾シグナルになる)。
     */
    fun compare(userText: String, expectedText: String): PolarityComparison {
        val userNegated = polarityOf(userText) == PolarityLabel.NEGATIVE
        val expectedNegated = polarityOf(expectedText) == PolarityLabel.NEGATIVE
        if (userNegated == expectedNegated) {
            return PolarityComparison(
                matched = true, reversed = false,
                userNegated = userNegated, expectedNegated = expectedNegated,
                detail = "極性一致(${if (userNegated) "否定" else "肯定"})"
            )
        }
        // 極性が異なる。否定スコープ(肯定復元形)と他方の命題の重なりを見る
        val userProp = negatedScopeOrWhole(userText)
        val expectedProp = negatedScopeOrWhole(expectedText)
        val overlap = TextNorm.bigramOverlap(userProp, expectedProp)
        val reversed = overlap >= 0.7f
        return PolarityComparison(
            matched = false, reversed = reversed,
            userNegated = userNegated, expectedNegated = expectedNegated,
            detail = "極性が逆。重なり率=$overlap(否定スコープ: $userProp)"
        )
    }

    private fun negatedScopeOrWhole(text: String): String {
        val negated = analyze(text).firstOrNull { it.negated }
        return negated?.scopeText?.takeIf { it.isNotBlank() } ?: TextNorm.normalize(text)
    }
}

package com.thuvstu.personalencyclopedia.brain.quiz.rubric

/**
 * 関係の反転検出(新採点システム.txt「関係の反転」)。
 *
 * Embeddingが苦手とする「意味は近いが採点上は全く違う」構造を独立して見る:
 *   A → B   vs  B → A
 *   AよりBが大きい  vs  BよりAが大きい
 *   AがBの原因  vs  BがAの原因
 *   Aの場合  vs  Aでない場合
 *
 * 役割分担(新採点システム.txt):
 *   Embedding = 内容が似ているか
 *   構造解析(本クラス+否定・極性) = その内容を正しく述べているか
 */
object RelationDirectionChecker {

    data class RelationTuple(
        val termA: String,
        val termB: String,
        val relation: String,
        /** +1: termA が termB の原因/先行で、比較では termB が termA より大きい */
        val direction: Int
    )

    data class RelationComparison(
        val matched: Boolean,
        val reversed: Boolean,
        val detail: String,
        val expectedRelations: List<RelationTuple> = emptyList(),
        val userRelations: List<RelationTuple> = emptyList()
    )

    private val COMPARE_ADJ =
        "(大きい|高い|多い|長い|重い|広い|速い|強い|小さい|低い|少ない|短い|軽い|狭い|遅い|弱い|安い|深い|浅い)"

    /** パターンごとに (termA, termB, relation, direction) を抽出する正規表現の一覧 */
    private val PATTERNS = listOf(
        // AよりBが(大きい|...) → 基準=A, 大きい方=B
        Regex("(.+?)より(.+?)(?:が|は)$COMPARE_ADJ"),
        // BはAより(大きい|...) → 基準=group2, 大きい方=group1
        Regex("(.+?)(?:は|が)(.+?)より$COMPARE_ADJ"),
        // AがBの原因 / AがBの原因だ
        Regex("(.+?)(?:が|は)(.+?)の原因(?:だ|である)?"),
        // BはAが原因だ(だ)
        Regex("(.+?)(?:は|が)(.+?)が原因(?:だ|である)?"),
        // AによってBが / AによりBが
        Regex("(.+?)(?:によって|により)(.+?)(?:が|は)"),
        // A→B / A -> B / A ⇒ B
        Regex("(.+?)\\s*[→⇒→>\\-]\\s*(.+?)"),
        // AからBへ / AからBまで
        Regex("(.+?)から(.+?)(?:へ|まで|にかけて)")
    )

    /** テキストから関係タプルを抽出する */
    fun extract(text: String): List<RelationTuple> {
        val result = mutableListOf<RelationTuple>()
        if (text.isBlank()) return result

        PATTERNS[0].findAll(text).forEach { m ->
            result += RelationTuple(m.groupValues[1].trim(), m.groupValues[2].trim(), "comparison:${m.groupValues[3]}", +1)
        }
        PATTERNS[1].findAll(text).forEach { m ->
            // group1 = 大きい方(B), group2 = 基準(A) → 基準をtermAに揃える
            result += RelationTuple(m.groupValues[2].trim(), m.groupValues[1].trim(), "comparison:${m.groupValues[3]}", +1)
        }
        PATTERNS[2].findAll(text).forEach { m ->
            result += RelationTuple(m.groupValues[1].trim(), m.groupValues[2].trim(), "cause", +1)
        }
        PATTERNS[3].findAll(text).forEach { m ->
            result += RelationTuple(m.groupValues[2].trim(), m.groupValues[1].trim(), "cause", +1)
        }
        PATTERNS[4].findAll(text).forEach { m ->
            result += RelationTuple(m.groupValues[1].trim(), m.groupValues[2].trim(), "cause", +1)
        }
        PATTERNS[5].findAll(text).forEach { m ->
            result += RelationTuple(m.groupValues[1].trim(), m.groupValues[2].trim(), "sequential", +1)
        }
        PATTERNS[6].findAll(text).forEach { m ->
            result += RelationTuple(m.groupValues[1].trim(), m.groupValues[2].trim(), "sequential", +1)
        }
        return result.distinct()
    }

    private fun sameTerm(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return TextNorm.bigramOverlap(a, b) >= 0.6f || a == b
    }

    /**
     * 模範解答とユーザー回答の関係の向きを比較する。
     * 同じ引数で同じ関係の向きが反転していれば reversed=true。
     */
    fun compare(userText: String, expectedText: String): RelationComparison {
        val expected = extract(expectedText)
        val user = extract(userText)
        if (expected.isEmpty()) {
            return RelationComparison(matched = true, reversed = false, detail = "模範に構造関係なし")
        }
        var anyMatched = false
        for (et in expected) {
            val sameDirection = user.firstOrNull {
                it.relation == et.relation &&
                    sameTerm(it.termA, et.termA) && sameTerm(it.termB, et.termB)
            }
            val reversed = user.firstOrNull {
                it.relation == et.relation &&
                    sameTerm(it.termA, et.termB) && sameTerm(it.termB, et.termA)
            }
            if (reversed != null) {
                return RelationComparison(
                    matched = false, reversed = true,
                    detail = "関係が反転: 模範「${et.termA}→${et.termB}」に対し回答「${reversed.termA}→${reversed.termB}」",
                    expectedRelations = expected, userRelations = user
                )
            }
            if (sameDirection != null) anyMatched = true
        }
        return RelationComparison(
            matched = anyMatched, reversed = false,
            detail = if (anyMatched) "関係の向き一致" else "模範の構造関係が見つからない(判定不能扱い)",
            expectedRelations = expected, userRelations = user
        )
    }
}

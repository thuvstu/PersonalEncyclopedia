package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ルーブリック分解(新採点システム.txt「Rubric分解」)。
 * gradingContextJson を採点項目(RubricItem)へ分解する。未設定/空の場合は模範解答から
 * 自動分解(autoDecompose)する。
 *
 * gradingContextJson 契約:
 * {
 *   "rubric": [
 *     {"kind":"keyword","label":"必須語","expected":"プレート境界","weight":0.5},
 *     {"kind":"numeric_unit","label":"単位変換","expected":"72 km/h","weight":0.3},
 *     {"kind":"polarity","label":"肯定/否定","expected":"POSITIVE","weight":0.2}
 *   ],
 *   "modelAnswers": ["模範解答1", "模範解答2"]
 * }
 */
object RubricParser {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class RubricItemJson(
        val kind: String,
        val label: String = "",
        val expected: String,
        val weight: Float = 1.0f,
        val optional: Boolean = false
    )

    @Serializable
    data class RubricJson(
        val rubric: List<RubricItemJson> = emptyList(),
        val modelAnswers: List<String> = emptyList()
    )

    data class RubricBundle(
        val items: List<RubricItem>,
        val modelAnswers: List<String>
    )

    private val NUMBER_RE = Regex("\\d")

    /** kind 文字列 → RubricKind(日本語ラベル・表記ゆれ対応) */
    fun mapKind(raw: String): RubricKind? {
        val k = raw.trim().lowercase()
        return when {
            k in listOf("keyword", "キーワード", "必須語", "用語") -> RubricKind.KEYWORD
            k in listOf("concept", "概念") -> RubricKind.CONCEPT
            k in listOf("numeric", "numeric_unit", "number", "数値", "数値・単位", "単位", "式") -> RubricKind.NUMERIC_UNIT
            k in listOf("relation", "因果", "関係", "比較") -> RubricKind.RELATION
            k in listOf("polarity", "否定", "極性", "肯定") -> RubricKind.POLARITY
            k in listOf("explanation", "説明", "説明文") -> RubricKind.EXPLANATION
            else -> null
        }
    }

    /**
     * ★最適化R5: 生成器が出力する gradingContextJson のビルダー。
     * gradingContextJson 契約(上記doc)に沿って採点項目と模範解答をシリアライズする。
     */
    fun buildGradingContextJson(
        items: List<RubricItemJson>,
        modelAnswers: List<String>
    ): String = json.encodeToString(RubricJson(items, modelAnswers))

    /** gradingContextJson を解析し、RubricBundle を返す。空/不正時は自動分解にフォールバック */
    fun parse(gradingContextJson: String?, modelAnswer: String): RubricBundle {
        if (gradingContextJson.isNullOrBlank()) {
            return autoDecompose(modelAnswer)
        }
        val parsed = try {
            json.decodeFromString<RubricJson>(gradingContextJson)
        } catch (_: Exception) {
            return autoDecompose(modelAnswer)
        }
        val items = parsed.rubric.mapNotNull { r ->
            val kind = mapKind(r.kind) ?: return@mapNotNull null
            RubricItem(
                id = "rubric-${r.label.ifBlank { r.expected }}",
                kind = kind,
                label = r.label.ifBlank { r.expected },
                expected = r.expected,
                weight = r.weight.coerceIn(0.01f, 10f),
                optional = r.optional
            )
        }
        return if (items.isEmpty()) {
            autoDecompose(modelAnswer)
        } else {
            RubricBundle(items, parsed.modelAnswers.ifEmpty { listOf(modelAnswer) })
        }
    }

    /**
     * 模範解答からの自動ルーブリック分解。
     * - 否定を含む節 → POLARITY(極性) + 否定スコープを CONCEPT に
     * - 数値を含む節 → NUMERIC_UNIT を追加
     * - その他 → 節全体を CONCEPT として重み均等配分
     */
    fun autoDecompose(modelAnswer: String): RubricBundle {
        val clauses = PolarityAnalyzer.splitClauses(modelAnswer)
        if (clauses.isEmpty()) {
            return RubricBundle(
                listOf(RubricItem("auto-1", RubricKind.CONCEPT, "概念", modelAnswer, 1.0f)),
                listOf(modelAnswer)
            )
        }
        val weight = 1.0f / clauses.size
        val items = mutableListOf<RubricItem>()
        var idx = 1
        for (clause in clauses) {
            val negated = PolarityAnalyzer.analyze(clause).firstOrNull { it.negated }
            if (negated != null) {
                val polarityLabel =
                    if (PolarityAnalyzer.polarityOf(clause) == PolarityAnalyzer.PolarityLabel.NEGATIVE) "NEGATIVE" else "POSITIVE"
                items += RubricItem("auto-${idx++}", RubricKind.POLARITY, "極性", polarityLabel, weight)
                if (negated.scopeText.isNotBlank()) {
                    items += RubricItem("auto-${idx++}", RubricKind.CONCEPT, "概念(否定スコープ)", negated.scopeText, weight)
                }
            } else {
                items += RubricItem("auto-${idx++}", RubricKind.CONCEPT, "概念", clause, weight)
            }
            if (NUMBER_RE.containsMatchIn(clause)) {
                items += RubricItem("auto-${idx++}", RubricKind.NUMERIC_UNIT, "数値・単位", clause, weight)
            }
        }
        return RubricBundle(items, listOf(modelAnswer))
    }
}

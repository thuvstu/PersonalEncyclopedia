package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 新採点システム(Rubric-based grading)のデータモデル。
 * docs/新採点システム.txt の「Rubric分解 → feature抽出 → 最終LLM judge」パイプラインを実体化する。
 *
 * 設計方針:
 * - RubricKindごとに「何を見るか」が異なるため、判定軸を列挙型で明示する。
 * - Signal は判定の根拠(採点根拠)を構造化したもの。UI表示・LLM judgeのevidence・将来のDB永続化に使う。
 * - score(0..1)は達成度、confidence(0..1)は判定の確信度を分離する(低confidenceはLLM judgeへdefer)。
 */
enum class RubricKind {
    /** 明示的な語の一致(必須語・専門用語) */
    KEYWORD,

    /** 概念の存在(キーワードより広い、Embedding/NLIで判定) */
    CONCEPT,

    /** 数値・単位・式の決定論的検証(72km/h = 20m/s 等) */
    NUMERIC_UNIT,

    /** 関係・因果・比較の向き(A→B vs B→A、AよりBが大きい 等) */
    RELATION,

    /** 否定・極性・否定スコープ(独立した評価軸・最重要) */
    POLARITY,

    /** 必要な説明の質・量 */
    EXPLANATION
}

/**
 * 採点ルーブリックの1項目。
 * `expected` は項目種別で意味が変わる:
 * - KEYWORD: 期待する語句
 * - NUMERIC_UNIT: 「20 m/s」「72 km/h」「1600年」等の値+単位表記
 * - POLARITY: "POSITIVE" / "NEGATIVE"
 * - RELATION: 「AはBの原因」「A>B」等の関係式
 * - CONCEPT/EXPLANATION: 期待する命題・説明文
 */
data class RubricItem(
    val id: String,
    val kind: RubricKind,
    val label: String,
    val expected: String,
    val weight: Float = 1.0f,
    val optional: Boolean = false
)

/** 判定の根拠シグナル。1つのrubric itemに対し複数付与される。 */
sealed class Signal {
    data class KeywordHit(val keyword: String) : Signal()
    data class KeywordNearMiss(val keyword: String, val similarity: Float) : Signal()
    data class KeywordMissing(val keyword: String) : Signal()

    data class ConceptDetected(val concept: String) : Signal()
    data class ConceptMissing(val concept: String) : Signal()

    data class SemanticSimilar(val similarity: Float) : Signal()

    data class PolarityMatched(val scope: String) : Signal()
    data class PolarityReversed(val scope: String) : Signal()

    data class NumericEquivalent(val expected: String, val userValue: String, val converted: String) : Signal()
    data class NumericMismatch(val expected: String, val userValue: String) : Signal()

    data class RelationMatched(val relation: String) : Signal()
    data class RelationReversed(val expected: String, val user: String) : Signal()

    /** 極性反転 + キーワード一致などから検出する矛盾シグナル(本格的NLIは将来IEntailmentProviderで) */
    data class ContradictionDetected(val detail: String) : Signal()

    /** 判定不能(モデル未設定・データ不足等)。誤答と判定不能は混ぜない */
    object Undeterminable : Signal()
}

/** 1つのrubric itemに対する評価結果 */
data class RubricItemFeature(
    val item: RubricItem,
    val score: Float,
    val confidence: Float,
    val signals: List<Signal> = emptyList(),
    val notes: String = ""
)

/** 全体の評価証跡。最終LLM judgeへ渡す構造化evidenceの元 */
data class RubricEvidence(
    val rubricItems: List<RubricItemFeature>,
    val overallConfidence: Float,
    val deferToLlm: Boolean,
    val providerName: String,
    val entailmentUsed: Boolean = false,
    val crossEncoderUsed: Boolean = false
)

/** 最終判定結果 */
data class RubricGradeResult(
    val isCorrect: Boolean,
    val score: Float,
    val method: String = "rubric",
    val rationale: String,
    val evidence: RubricEvidence,
    val judgeSource: String     // "llm" | "heuristic"
)

/** evidenceのLLM judge用JSON表現(新採点システム.txtの構造化evidenceに準拠) */
fun RubricEvidence.toJudgeJson(): JsonObject = buildJsonObject {
    put("overallConfidence", overallConfidence)
    put("deferToLlm", deferToLlm)
    put("provider", providerName)
    put("rubric", buildJsonArray {
        rubricItems.forEach { f ->
            add(buildJsonObject {
                put("label", f.item.label)
                put("kind", f.item.kind.name)
                put("expected", f.item.expected)
                put("score", f.score)
                put("confidence", f.confidence)
                put("signals", buildSignalJson(f.signals))
            })
        }
    })
}

private fun buildSignalJson(signals: List<Signal>): JsonArray = buildJsonArray {
    signals.forEach { s ->
        when (s) {
            is Signal.KeywordHit -> add(signalsObj("keyword_hit", mapOf("keyword" to s.keyword)))
            is Signal.KeywordNearMiss -> add(signalsObj("keyword_near_miss", mapOf("keyword" to s.keyword, "similarity" to s.similarity)))
            is Signal.KeywordMissing -> add(signalsObj("keyword_missing", mapOf("keyword" to s.keyword)))
            is Signal.ConceptDetected -> add(signalsObj("concept_detected", mapOf("concept" to s.concept)))
            is Signal.ConceptMissing -> add(signalsObj("concept_missing", mapOf("concept" to s.concept)))
            is Signal.SemanticSimilar -> add(signalsObj("semantic_similar", mapOf("similarity" to s.similarity)))
            is Signal.PolarityMatched -> add(signalsObj("polarity_matched", mapOf("scope" to s.scope)))
            is Signal.PolarityReversed -> add(signalsObj("polarity_reversed", mapOf("scope" to s.scope)))
            is Signal.NumericEquivalent -> add(signalsObj("numeric_equivalent", mapOf("expected" to s.expected, "userValue" to s.userValue, "converted" to s.converted)))
            is Signal.NumericMismatch -> add(signalsObj("numeric_mismatch", mapOf("expected" to s.expected, "userValue" to s.userValue)))
            is Signal.RelationMatched -> add(signalsObj("relation_matched", mapOf("relation" to s.relation)))
            is Signal.RelationReversed -> add(signalsObj("relation_reversed", mapOf("expected" to s.expected, "user" to s.user)))
            is Signal.ContradictionDetected -> add(signalsObj("contradiction_detected", mapOf("detail" to s.detail)))
            Signal.Undeterminable -> add(signalsObj("undeterminable", emptyMap()))
        }
    }
}

private fun signalsObj(type: String, fields: Map<String, Any>): JsonObject = buildJsonObject {
    put("type", type)
    fields.forEach { (k, v) ->
        when (v) {
            is String -> put(k, v)
            is Float -> put(k, v)
            is Int -> put(k, v)
            is Boolean -> put(k, v)
        }
    }
}

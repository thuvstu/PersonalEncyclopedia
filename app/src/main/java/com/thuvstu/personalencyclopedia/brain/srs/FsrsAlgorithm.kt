// app/src/main/java/com/thuvstu/personalencyclopedia/brain/srs/FsrsAlgorithm.kt
package com.thuvstu.personalencyclopedia.brain.srs

import com.thuvstu.personalencyclopedia.db.entity.SrsReviewEntity
import java.util.UUID
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS-4.5 (Free Spaced Repetition Scheduler) のKotlin移植。
 * open-spaced-repetition/fsrs4anki のアルゴリズムを参照。
 *
 * §8.6 設計方針:
 * - 初期安定性 3.0日 (Good) / 目標保持率 90%
 * - srs_review テーブルは不変（§5.5.5）。
 *   easeFactor = difficulty (1..10) として流用、
 *   intervalDays = stability として格納。
 *   ※ R=0.9 のとき I(0.9,S)=S が数学的に厳密に成立するため、
 *     intervalDays を stability として扱っても誤差ゼロ。
 *
 * 状態: (difficulty D ∈ [1,10], stability S > 0)
 * FSRS grade: 1=Again, 2=Hard, 3=Good, 4=Easy
 */
object FsrsAlgorithm {

    // FSRS-4.5 数学定数
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0          // = 0.9^(1/DECAY) - 1

    const val REQUESTED_RETENTION = 0.9             // §8.6 保持率90%
    private const val MAX_INTERVAL = 36500          // 100年
    private const val MIN_DIFFICULTY = 1.0
    private const val MAX_DIFFICULTY = 10.0

    /**
     * FSRS-4.5 デフォルト重み w[0..16]
     * w[0..3]  初期安定性 (Again/Hard/Good/Easy)  ← w[2]=3.0 は §8.6 反映
     * w[4..5]  初期難易度
     * w[6]     難易度更新勾配
     * w[7]     mean reversion 重み
     * w[8..10] 成功時の安定性増加（w[8]≒成長率）
     * w[11..14]失敗時の安定性
     * w[15]    hard penalty / w[16] easy bonus
     */
    private val W = doubleArrayOf(
        0.4, 0.6, 3.0, 5.8,
        4.93, 0.94, 0.86, 0.01,
        1.49, 0.14, 0.94,
        2.18, 0.05, 0.34, 1.26,
        0.29, 2.61
    )

    data class FsrsResult(
        val intervalDays: Int,
        val difficulty: Float,
        val stability: Float,
        val nextReviewAt: Long
    )

    // ── 核心関数 ────────────────────────────────────────────

    /** 初期難易度 D0(G) = w4 - e^(w5*(G-1)) + 1, clamped [1,10] */
    private fun initDifficulty(grade: Int): Double {
        val d = W[4] - exp(W[5] * (grade - 1)) + 1.0
        return clampDifficulty(d)
    }

    /** 難易度更新（mean reversion 付き） */
    private fun nextDifficulty(d: Double, grade: Int): Double {
        val dNew = d - W[6] * (grade - 3)
        val d0 = initDifficulty(4)
        return clampDifficulty(W[7] * d0 + (1.0 - W[7]) * dNew)
    }

    private fun clampDifficulty(d: Double): Double =
        max(MIN_DIFFICULTY, min(MAX_DIFFICULTY, d))

    /** 保持率 R(t, S) = (1 + FACTOR * t / S)^DECAY */
    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0) return 0.0
        return (1.0 + FACTOR * elapsedDays / stability).pow(DECAY)
    }

    /** 成功時安定性 S'_r */
    private fun nextStabilitySuccess(d: Double, s: Double, r: Double, grade: Int): Double {
        val hardPenalty = if (grade == 2) W[15] else 1.0
        val easyBonus = if (grade == 4) W[16] else 1.0
        val inner = exp(W[8]) *
                (11.0 - d) *
                s.pow(-W[9]) *
                (exp(W[10] * (1.0 - r)) - 1.0) *
                hardPenalty *
                easyBonus
        return s * (inner + 1.0)
    }

    /** 失敗時安定性 S'_f（必ず元より小さくなる） */
    private fun nextStabilityFail(d: Double, s: Double, r: Double): Double {
        val sNew = W[11] *
                d.pow(-W[12]) *
                ((s + 1.0).pow(W[13]) - 1.0) *
                exp(W[14] * (1.0 - r))
        return min(sNew, s)
    }

    /** 目標保持率 r からインターバルを計算 I(r, S) */
    private fun intervalFromStability(stability: Double): Int {
        val interval = (stability / FACTOR) * (REQUESTED_RETENTION.pow(1.0 / DECAY) - 1.0)
        return max(1, min(interval.roundToInt(), MAX_INTERVAL))
    }

    // ── メイン計算 ──────────────────────────────────────────

    /**
     * @param grade FSRS grade: 1=Again, 2=Hard, 3=Good, 4=Easy
     * @param elapsedDays 前回レビューからの経過日数
     * @param previousDifficulty 前回 difficulty（初回 null）
     * @param previousStability 前回 stability（初回 null）
     */
    fun calculate(
        grade: Int,
        elapsedDays: Double,
        previousDifficulty: Double?,
        previousStability: Double?
    ): FsrsResult {
        val g = grade.coerceIn(1, 4)

        val newDifficulty: Double
        val newStability: Double

        if (previousDifficulty == null || previousStability == null || previousStability <= 0) {
            // 初回学習
            newDifficulty = initDifficulty(g)
            newStability = W[g - 1]
        } else {
            val r = retrievability(elapsedDays, previousStability)
            newDifficulty = nextDifficulty(previousDifficulty, g)
            newStability = if (g == 1) {
                nextStabilityFail(previousDifficulty, previousStability, r)
            } else {
                nextStabilitySuccess(previousDifficulty, previousStability, r, g)
            }
        }

        val intervalDays = intervalFromStability(newStability)
        val nextReviewAt = if (g == 1) {
            System.currentTimeMillis() + 10 * 60 * 1000L   // Again は10分後
        } else {
            System.currentTimeMillis() + intervalDays * 24L * 60 * 60 * 1000
        }

        return FsrsResult(
            intervalDays = intervalDays,
            difficulty = newDifficulty.toFloat(),
            stability = newStability.toFloat(),
            nextReviewAt = nextReviewAt
        )
    }

    /**
     * SM-2 grade (0-5) を FSRS grade (1-4) に変換。
     * 既存UI（SrsReviewScreen）が SM-2 基準で grade を渡すための橋渡し。
     */
    fun sm2GradeToFsrs(sm2Grade: Int): Int = when {
        sm2Grade <= 1 -> 1   // Again
        sm2Grade <= 3 -> 2   // Hard
        sm2Grade == 4 -> 3   // Good
        else -> 4            // Easy
    }

    /**
     * SrsReviewEntity 生成。
     * grade は履歴互換のため SM-2 基準のまま保存。
     * easeFactor = difficulty, intervalDays = stability として格納。
     */
    fun createReview(
        entryId: String,
        sm2Grade: Int,
        elapsedDays: Double,
        previousDifficulty: Float?,
        previousStability: Float?
    ): SrsReviewEntity {
        val g = sm2GradeToFsrs(sm2Grade)
        val result = calculate(
            grade = g,
            elapsedDays = elapsedDays,
            previousDifficulty = previousDifficulty?.toDouble(),
            previousStability = previousStability?.toDouble()
        )
        return SrsReviewEntity(
            id = UUID.randomUUID().toString(),
            entryId = entryId,
            grade = sm2Grade,
            intervalDays = result.intervalDays,
            easeFactor = result.difficulty,
            nextReviewAt = result.nextReviewAt
        )
    }
}
package com.thuvstu.personalencyclopedia.brain.srs

import com.thuvstu.personalencyclopedia.db.entity.SrsReviewEntity
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * SM-2 Algorithm implementation.
 * Grade: 0-5
 *   0-1: complete blackout / wrong → reset
 *   2:   wrong but upon seeing answer, remembered → reduced interval
 *   3:   correct with serious difficulty
 *   4:   correct with some hesitation
 *   5:   perfect response
 */
object Sm2Algorithm {

    data class Sm2Result(
        val intervalDays: Int,
        val easeFactor: Float,
        val nextReviewAt: Long
    )

    fun calculate(
        grade: Int,
        previousInterval: Int = 0,
        previousEase: Float = 2.5f,
        repetitionCount: Int = 0
    ): Sm2Result {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        // Update ease factor
        val newEase = max(
            1.3f,
            previousEase + (0.1f - (5 - grade) * (0.08f + (5 - grade) * 0.02f))
        )

        val intervalDays: Int = when {
            grade < 2 -> 0  // Reset: review again today (or in 10 min for immediate retry)
            grade == 2 -> max(1, (previousInterval * 0.5f).roundToInt())
            else -> when (repetitionCount) {
                0 -> 1
                1 -> 6
                else -> (previousInterval * newEase).roundToInt()
            }
        }

        val nextReviewAt = if (grade < 2) {
            now + 10 * 60 * 1000L  // 10 minutes for failed items
        } else {
            now + intervalDays * dayMs
        }

        return Sm2Result(
            intervalDays = intervalDays,
            easeFactor = newEase,
            nextReviewAt = nextReviewAt
        )
    }

    fun createReview(
        entryId: String,
        grade: Int,
        previousInterval: Int = 0,
        previousEase: Float = 2.5f,
        repetitionCount: Int = 0,
        // §5.8.5 (v8): このレビュー時点の累積成功反復回数。成功(grade>=2)なら前回+1、失敗なら0
        recordedRepetitionCount: Int = if (grade < 2) 0 else repetitionCount + 1
    ): SrsReviewEntity {
        val result = calculate(grade, previousInterval, previousEase, repetitionCount)
        return SrsReviewEntity(
            id = UUID.randomUUID().toString(),
            entryId = entryId,
            grade = grade,
            intervalDays = result.intervalDays,
            easeFactor = result.easeFactor,
            nextReviewAt = result.nextReviewAt,
            repetitionCount = recordedRepetitionCount
        )
    }
}
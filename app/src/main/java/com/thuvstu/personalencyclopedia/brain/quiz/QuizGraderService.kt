package com.thuvstu.personalencyclopedia.brain.quiz

import com.thuvstu.personalencyclopedia.brain.quiz.rubric.RubricGrader
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.toJudgeJson
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ★最適化R6: 採点パイプラインの共通実装。
 * アプリ(QuizRepository)とKtorサーバー(QuizRoutes)の採点を同一ロジックに統一する。
 *
 * パイプライン: 多段採点 → ルーブリック採点(試作) → 意味的採点(§8.4第6段階) → スコア計算
 * (ヒント減点・速度ボーナス)。
 */
@Singleton
class QuizGraderService @Inject constructor(
    private val multiStageGrader: MultiStageGrader,
    private val rubricGrader: RubricGrader,
    private val semanticGrader: SemanticGrader
) {

    data class GradedResult(
        val isCorrect: Boolean?,
        val score: Float,
        val method: String,
        val rubricRationale: String? = null,
        val rubricEvidenceJson: String? = null,
        val rubricUsed: Boolean = false
    )

    suspend fun grade(
        quiz: QuizBankEntity,
        userAnswer: String,
        hintsRevealed: Int = 0,
        answeredWithinMs: Long? = null,   // §8.7.3: 設問表示〜回答までの経過時間
        hintPenalty: Float = 0.3f         // ヒント減点率
    ): GradedResult {
        var gradeResult = multiStageGrader.grade(userAnswer, quiz.answer)

        // ★新採点システム(試作): 記述式はルーブリック採点を適用し、採点根拠を記録する。
        // rubricが正解と判定した場合のみ正解に昇格する(safeな試作統合)。
        var rubricRationale: String? = null
        var rubricEvidenceJson: String? = null
        var rubricUsed = false
        if (rubricGrader.applicable(quiz.quizType, userAnswer)) {
            val rubric = rubricGrader.grade(quiz.question, userAnswer, quiz.answer, quiz.gradingContextJson)
            rubricUsed = true
            rubricRationale = rubric.rationale
            rubricEvidenceJson = rubric.evidence.toJudgeJson().toString()
            if (rubric.isCorrect) {
                gradeResult = MultiStageGrader.GradeResult(
                    isCorrect = true,
                    score = rubric.score.coerceAtLeast(gradeResult.score),
                    method = "rubric"
                )
            }
        }

        // ★G: 記述式で通常採点が不正解かつAPI利用可能な場合、意味的採点に昇格（§8.4第6段階）
        if (!gradeResult.isCorrect &&
            userAnswer != "__UNLEARNED__" &&
            userAnswer.length >= 5 &&
            quiz.quizType in listOf("qa", "essay")
        ) {
            semanticGrader.grade(userAnswer, quiz.answer)?.let { sem ->
                if (sem.isCorrect) gradeResult = sem
            }
        }

        val baseScore = when {
            gradeResult.isCorrect -> maxOf(0f, 1.0f - hintPenalty * hintsRevealed)
            userAnswer == "__UNLEARNED__" -> 0f
            else -> -1.0f
        }
        // §8.7.3 (Kahoot由来): 正解かつ速いほど高得点。10秒未満で最大+50%のボーナス。
        val speedBonus = if (gradeResult.isCorrect && answeredWithinMs != null) {
            (1.0f - answeredWithinMs.coerceAtMost(10_000L) / 10_000f)
                .coerceIn(0f, 1f) * 0.5f
        } else 0f
        val score = baseScore + speedBonus

        return GradedResult(
            isCorrect = if (userAnswer == "__UNLEARNED__") null else gradeResult.isCorrect,
            score = score,
            method = gradeResult.method,
            rubricRationale = rubricRationale,
            rubricEvidenceJson = rubricEvidenceJson,
            rubricUsed = rubricUsed
        )
    }
}

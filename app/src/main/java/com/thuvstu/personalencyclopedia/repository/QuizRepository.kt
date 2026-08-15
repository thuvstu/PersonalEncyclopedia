package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.brain.quiz.LlmQuizGenerator
import com.thuvstu.personalencyclopedia.brain.quiz.MultiStageGrader
import com.thuvstu.personalencyclopedia.brain.quiz.RuleBasedQuizGenerator
import com.thuvstu.personalencyclopedia.brain.quiz.SemanticGrader
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.RubricGrader
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.toJudgeJson
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.dao.TopicDao
import com.thuvstu.personalencyclopedia.db.entity.QuizAttemptEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizRepository @Inject constructor(
    private val quizDao: QuizDao,
    private val definitionDao: EntryDefinitionDao,
    private val topicDao: TopicDao,
    private val entryDao: EntryDao,                    // ★追加
    private val llmQuizGenerator: LlmQuizGenerator,    // ★追加
    private val semanticGrader: SemanticGrader,        // ★追加（G）
    private val geminiClient: GeminiClient,             // ★追加
    private val multiStageGrader: MultiStageGrader,      // ★追加（E: era_master参照）
    private val rubricGrader: RubricGrader               // ★新採点システム(試作)
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateQuizzesFromDefinitions(topicId: String? = null): Int {
        val allDefs = definitionDao.search("", limit = 500).first()
        if (allDefs.isEmpty()) return 0
        val quizzes = RuleBasedQuizGenerator.generateBatch(allDefs, topicId)
        val newQuizzes = quizzes.filter { quizDao.countByQuestion(it.question) == 0 }
        quizDao.insertQuizzes(newQuizzes)
        return newQuizzes.size
    }

    /**
     * ★C: 特定エントリーからのクイズ生成。
     * definition → ルールベース4種（コスト0・§8.3第1段階）
     * その他    → LLM（Gemini設定時のみ・§8.3第4段階）
     */
    suspend fun generateFromEntry(entryId: String): Int {
        definitionDao.getByEntryId(entryId)?.let { def ->
            val allDefs = definitionDao.search("", limit = 500).first()
            val distractors = allDefs.filter { it.field == def.field && it.entryId != def.entryId }
            val quizzes = mutableListOf(
                RuleBasedQuizGenerator.generateQaFromDefinition(def, null),
                RuleBasedQuizGenerator.generateReverseQa(def, null)
            )
            if (distractors.size >= 3) {
                quizzes.add(RuleBasedQuizGenerator.generateMcq(def, distractors, null))
            }
            RuleBasedQuizGenerator.generateFillBlank(def, null)?.let { quizzes.add(it) }
            val newQuizzes = quizzes.filter { quizDao.countByQuestion(it.question) == 0 }
            quizDao.insertQuizzes(newQuizzes)
            return newQuizzes.size
        }
        if (geminiClient.isConfigured()) {
            val entry = entryDao.getById(entryId) ?: return 0
            return llmQuizGenerator.generateFromEntry(entry, count = 3)
        }
        return 0
    }

    /**
     * ★最適化R1: 出題プールを排他分類で構成する（topicId/難易度/形式プール対応）。
     * - wrong: 不正解履歴あり・正解履歴なし（苦手）
     * - new:   一度も回答していない（未習）
     * - random: 正解済みを除くアクティブ全件（残りを補填）
     * 分類間の重複・正解済みの混入を排除し、セッション内は distinctBy で重複しない。
     */
    suspend fun getNextQuizzes(
        topicId: String? = null,
        limit: Int = 10,
        difficultyMin: Int? = null,
        types: List<String> = listOf("qa", "mcq", "fill_blank")
    ): List<QuizBankEntity> {
        val slice = (limit / 3).coerceAtLeast(1)
        val wrong = quizDao.getWrongUnmasteredQuizzes(topicId, difficultyMin, slice)
        val unlearned = quizDao.getNeverAttemptedQuizzes(topicId, difficultyMin, slice)
        val random = quizDao.getRandomUnmasteredQuizzes(topicId, types, difficultyMin, limit)
        return (wrong + unlearned + random).distinctBy { it.id }.take(limit)
    }

    // §8.7.2 プレッシャーテスト(全列挙型)の出題セット
    data class EnumerateChallenge(
        val field: String,
        val answers: List<String>
    )

    /** 同一分野のentry群から正解集合を動的生成する。対象が少なすぎる分野はスキップ。 */
    suspend fun buildEnumerateChallenge(): EnumerateChallenge? {
        val fields = definitionDao.getDistinctFields()
        for (field in fields) {
            val defs = definitionDao.getByField(field)
            val terms = defs.mapNotNull { it.term.trim().takeIf { t -> t.isNotBlank() } }
                .distinct().take(12)
            if (terms.size >= 3) return EnumerateChallenge(field, terms)
        }
        return null
    }

    /** §8.7.2: 回答文字列が正解集合に含まれるかを正規化比較で判定（マッチ済みは除外）。 */
    fun matchEnumerateAnswer(
        answer: String,
        correctSet: List<String>,
        matched: List<String>
    ): String? {
        val norm = answer.trim().replace(Regex("\\s+"), "")
        if (norm.isBlank()) return null
        val already = matched.map { it.replace(Regex("\\s+"), "") }.toSet()
        return correctSet.firstOrNull { c ->
            val cNorm = c.replace(Regex("\\s+"), "")
            cNorm.isNotEmpty() && cNorm !in already && cNorm == norm
        }
    }

    /**
     * 採点結果(試作)に rubric の採点根拠を添えて返す。
     * 既存呼び出し(QuizViewModel)は attempt を、新採点システムは rationale/evidence を使う。
     */
    data class QuizGradingResult(
        val attempt: QuizAttemptEntity,
        val rubricRationale: String? = null,
        val rubricEvidenceJson: String? = null,
        val rubricUsed: Boolean = false
    )

    suspend fun gradeAndRecord(
        quiz: QuizBankEntity,
        userAnswer: String,
        hintsRevealed: Int = 0,
        answeredWithinMs: Long? = null,   // §8.7.3 (v8): 設問表示〜回答までの経過時間
        hintPenalty: Float = 0.3f         // ★最適化R2: ヒント減点率（設定で調整可能）
    ): QuizGradingResult {
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
        val attempt = QuizAttemptEntity(
            id = UUID.randomUUID().toString(),
            quizId = quiz.id,
            userAnswer = userAnswer,
            isCorrect = if (userAnswer == "__UNLEARNED__") null else gradeResult.isCorrect,
            score = score,
            gradingMethod = gradeResult.method,
            hintsRevealed = hintsRevealed,
            answeredWithinMs = answeredWithinMs
        )
        quizDao.insertAttempt(attempt)
        return QuizGradingResult(
            attempt = attempt,
            rubricRationale = rubricRationale,
            rubricEvidenceJson = rubricEvidenceJson,
            rubricUsed = rubricUsed
        )
    }

    fun parseChoices(choicesJson: String): List<String> = try {
        json.parseToJsonElement(choicesJson).jsonArray.map { it.jsonPrimitive.content }
    } catch (_: Exception) { emptyList() }

    fun parseHints(hintsJson: String): List<String> = try {
        json.parseToJsonElement(hintsJson).jsonArray.map { it.jsonPrimitive.content }
    } catch (_: Exception) { emptyList() }

    fun observeQuizCount(): Flow<Int> = quizDao.observeQuizCount()

    fun observeAttemptsToday(): Flow<Int> = quizDao.observeAttemptsTodayCount(getStartOfDay())
    fun observeCorrectToday(): Flow<Int> = quizDao.observeCorrectTodayCount(getStartOfDay())

    private fun getStartOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
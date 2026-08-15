package com.thuvstu.personalencyclopedia.brain.quiz

import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.RubricParser
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.RubricParser.RubricItemJson
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLMによるクイズ一括生成エンジン (§8.3, §12.4).
 * テキスト・既存entryからGemini APIでクイズを生成し、quiz_bankへ登録する。
 */
@Singleton
class LlmQuizGenerator @Inject constructor(
    private val geminiClient: GeminiClient,
    private val quizDao: QuizDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class GeneratedQuiz(
        val question: String,
        val quizType: String = "mcq", // qa/mcq/fill_blank/sort/essay/cloze
        val choices: List<String> = emptyList(),
        val answer: String,
        val hints: List<String> = emptyList(),
        val explanation: String = ""
    )

    @Serializable
    data class GeneratedQuizContainer(
        val quizzes: List<GeneratedQuiz>
    )

    suspend fun generateFromEntry(entry: EntryEntity, count: Int = 3): Int {
        if (!geminiClient.isConfigured()) return 0

        val content = entry.content ?: entry.summary ?: entry.title
        val prompt = """
            以下の文章から、重要な概念・知識を問う選択式クイズを${count}問作成し、JSONフォーマットのみで出力してください。

            【文章】
            タイトル: ${entry.title}
            内容: $content

            【出力JSON構造】
            {
              "quizzes": [
                {
                  "question": "問題文",
                  "quizType": "mcq",
                  "choices": ["選択肢1", "選択肢2", "選択肢3", "選択肢4"],
                  "answer": "正解の文字列",
                  "hints": ["ヒント1"],
                  "explanation": "解説文"
                }
              ]
            }
        """.trimIndent()

        val jsonStr = geminiClient.generate(prompt, jsonMode = true) ?: return 0

        return try {
            val container = json.decodeFromString<GeneratedQuizContainer>(jsonStr)
            var inserted = 0
            container.quizzes.forEach { q ->
                // ★追加: 重複チェック
                if (quizDao.countByQuestion(q.question) > 0) return@forEach
                val choicesJson = "[" + q.choices.joinToString(",") { "\"$it\"" } + "]"
                val hintsJson = "[" + q.hints.joinToString(",") { "\"$it\"" } + "]"
                quizDao.insertQuiz(
                    QuizBankEntity(
                        sourceEntryId = entry.id,
                        quizType = q.quizType,
                        question = q.question,
                        choicesJson = choicesJson,
                        answer = q.answer,
                        // ★最適化R5: 記述式は採点用ルーブリックコンテキストを書き出し、ルーブリック採点が使えるようにする
                        gradingContextJson = if (q.quizType in listOf("qa", "essay")) {
                            RubricParser.buildGradingContextJson(
                                items = listOf(
                                    RubricItemJson(kind = "keyword", label = "必須キーワード", expected = q.answer, weight = 1.0f)
                                ),
                                modelAnswers = listOf(q.answer)
                            )
                        } else "{}",
                        hintsJson = hintsJson,
                        explanation = q.explanation,
                        generationMethod = "cloud_ai"
                    )
                )
                inserted++
            }
            inserted
        } catch (e: Exception) {
            0
        }
    }
}

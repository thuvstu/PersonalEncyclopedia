package com.thuvstu.personalencyclopedia.brain.quiz

import com.thuvstu.personalencyclopedia.brain.quiz.rubric.RubricParser
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.RubricParser.RubricItemJson
import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Rule-based quiz generation (§8.3 stages 1-2, cost = 0)
 */
object RuleBasedQuizGenerator {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Generate QA quiz from a definition entry.
     * "What is the definition of X?" → answer
     */
    fun generateQaFromDefinition(def: EntryDefinitionEntity, topicId: String?): QuizBankEntity {
        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = def.entryId,
            topicId = topicId,
            quizType = "qa",
            question = "「${def.term}」の定義を述べよ。",
            answer = def.definition,
            generationMethod = "rule_based",
            difficulty = 3,
            gradingContextJson = RubricParser.buildGradingContextJson( // ★最適化R5
                items = listOf(
                    RubricItemJson(kind = "keyword", label = "必須用語", expected = def.term, weight = 0.4f),
                    RubricItemJson(kind = "concept", label = "定義内容", expected = def.definition, weight = 0.6f)
                ),
                modelAnswers = listOf(def.definition)
            ),
            hintsJson = json.encodeToString(
                listOfNotNull(
                    def.field?.let { "分野: $it" },
                    def.reading?.let { "読み: $it" }
                ).take(3)
            )
        )
    }

    /**
     * Generate reverse QA: "What term has this definition?" → term
     */
    fun generateReverseQa(def: EntryDefinitionEntity, topicId: String?): QuizBankEntity {
        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = def.entryId,
            topicId = topicId,
            quizType = "qa",
            question = "次の定義に当てはまる用語は？\n「${def.definition.take(100)}${if (def.definition.length > 100) "…" else ""}」",
            answer = def.term,
            generationMethod = "rule_based",
            difficulty = 2,
            gradingContextJson = RubricParser.buildGradingContextJson( // ★最適化R5
                items = listOf(
                    RubricItemJson(kind = "keyword", label = "必須用語", expected = def.term, weight = 1.0f)
                ),
                modelAnswers = listOf(def.term)
            ),
            hintsJson = json.encodeToString(
                listOfNotNull(
                    def.field?.let { "分野: $it" },
                    "文字数: ${def.term.length}文字"
                ).take(3)
            )
        )
    }

    /**
     * Generate MCQ (4 choices) using other definitions in the same field as distractors.
     */
    fun generateMcq(
        target: EntryDefinitionEntity,
        distractors: List<EntryDefinitionEntity>,
        topicId: String?
    ): QuizBankEntity {
        val choices = mutableListOf(target.term)
        distractors.shuffled().take(3).forEach { choices.add(it.term) }
        choices.shuffle()

        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = target.entryId,
            topicId = topicId,
            quizType = "mcq",
            question = "「${target.definition.take(80)}${if (target.definition.length > 80) "…" else ""}」\nに当てはまる用語を選べ。",
            choicesJson = json.encodeToString(choices),
            answer = target.term,
            generationMethod = "rule_based",
            difficulty = 2,
            explanation = "${target.term}: ${target.definition}"
        )
    }

    /**
     * Generate fill-in-the-blank from definition text.
     * Replaces the term within the definition with {{blank}}.
     */
    fun generateFillBlank(def: EntryDefinitionEntity, topicId: String?): QuizBankEntity? {
        // Try to find the term within the definition
        val idx = def.definition.indexOf(def.term)
        if (idx < 0) return null

        val question = def.definition.replaceRange(
            idx, idx + def.term.length, "＿＿＿"
        )

        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = def.entryId,
            topicId = topicId,
            quizType = "fill_blank",
            question = "空欄を埋めよ:\n「$question」",
            answer = def.term,
            generationMethod = "rule_based",
            difficulty = 3,
            gradingContextJson = RubricParser.buildGradingContextJson( // ★最適化R5
                items = listOf(
                    RubricItemJson(kind = "keyword", label = "必須用語", expected = def.term, weight = 1.0f)
                ),
                modelAnswers = listOf(def.term)
            ),
            hintsJson = json.encodeToString(listOf("最初の文字: ${def.term.first()}"))
        )
    }

    /**
     * Batch generate all applicable quiz types from a list of definitions.
     */
    fun generateBatch(
        definitions: List<EntryDefinitionEntity>,
        topicId: String? = null
    ): List<QuizBankEntity> {
        val quizzes = mutableListOf<QuizBankEntity>()

        for (def in definitions) {
            // QA (forward)
            quizzes.add(generateQaFromDefinition(def, topicId))

            // Reverse QA
            quizzes.add(generateReverseQa(def, topicId))

            // MCQ (need at least 4 definitions in same field)
            val sameField = definitions.filter {
                it.field == def.field && it.entryId != def.entryId
            }
            if (sameField.size >= 3) {
                quizzes.add(generateMcq(def, sameField, topicId))
            }

            // Fill blank
            generateFillBlank(def, topicId)?.let { quizzes.add(it) }
        }

        return quizzes
    }
}
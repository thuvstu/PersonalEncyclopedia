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
     * ★P1-3: 並べ替えクイズ。用語を読みの五十音順に並べ、`>` 区切りで答える。
     * 解答は正規化完全一致で採点できる（MultiStageGraderは空白除去・小文字化のみで `>` を保持）。
     */
    fun generateSort(
        members: List<EntryDefinitionEntity>,
        topicId: String?
    ): QuizBankEntity? {
        if (members.size < 3) return null
        val ordered = members.sortedBy { it.reading?.ifBlank { null } ?: it.term }
        val answer = ordered.joinToString(">") { it.term }
        val shuffled = members.map { it.term }.shuffled()
            .let { first ->
                // シャッフル結果が正解と同一なら振り直す（最大5回）
                var s = first
                repeat(5) { if (s.joinToString(">") == answer) s = s.shuffled() }
                s
            }
        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = members.first().entryId,
            topicId = topicId,
            quizType = "sort",
            question = "次の${members.size}語を読みの五十音順に並べよ。",
            choicesJson = json.encodeToString(shuffled),
            answer = answer,
            generationMethod = "rule_based",
            difficulty = 3,
            gradingContextJson = RubricParser.buildGradingContextJson(
                items = listOf(
                    RubricItemJson(kind = "keyword", label = "正順", expected = answer, weight = 1.0f)
                ),
                modelAnswers = listOf(answer)
            ),
            hintsJson = json.encodeToString(listOf("項目数: ${members.size}"))
        )
    }

    /**
     * 複数穴埋め。定義文中の用語＋分野（または別トークン）を ＿＿＿ に置換。
     * 文中に2空欄取れないときは「用語／読み／分野」テンプレへフォールバック。
     */
    fun generateCloze(def: EntryDefinitionEntity, topicId: String?): QuizBankEntity? {
        val field = def.field?.trim()?.takeIf { it.isNotEmpty() }
        val reading = def.reading?.trim()?.takeIf { it.isNotEmpty() }
        val blank = QuizFormatSupport.BLANK
        val tokenRe = Regex("[一-龯ぁ-んァ-ヶA-Za-z0-9]{2,}")
        val stop = setOf("定義", "公式", "例", "体系", "記法", "値")

        var text = def.definition
        val blanks = mutableListOf<String>()
        if (def.term.isNotBlank() && text.contains(def.term)) {
            text = text.replaceFirst(def.term, blank)
            blanks += def.term
            val second = listOfNotNull(field, reading)
                .firstOrNull { it != def.term && text.contains(it) }
                ?: tokenRe.findAll(def.definition)
                    .map { it.value }
                    .firstOrNull { it != def.term && it !in stop && it.length >= 2 && text.contains(it) }
            if (second != null) {
                text = text.replaceFirst(second, blank)
                blanks += second
                return clozeEntity(def, topicId, "空欄を順に埋めよ:\n「$text」", blanks)
            }
        }
        if (field == null) return null
        return if (reading != null) {
            clozeEntity(
                def, topicId,
                "空欄を順に埋めよ:\n「$blank」（読み「$blank」）は「$blank」の用語である。",
                listOf(def.term, reading, field)
            )
        } else {
            clozeEntity(
                def, topicId,
                "空欄を順に埋めよ:\n「$blank」は「$blank」の用語である。",
                listOf(def.term, field)
            )
        }
    }

    private fun clozeEntity(
        def: EntryDefinitionEntity,
        topicId: String?,
        question: String,
        blanks: List<String>
    ): QuizBankEntity {
        val answer = QuizFormatSupport.joinSequence(blanks)
        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = def.entryId,
            topicId = topicId,
            quizType = "cloze",
            question = question,
            answer = answer,
            generationMethod = "rule_based",
            difficulty = 3,
            gradingContextJson = RubricParser.buildGradingContextJson(
                items = blanks.map { b ->
                    RubricItemJson(kind = "keyword", label = "空欄", expected = b, weight = 1f / blanks.size)
                },
                modelAnswers = listOf(answer)
            ),
            hintsJson = json.encodeToString(listOf("空欄数: ${blanks.size}"))
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
                // ★P1-3: 同分野4語で並べ替えを1問
                generateSort(listOf(def) + sameField.take(3), topicId)?.let { quizzes.add(it) }
            }

            // Fill blank
            generateFillBlank(def, topicId)?.let { quizzes.add(it) }

            generateCloze(def, topicId)?.let { quizzes.add(it) }

            val otherFields = definitions.mapNotNull { it.field?.trim()?.takeIf { f -> f.isNotEmpty() } }
                .distinct().filter { it != def.field }
            quizzes.add(generateTf(def, otherFields, topicId))
            if (sameField.isNotEmpty() && otherFields.isNotEmpty()) {
                val others = definitions.filter { it.field != def.field }
                generateMulti(def, sameField, others, topicId)?.let { quizzes.add(it) }
            }
            if (sameField.size >= 2) {
                generateMatch(listOf(def) + sameField.take(3), topicId)?.let { quizzes.add(it) }
            }
        }

        return quizzes
    }

    /** 正誤。「term は field の用語」が真、別分野を付けたら偽。 */
    fun generateTf(
        def: EntryDefinitionEntity,
        otherFields: List<String>,
        topicId: String?
    ): QuizBankEntity {
        val field = def.field?.trim().orEmpty()
        val useFalse = otherFields.isNotEmpty() && (def.entryId.hashCode() and 1) == 1
        val (question, answer, explanation) = if (useFalse) {
            val wrong = otherFields.first()
            Triple(
                "次の文は正しいか。\n「${def.term}」は「$wrong」の用語である。",
                "誤り",
                "${def.term}は「${field.ifBlank { "別分野" }}」の用語。"
            )
        } else {
            Triple(
                "次の文は正しいか。\n「${def.term}」は「${field.ifBlank { "この辞典" }}」の用語である。",
                "正しい",
                def.definition.take(80)
            )
        }
        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = def.entryId,
            topicId = topicId,
            quizType = "tf",
            question = question,
            answer = answer,
            generationMethod = "rule_based",
            difficulty = 2,
            explanation = explanation,
            hintsJson = json.encodeToString(listOfNotNull(field.takeIf { it.isNotEmpty() }?.let { "分野を思い出す" }))
        )
    }

    /** 複数選択。同分野2語＋他分野2語。順不同。 */
    fun generateMulti(
        def: EntryDefinitionEntity,
        sameField: List<EntryDefinitionEntity>,
        otherField: List<EntryDefinitionEntity>,
        topicId: String?
    ): QuizBankEntity? {
        val field = def.field?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val partner = sameField.firstOrNull { it.term != def.term } ?: return null
        val distractors = otherField.filter { it.term != def.term && it.term != partner.term }
            .map { it.term }.distinct().take(2)
        if (distractors.size < 2) return null
        val correct = listOf(def.term, partner.term)
        val choices = (correct + distractors).shuffled()
        val answer = QuizFormatSupport.joinSequence(correct)
        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = def.entryId,
            topicId = topicId,
            quizType = "multi",
            question = "次のうち「$field」の用語をすべて選べ。",
            choicesJson = json.encodeToString(choices),
            answer = answer,
            generationMethod = "rule_based",
            difficulty = 3,
            explanation = "$field: ${correct.joinToString("、")}"
        )
    }

    /** 対応づけ。用語と定義の短い抜粋。 */
    fun generateMatch(
        members: List<EntryDefinitionEntity>,
        topicId: String?
    ): QuizBankEntity? {
        if (members.size < 3) return null
        val pairs = members.take(4).map { it.term to shortDef(it.definition) }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
        if (pairs.size < 3) return null
        val choices = pairs.map { "${it.first}|${it.second}" }
        val answer = QuizFormatSupport.joinMatchAnswer(pairs)
        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = members.first().entryId,
            topicId = topicId,
            quizType = "match",
            question = "用語と定義を対応づけよ。",
            choicesJson = json.encodeToString(choices),
            answer = answer,
            generationMethod = "rule_based",
            difficulty = 3,
            explanation = pairs.joinToString("、") { "${it.first}=${it.second}" },
            hintsJson = json.encodeToString(listOf("組数: ${pairs.size}"))
        )
    }

    private fun shortDef(definition: String): String {
        val t = definition.removePrefix("【定義】").substringBefore("【").trim()
        return t.take(36).ifBlank { definition.take(36) }
    }
}
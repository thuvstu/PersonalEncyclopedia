package com.thuvstu.personalencyclopedia.brain.quiz

import kotlin.math.min

/**
 * Multi-stage grading engine (§8.4)
 * Phase 1: stages 1-5 (normalization → calendar/numeric → fuzzy → synonym → multi-answer)
 * Phase 2: stage 6 (semantic via embedding)
 */
object MultiStageGrader {

    data class GradeResult(
        val isCorrect: Boolean,
        val score: Float,       // 1.0 = full, 0.5 = partial, 0.0 = wrong
        val method: String,     // exact/normalized/numeric/fuzzy/synonym/multi_answer
        val normalizedUser: String = "",
        val normalizedAnswer: String = ""
    )

    // ── Stage 1: Normalized exact match ──
    fun normalize(text: String): String {
        return text
            .trim()
            .replace(Regex("[\\s\\u3000]+"), "")   // Remove all whitespace incl. full-width
            .replace(Regex("[（(]"), "(")
            .replace(Regex("[）)]"), ")")
            .replace(Regex("[、,，]"), ",")
            .replace(Regex("[。．.]"), ".")
            .replace(Regex("[「」『』\"']"), "")
            .lowercase()
            // Full-width alphanumeric → half-width
            .map { c ->
                when (c) {
                    in 'Ａ'..'Ｚ' -> (c - 'Ａ' + 'A')
                    in 'ａ'..'ｚ' -> (c - 'ａ' + 'a')
                    in '０'..'９' -> (c - '０' + '0')
                    '　' -> ' '
                    else -> c
                }
            }.joinToString("")
    }

    fun gradeExact(userAnswer: String, correctAnswer: String): GradeResult {
        if (userAnswer.trim() == correctAnswer.trim()) {
            return GradeResult(true, 1.0f, "exact")
        }
        val nu = normalize(userAnswer)
        val na = normalize(correctAnswer)
        if (nu == na) {
            return GradeResult(true, 1.0f, "normalized", nu, na)
        }
        return GradeResult(false, 0f, "exact", nu, na)
    }

    // ── Stage 2: Calendar / Numeric conversion ──
    private val japaneseEras = listOf(
        "令和" to 2018, "平成" to 1988, "昭和" to 1925,
        "大正" to 1911, "明治" to 1867
    )

    fun parseYear(text: String): Int? {
        // Western year
        Regex("(\\d{3,4})年?").find(text)?.let { return it.groupValues[1].toIntOrNull() }
        // Japanese era
        for ((era, baseYear) in japaneseEras) {
            Regex("$era(\\d{1,2})年?").find(text)?.let { m ->
                val yearInEra = m.groupValues[1].toIntOrNull() ?: return@let
                return baseYear + yearInEra
            }
            if (text.contains("${era}元")) return baseYear + 1
        }
        // BC
        Regex("紀元前(\\d+)年?").find(text)?.let {
            return -(it.groupValues[1].toIntOrNull() ?: return null)
        }
        return null
    }

    fun gradeNumeric(userAnswer: String, correctAnswer: String): GradeResult? {
        val userYear = parseYear(userAnswer)
        val correctYear = parseYear(correctAnswer)
        if (userYear != null && correctYear != null) {
            val correct = userYear == correctYear
            return GradeResult(correct, if (correct) 1.0f else 0f, "numeric")
        }
        // Pure numeric comparison
        val userNum = userAnswer.trim().toDoubleOrNull()
        val correctNum = correctAnswer.trim().toDoubleOrNull()
        if (userNum != null && correctNum != null) {
            val correct = kotlin.math.abs(userNum - correctNum) < 1e-9
            return GradeResult(correct, if (correct) 1.0f else 0f, "numeric")
        }
        return null // Not applicable
    }

    // ── Stage 3: Fuzzy matching (Levenshtein) ──
    fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[m][n]
    }

    fun similarity(a: String, b: String): Float {
        if (a.isEmpty() && b.isEmpty()) return 1.0f
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0f
        return 1.0f - levenshteinDistance(a, b).toFloat() / maxLen
    }

    fun gradeFuzzy(userAnswer: String, correctAnswer: String, threshold: Float = 0.85f): GradeResult {
        val nu = normalize(userAnswer)
        val na = normalize(correctAnswer)
        val sim = similarity(nu, na)
        return if (sim >= threshold) {
            GradeResult(true, sim, "fuzzy", nu, na)
        } else {
            GradeResult(false, 0f, "fuzzy", nu, na)
        }
    }

    // ── Stage 4: Synonym matching ──
    private val builtinSynonyms = mapOf(
        "ww1" to listOf("第一次世界大戦", "world war 1", "world war i"),
        "ww2" to listOf("第二次世界大戦", "world war 2", "world war ii"),
        "usa" to listOf("アメリカ", "米国", "united states"),
        "uk" to listOf("イギリス", "英国", "united kingdom"),
    )

    fun gradeSynonym(userAnswer: String, correctAnswer: String): GradeResult? {
        val nu = normalize(userAnswer)
        val na = normalize(correctAnswer)
        // Check if user answer is a known synonym of the correct answer
        for ((key, synonyms) in builtinSynonyms) {
            val allForms = (listOf(key) + synonyms).map { normalize(it) }
            if (na in allForms && nu in allForms) {
                return GradeResult(true, 1.0f, "synonym", nu, na)
            }
        }
        return null
    }

    // ── Stage 5: Multiple answer expansion ──
    fun expandAnswers(answer: String): List<String> {
        // Split by common delimiters: /, |, , (within parentheses)
        val parts = mutableListOf<String>()
        // Handle "A(B)" → "A", "AB"
        Regex("(.+?)\\((.+?)\\)").findAll(answer).forEach { m ->
            val prefix = m.groupValues[1]
            val optional = m.groupValues[2]
            parts.add(prefix + optional)
            parts.add(prefix)
        }
        // Split by / or |
        answer.split(Regex("[/|]")).forEach { parts.add(it.trim()) }
        if (parts.isEmpty()) parts.add(answer)
        return parts.distinct()
    }

    // ── Full grading pipeline ──
    fun grade(
        userAnswer: String,
        correctAnswer: String,
        mode: String = "standard"  // standard/lenient/strict/exact
    ): GradeResult {
        if (userAnswer.isBlank()) return GradeResult(false, 0f, "exact")

        // Stage 1: Exact / Normalized
        val exactResult = gradeExact(userAnswer, correctAnswer)
        if (exactResult.isCorrect) return exactResult

        if (mode == "exact") return exactResult

        // Stage 5: Multiple answers (check all variants)
        val answers = expandAnswers(correctAnswer)
        if (answers.size > 1) {
            for (ans in answers) {
                val r = gradeExact(userAnswer, ans)
                if (r.isCorrect) return r.copy(method = "multi_answer")
            }
        }

        if (mode == "strict") return exactResult

        // Stage 2: Numeric / Calendar
        val numericResult = gradeNumeric(userAnswer, correctAnswer)
        if (numericResult != null && numericResult.isCorrect) return numericResult

        // Stage 4: Synonym
        val synonymResult = gradeSynonym(userAnswer, correctAnswer)
        if (synonymResult != null && synonymResult.isCorrect) return synonymResult

        // Stage 3: Fuzzy
        val threshold = if (mode == "lenient") 0.70f else 0.85f
        val fuzzyResult = gradeFuzzy(userAnswer, correctAnswer, threshold)
        if (fuzzyResult.isCorrect) return fuzzyResult

        return GradeResult(false, 0f, fuzzyResult.method, fuzzyResult.normalizedUser, fuzzyResult.normalizedAnswer)
    }
}
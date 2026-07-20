package com.thuvstu.personalencyclopedia.brain.quiz

import kotlin.math.min

object MultiStageGrader {

    data class GradeResult(
        val isCorrect: Boolean,
        val score: Float,
        val method: String,
        val normalizedUser: String = "",
        val normalizedAnswer: String = ""
    )

    fun normalize(text: String): String {
        return text
            .trim()
            .replace(Regex("[\\s\\u3000]+"), "")
            .replace(Regex("[（(]"), "(")
            .replace(Regex("[）)]"), ")")
            .replace(Regex("[、,，]"), ",")
            .replace(Regex("[。．.]"), ".")
            .replace(Regex("[「」『』\"']"), "")
            .lowercase()
            .map { c ->
                when (c) {
                    in 'Ａ'..'Ｚ' -> (c.code - 'Ａ'.code + 'A'.code).toChar()
                    in 'ａ'..'ｚ' -> (c.code - 'ａ'.code + 'a'.code).toChar()
                    in '０'..'９' -> (c.code - '０'.code + '0'.code).toChar()
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

    private val japaneseEras = listOf(
        "令和" to 2018, "平成" to 1988, "昭和" to 1925,
        "大正" to 1911, "明治" to 1867
    )

    fun parseYear(text: String): Int? {
        Regex("(\\d{3,4})年?").find(text)?.let { return it.groupValues[1].toIntOrNull() }
        for ((era, baseYear) in japaneseEras) {
            Regex("$era(\\d{1,2})年?").find(text)?.let { m ->
                val yearInEra = m.groupValues[1].toIntOrNull() ?: return@let
                return baseYear + yearInEra
            }
            if (text.contains("${era}元")) return baseYear + 1
        }
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
        val userNum = userAnswer.trim().toDoubleOrNull()
        val correctNum = correctAnswer.trim().toDoubleOrNull()
        if (userNum != null && correctNum != null) {
            val correct = kotlin.math.abs(userNum - correctNum) < 1e-9
            return GradeResult(correct, if (correct) 1.0f else 0f, "numeric")
        }
        return null
    }

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

    private val builtinSynonyms = mapOf(
        "ww1" to listOf("第一次世界大戦", "world war 1", "world war i"),
        "ww2" to listOf("第二次世界大戦", "world war 2", "world war ii"),
        "usa" to listOf("アメリカ", "米国", "united states"),
        "uk" to listOf("イギリス", "英国", "united kingdom"),
    )

    fun gradeSynonym(userAnswer: String, correctAnswer: String): GradeResult? {
        val nu = normalize(userAnswer)
        val na = normalize(correctAnswer)
        for ((key, synonyms) in builtinSynonyms) {
            val allForms = (listOf(key) + synonyms).map { normalize(it) }
            if (na in allForms && nu in allForms) {
                return GradeResult(true, 1.0f, "synonym", nu, na)
            }
        }
        return null
    }

    fun expandAnswers(answer: String): List<String> {
        val parts = mutableListOf<String>()
        Regex("(.+?)\\((.+?)\\)").findAll(answer).forEach { m ->
            val prefix = m.groupValues[1]
            val optional = m.groupValues[2]
            parts.add(prefix + optional)
            parts.add(prefix)
        }
        answer.split(Regex("[/|]")).forEach { parts.add(it.trim()) }
        if (parts.isEmpty()) parts.add(answer)
        return parts.distinct()
    }

    fun grade(
        userAnswer: String,
        correctAnswer: String,
        mode: String = "standard"
    ): GradeResult {
        if (userAnswer.isBlank()) return GradeResult(false, 0f, "exact")

        val exactResult = gradeExact(userAnswer, correctAnswer)
        if (exactResult.isCorrect) return exactResult

        if (mode == "exact") return exactResult

        val answers = expandAnswers(correctAnswer)
        if (answers.size > 1) {
            for (ans in answers) {
                val r = gradeExact(userAnswer, ans)
                if (r.isCorrect) return r.copy(method = "multi_answer")
            }
        }

        if (mode == "strict") return exactResult

        val numericResult = gradeNumeric(userAnswer, correctAnswer)
        if (numericResult != null && numericResult.isCorrect) return numericResult

        val synonymResult = gradeSynonym(userAnswer, correctAnswer)
        if (synonymResult != null && synonymResult.isCorrect) return synonymResult

        val threshold = if (mode == "lenient") 0.70f else 0.85f
        val fuzzyResult = gradeFuzzy(userAnswer, correctAnswer, threshold)
        if (fuzzyResult.isCorrect) return fuzzyResult

        return GradeResult(false, 0f, fuzzyResult.method, fuzzyResult.normalizedUser, fuzzyResult.normalizedAnswer)
    }
}
package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import kotlin.math.min

/**
 * 新採点システム用のテキスト正規化・類似度ユーティリティ。
 * MultiStageGrader.normalize / similarity と同一の仕様で実装する(仕様乖離の検出は
 * TextNormConsistencyTest が MultiStageGrader と突き合わせる)。
 */
object TextNorm {

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

    /** 文字バイグラム(日本語向けトークン近似)。テキストの重なり判定に使う */
    fun bigrams(text: String): Set<String> {
        val n = normalize(text)
        if (n.length < 2) return setOf(n)
        return n.windowed(2).toSet()
    }

    /** バイグラム集合の重なり率(0..1)。minサイズ基準 */
    fun bigramOverlap(a: String, b: String): Float {
        val ga = bigrams(a)
        val gb = bigrams(b)
        if (ga.isEmpty() || gb.isEmpty()) return 0f
        return ga.intersect(gb).size.toFloat() / minOf(ga.size, gb.size)
    }

    /** 空白区切りのトークン(半角空白区切り文のみ有効) */
    fun tokens(text: String): List<String> =
        normalize(text).split(Regex("\\s+")).filter { it.isNotBlank() }
}

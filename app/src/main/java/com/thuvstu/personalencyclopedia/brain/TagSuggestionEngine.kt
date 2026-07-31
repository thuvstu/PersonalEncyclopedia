package com.thuvstu.personalencyclopedia.brain

import com.thuvstu.personalencyclopedia.db.dao.TagDao
import com.thuvstu.personalencyclopedia.db.entity.TagEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * タグ・表記揺れの自動統合サジェストエンジン (§12.6).
 * 新規タグ作成時に既存タグとの類似性(文字コード距離/Fuzzy)を計算し、表記揺れの可能性があるタグをサジェストする。
 */
@Singleton
class TagSuggestionEngine @Inject constructor(
    private val tagDao: TagDao
) {
    data class TagSuggestion(
        val existingTag: TagEntity,
        val similarity: Float
    )

    suspend fun suggestSimilarTags(newTagName: String, threshold: Float = 0.75f): List<TagSuggestion> {
        val trimmed = newTagName.trim()
        if (trimmed.isEmpty()) return emptyList()

        val allTags = tagDao.observeAll().first()
        val suggestions = mutableListOf<TagSuggestion>()

        for (tag in allTags) {
            if (tag.name.equals(trimmed, ignoreCase = true)) continue

            val sim = calculateSimilarity(trimmed.lowercase(), tag.name.lowercase())
            if (sim >= threshold) {
                suggestions.add(TagSuggestion(existingTag = tag, similarity = sim))
            }
        }

        return suggestions.sortedByDescending { it.similarity }
    }

    private fun calculateSimilarity(s1: String, s2: String): Float {
        val maxLen = kotlin.math.max(s1.length, s2.length)
        if (maxLen == 0) return 1.0f

        val dist = levenshteinDistance(s1, s2)
        return 1.0f - (dist.toFloat() / maxLen.toFloat())
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = kotlin.math.min(
                    dp[i - 1][j] + 1,
                    kotlin.math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}

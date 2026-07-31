package com.thuvstu.personalencyclopedia.brain.search

import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.SearchDocumentDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResult(
    val entryId: String,
    val score: Double
)

enum class SearchMode {
    HYBRID, FULLTEXT, SEMANTIC, LIKE
}

/**
 * Hybrid Search with RRF (Reciprocal Rank Fusion) (§7.2.2).
 */
@Singleton
class HybridSearchEngine @Inject constructor(
    private val searchDocumentDao: SearchDocumentDao,
    private val vectorIndex: InMemoryVectorIndex,
    private val geminiClient: GeminiClient,
    private val entryDao: EntryDao
) {
    companion object {
        private const val RRF_K = 60
        private const val RECENT_7D_BOOST = 0.05
        private const val RECENT_30D_BOOST = 0.02
    }

    suspend fun search(
        query: String,
        mode: SearchMode = SearchMode.HYBRID,
        limit: Int = 20
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        return when (mode) {
            SearchMode.LIKE -> {
                entryDao.search(query, limit).first()
                    .mapIndexed { i, e -> SearchResult(e.id, 1.0 / (RRF_K + i + 1)) }
            }
            SearchMode.FULLTEXT -> fulltextSearch(query, limit)
            SearchMode.SEMANTIC -> semanticSearch(query, limit)
            SearchMode.HYBRID -> hybridSearch(query, limit)
        }
    }

    private suspend fun fulltextSearch(query: String, limit: Int): List<SearchResult> {
        val ftsQuery = NgramTokenizer.buildFtsQuery(query)
        if (ftsQuery.isBlank()) return emptyList()

        return try {
            searchDocumentDao.ftsSearch(ftsQuery, limit * 2)
                .mapIndexed { i, id -> SearchResult(id, 1.0 / (RRF_K + i + 1)) }
                .take(limit)
        } catch (e: Exception) {
            // FTS query syntax error fallback
            emptyList()
        }
    }

    private suspend fun semanticSearch(query: String, limit: Int): List<SearchResult> {
        if (!geminiClient.isConfigured() || !vectorIndex.isLoaded()) return emptyList()

        val queryVector = geminiClient.embed(query) ?: return emptyList()
        return vectorIndex.topK(queryVector, limit)
            .map { (id, sim) -> SearchResult(id, sim.toDouble()) }
    }

    private suspend fun hybridSearch(query: String, limit: Int): List<SearchResult> {
        // 1. Fulltext results
        val ftsQuery = NgramTokenizer.buildFtsQuery(query)
        val fulltextRanked: Map<String, Int> = if (ftsQuery.isNotBlank()) {
            try {
                searchDocumentDao.ftsSearch(ftsQuery, 50)
                    .mapIndexed { i, id -> id to (i + 1) }
                    .toMap()
            } catch (e: Exception) { emptyMap() }
        } else emptyMap()

        // 2. Semantic results
        val semanticRanked: Map<String, Int> = if (geminiClient.isConfigured() && vectorIndex.isLoaded()) {
            val queryVector = geminiClient.embed(query)
            if (queryVector != null) {
                vectorIndex.topK(queryVector, 50)
                    .mapIndexed { i, (id, _) -> id to (i + 1) }
                    .toMap()
            } else emptyMap()
        } else emptyMap()

        // 3. RRF merge
        val allIds = fulltextRanked.keys + semanticRanked.keys
        val now = System.currentTimeMillis()
        val day7 = 7L * 24 * 60 * 60 * 1000
        val day30 = 30L * 24 * 60 * 60 * 1000

        return allIds.map { id ->
            val rrfScore = (fulltextRanked[id]?.let { 1.0 / (RRF_K + it) } ?: 0.0) +
                    (semanticRanked[id]?.let { 1.0 / (RRF_K + it) } ?: 0.0)

            // Recency boost
            val entry = entryDao.getById(id)
            val recencyBoost = when {
                entry == null -> 0.0
                now - entry.createdAt < day7 -> RECENT_7D_BOOST
                now - entry.createdAt < day30 -> RECENT_30D_BOOST
                else -> 0.0
            }

            SearchResult(id, rrfScore + recencyBoost)
        }
            .filter { result ->
                // Exclude muted entries
                val entry = entryDao.getById(result.entryId)
                entry != null && !entry.isMuted && entry.deletedAt == null
            }
            .sortedByDescending { it.score }
            .take(limit)
    }
}
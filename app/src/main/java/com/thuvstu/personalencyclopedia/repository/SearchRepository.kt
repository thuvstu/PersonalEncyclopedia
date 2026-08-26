package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.brain.ai.EmbeddingQueue
import com.thuvstu.personalencyclopedia.brain.search.HybridSearchEngine
import com.thuvstu.personalencyclopedia.brain.search.SearchMode
import com.thuvstu.personalencyclopedia.brain.search.SearchResult
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.util.timed
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val hybridSearch: HybridSearchEngine,
    private val entryDao: EntryDao,
    private val embeddingQueue: EmbeddingQueue
) {
    suspend fun search(
        query: String,
        mode: SearchMode = SearchMode.HYBRID,
        limit: Int = 20
    ): List<EntryEntity> {
        val results = timed("App", "hybridSearch") { hybridSearch.search(query, mode, limit) }
        return results.mapNotNull { result ->
            entryDao.getById(result.entryId)
        }.filter { it.deletedAt == null }
    }

    /**
     * Index an entry for search (call after create/update).
     */
    suspend fun indexEntry(entryId: String) {
        embeddingQueue.enqueue(entryId)
    }

    /**
     * Rebuild all search indices (one-time migration).
     */
    suspend fun rebuildAllIndices() {
        embeddingQueue.rebuildAllSearchDocuments()
    }
}
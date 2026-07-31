package com.thuvstu.personalencyclopedia.brain.search

import com.thuvstu.personalencyclopedia.brain.ai.cosineSimilarity
import com.thuvstu.personalencyclopedia.brain.ai.toFloatArray
import com.thuvstu.personalencyclopedia.db.dao.EmbeddingDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brute-force in-memory vector index (§7.1.5).
 * Loads all embeddings at startup. For personal scale (thousands of entries),
 * 768-dim × 10K entries ≈ 30MB RAM, search in tens of ms.
 */
@Singleton
class InMemoryVectorIndex @Inject constructor(
    private val embeddingDao: EmbeddingDao
) {
    private var ids: Array<String> = emptyArray()
    private var vectors: Array<FloatArray> = emptyArray()
    private var loaded = false

    suspend fun load() {
        val all = embeddingDao.getAll()
        ids = Array(all.size) { all[it].entryId }
        vectors = Array(all.size) { all[it].vectorBlob.toFloatArray() }
        loaded = true
    }

    fun isLoaded(): Boolean = loaded

    fun size(): Int = ids.size

    fun topK(query: FloatArray, k: Int = 20): List<Pair<String, Float>> {
        if (!loaded || ids.isEmpty()) return emptyList()
        return ids.indices
            .map { i -> ids[i] to cosineSimilarity(query, vectors[i]) }
            .sortedByDescending { it.second }
            .take(k)
    }

    /**
     * Add a single vector without full reload.
     */
    fun addVector(entryId: String, vector: FloatArray) {
        val existingIdx = ids.indexOf(entryId)
        if (existingIdx >= 0) {
            vectors[existingIdx] = vector
        } else {
            ids = ids + entryId
            vectors = vectors + vector
        }
    }

    fun removeVector(entryId: String) {
        val idx = ids.indexOf(entryId)
        if (idx >= 0) {
            ids = ids.filterIndexed { i, _ -> i != idx }.toTypedArray()
            vectors = vectors.filterIndexed { i, _ -> i != idx }.toTypedArray()
        }
    }
}
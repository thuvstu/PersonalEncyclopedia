package com.thuvstu.personalencyclopedia.brain.search

import com.thuvstu.personalencyclopedia.brain.ai.cosineSimilarity
import com.thuvstu.personalencyclopedia.brain.ai.toFloatArray
import com.thuvstu.personalencyclopedia.db.dao.EmbeddingDao
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brute-force in-memory vector index (§7.1.5).
 * Loads all embeddings at startup. For personal scale (thousands of entries),
 * 768-dim × 10K entries ≈ 30MB RAM, search in tens of ms.
 *
 * ★ D1 (GAP-4): スレッドセーフ化。
 * ids/vectors の直接 var 保持から、イミュータブルなスナップショット + AtomicReference 方式へ変更。
 * - topK は常に一貫したスナップショットを読む（read途中に addVector が割り込んでも不整合にならない）
 * - addVector / removeVector は compare-and-swap (CAS) ループで安全に差し替える
 */
@Singleton
class InMemoryVectorIndex @Inject constructor(
    private val embeddingDao: EmbeddingDao
) {
    /**
     * 不変スナップショット。AtomicReference で保持することで、
     * どのスレッドが読んでも整合したペアが見える。
     */
    private data class Snapshot(
        val ids: Array<String>,
        val vectors: Array<FloatArray>
    ) {
        companion object {
            val EMPTY = Snapshot(emptyArray(), emptyArray())
        }
    }

    private val snapshotRef = AtomicReference(Snapshot.EMPTY)
    @Volatile private var loaded = false

    suspend fun load() {
        val all = embeddingDao.getAll()
        val snap = Snapshot(
            ids     = Array(all.size) { all[it].entryId },
            vectors = Array(all.size) { all[it].vectorBlob.toFloatArray() }
        )
        snapshotRef.set(snap)
        loaded = true
    }

    fun isLoaded(): Boolean = loaded

    fun size(): Int = snapshotRef.get().ids.size

    /**
     * 呼び出し時点のスナップショットを取得して検索するため、
     * addVector との競合があっても常に整合した結果を返す。
     */
    fun topK(query: FloatArray, k: Int = 20): List<Pair<String, Float>> {
        val snap = snapshotRef.get()
        if (!loaded || snap.ids.isEmpty()) return emptyList()
        return snap.ids.indices
            .map { i -> snap.ids[i] to cosineSimilarity(query, snap.vectors[i]) }
            .sortedByDescending { it.second }
            .take(k)
    }

    /**
     * ★ D1: CAS ループで不変スナップショットを差し替える。
     * addVector は topK の読み取りに割り込まない。
     */
    fun addVector(entryId: String, vector: FloatArray) {
        while (true) {
            val old = snapshotRef.get()
            val existingIdx = old.ids.indexOf(entryId)
            val newSnap = if (existingIdx >= 0) {
                val newVectors = old.vectors.copyOf()
                newVectors[existingIdx] = vector
                Snapshot(old.ids, newVectors)
            } else {
                Snapshot(old.ids + entryId, old.vectors + vector)
            }
            if (snapshotRef.compareAndSet(old, newSnap)) return
        }
    }

    /**
     * ★ D1: CAS ループで不変スナップショットを差し替える。
     */
    fun removeVector(entryId: String) {
        while (true) {
            val old = snapshotRef.get()
            val idx = old.ids.indexOf(entryId)
            if (idx < 0) return  // 存在しない → 何もしない
            val newSnap = Snapshot(
                ids     = old.ids.filterIndexed { i, _ -> i != idx }.toTypedArray(),
                vectors = old.vectors.filterIndexed { i, _ -> i != idx }.toTypedArray()
            )
            if (snapshotRef.compareAndSet(old, newSnap)) return
        }
    }
}
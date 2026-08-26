package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.EmbeddingEntity
import com.thuvstu.personalencyclopedia.db.entity.EmbeddingJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: EmbeddingEntity)

    /** Round 0 (M-1): SyntheticDataSeeder用の一括挿入 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(embeddings: List<EmbeddingEntity>)

    @Query("SELECT * FROM embedding WHERE entryId = :entryId")
    suspend fun getByEntryId(entryId: String): EmbeddingEntity?

    @Query("SELECT * FROM embedding")
    suspend fun getAll(): List<EmbeddingEntity>

    @Query("DELETE FROM embedding WHERE entryId = :entryId")
    suspend fun deleteByEntryId(entryId: String)

    @Query("SELECT COUNT(*) FROM embedding")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM embedding")
    suspend fun count(): Int

    // PERF-8: sqlite-vec によるDB側近傍検索。InMemoryの全件ロード(50kでOOM/9.8s)を置換
    // Roomのクエリ検証は vec_distance_cosine を知らないため RawQuery で回避
    data class VecDistanceRow(val entryId: String, val distance: Double)

    @RawQuery
    suspend fun vecSearchRaw(query: androidx.sqlite.db.SupportSQLiteQuery): List<VecDistanceRow>

    suspend fun vecSearch(queryBlob: ByteArray, limit: Int): List<VecDistanceRow> {
        val sql = "SELECT entryId, vec_distance_cosine(vectorBlob, ?) AS distance FROM embedding ORDER BY distance ASC LIMIT ?"
        val q = androidx.sqlite.db.SimpleSQLiteQuery(sql, arrayOf(queryBlob, limit))
        return vecSearchRaw(q)
    }

    // ── Embedding Job Queue ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJob(job: EmbeddingJobEntity)

    @Query("SELECT * FROM embedding_job WHERE status IN ('queued', 'running') ORDER BY queuedAt")
    suspend fun getPendingJobs(): List<EmbeddingJobEntity>

    @Query("UPDATE embedding_job SET status = :status, attempts = :attempts, error = :error, doneAt = :doneAt WHERE entryId = :entryId")
    suspend fun updateJobStatus(entryId: String, status: String, attempts: Int = 0, error: String? = null, doneAt: Long? = null)

    @Query("SELECT * FROM embedding_job WHERE entryId = :entryId")
    suspend fun getJob(entryId: String): EmbeddingJobEntity?

    @Query("DELETE FROM embedding_job WHERE status = 'done' AND doneAt < :before")
    suspend fun pruneOldJobs(before: Long)
}
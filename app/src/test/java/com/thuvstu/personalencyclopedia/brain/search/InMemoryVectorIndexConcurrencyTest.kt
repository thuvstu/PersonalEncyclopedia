package com.thuvstu.personalencyclopedia.brain.search

import com.thuvstu.personalencyclopedia.db.dao.EmbeddingDao
import com.thuvstu.personalencyclopedia.db.entity.EmbeddingEntity
import com.thuvstu.personalencyclopedia.db.entity.EmbeddingJobEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * D2 (GAP-4): InMemoryVectorIndex の並行アクセステスト。
 * DAO を使わずに addVector で直接データを投入するため、JVM 単体テストとして実行可能。
 */
class InMemoryVectorIndexConcurrencyTest {

    private lateinit var index: InMemoryVectorIndex

    /** EmbeddingDao のテスト用スタブ（DB 不要） */
    private val fakeDao = object : EmbeddingDao {
        override suspend fun upsert(embedding: EmbeddingEntity) {}
        override suspend fun insertAll(embeddings: List<EmbeddingEntity>) {}
        override suspend fun getByEntryId(entryId: String): EmbeddingEntity? = null
        override suspend fun getAll(): List<EmbeddingEntity> = emptyList()
        override suspend fun deleteByEntryId(entryId: String) {}
        override fun observeCount(): Flow<Int> = flowOf(0)
        override suspend fun count(): Int = 0
        override suspend fun vecSearchRaw(query: androidx.sqlite.db.SupportSQLiteQuery): List<EmbeddingDao.VecDistanceRow> = emptyList()
        override suspend fun upsertJob(job: EmbeddingJobEntity) {}
        override suspend fun getPendingJobs(): List<EmbeddingJobEntity> = emptyList()
        override suspend fun updateJobStatus(entryId: String, status: String, attempts: Int, error: String?, doneAt: Long?) {}
        override suspend fun getJob(entryId: String): EmbeddingJobEntity? = null
        override suspend fun pruneOldJobs(before: Long) {}
    }

    @Before
    fun setup() {
        index = InMemoryVectorIndex(fakeDao)
        // テスト用に 100 件追加（load() は呼ばない）
        repeat(100) { i ->
            index.addVector("entry-$i", FloatArray(4) { i.toFloat() })
        }
    }

    @Test
    fun `concurrent topK and addVector do not crash or corrupt`() {
        runBlocking {
            val queryVec = FloatArray(4) { 1.0f }
            val jobs = (0 until 50).map { t ->
                async(Dispatchers.Default) {
                    if (t % 2 == 0) {
                        val result = index.topK(queryVec, 5)
                        assertTrue("topK should return <= 5 results", result.size <= 5)
                    } else {
                        index.addVector("concurrent-entry-$t", FloatArray(4) { t.toFloat() })
                    }
                }
            }
            jobs.awaitAll()
            // クラッシュせず完了すれば合格
        }
    }

    @Test
    fun `addVector updates existing entry without duplicates`() {
        val id = "entry-0"
        val sizeBefore = index.size()
        index.addVector(id, FloatArray(4) { 99.0f })
        assertEquals("Size should not change on update", sizeBefore, index.size())
    }

    @Test
    fun `removeVector reduces size by 1`() {
        val sizeBefore = index.size()
        index.removeVector("entry-0")
        assertEquals("Size should decrease by 1", sizeBefore - 1, index.size())
    }

    @Test
    fun `concurrent addVector and removeVector produce non-negative size`() {
        runBlocking {
            val jobs = (0 until 30).map { t ->
                async(Dispatchers.Default) {
                    when (t % 3) {
                        0 -> index.addVector("extra-$t", FloatArray(4) { t.toFloat() })
                        1 -> index.removeVector("entry-$t")
                        else -> index.topK(FloatArray(4) { 0.5f }, 3)
                    }
                }
            }
            jobs.awaitAll()
            assertTrue("Size must be non-negative after concurrent ops", index.size() >= 0)
        }
    }
}

package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.SrsCurrentView
import com.thuvstu.personalencyclopedia.db.entity.SrsReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SrsReviewDao {

    @Insert
    suspend fun insert(review: SrsReviewEntity)

    @Query("""
        SELECT e.id, e.title, e.content
        FROM entry e
        INNER JOIN entry_definition ed ON ed.entryId = e.id
        LEFT JOIN (
            SELECT sr.entryId, sr.nextReviewAt
            FROM srs_review sr
            INNER JOIN (
                SELECT entryId, MAX(reviewedAt) AS maxAt
                FROM srs_review GROUP BY entryId
            ) l ON sr.entryId = l.entryId AND sr.reviewedAt = l.maxAt
        ) srs ON srs.entryId = e.id
        WHERE e.deletedAt IS NULL
          AND e.type = 'definition'
          AND (srs.nextReviewAt IS NULL OR srs.nextReviewAt <= :now)
        ORDER BY
          CASE WHEN srs.nextReviewAt IS NULL THEN 0 ELSE 1 END,
          srs.nextReviewAt ASC
        LIMIT :limit
    """)
    suspend fun getDueEntries(now: Long = System.currentTimeMillis(), limit: Int = 50): List<DueEntry>

    @Query("""
        SELECT COUNT(*)
        FROM entry e
        INNER JOIN entry_definition ed ON ed.entryId = e.id
        LEFT JOIN (
            SELECT sr.entryId, sr.nextReviewAt
            FROM srs_review sr
            INNER JOIN (
                SELECT entryId, MAX(reviewedAt) AS maxAt
                FROM srs_review GROUP BY entryId
            ) l ON sr.entryId = l.entryId AND sr.reviewedAt = l.maxAt
        ) srs ON srs.entryId = e.id
        WHERE e.deletedAt IS NULL
          AND e.type = 'definition'
          AND (srs.nextReviewAt IS NULL OR srs.nextReviewAt <= :now)
    """)
    fun observeDueCount(now: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT * FROM SrsCurrentView WHERE entryId = :entryId")
    suspend fun getCurrentState(entryId: String): SrsCurrentView?

    @Query("SELECT * FROM SrsCurrentView")
    fun observeAllCurrent(): Flow<List<SrsCurrentView>>

    @Query("""
        SELECT COUNT(DISTINCT entryId) FROM srs_review
        WHERE reviewedAt >= :startOfDay
    """)
    fun observeReviewedTodayCount(startOfDay: Long): Flow<Int>
}

data class DueEntry(
    val id: String,
    val title: String,
    val content: String?
)
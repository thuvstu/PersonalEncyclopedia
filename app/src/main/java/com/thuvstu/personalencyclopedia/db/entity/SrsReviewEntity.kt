package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.*
import java.util.UUID

@Entity(
    tableName = "srs_review",
    indices = [Index("entryId"), Index("reviewedAt"), Index("nextReviewAt")]
)
data class SrsReviewEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val reviewedAt: Long = System.currentTimeMillis(),
    val grade: Int,             // 0-5 (SM-2)
    val intervalDays: Int,
    val easeFactor: Float = 2.5f,
    val nextReviewAt: Long
)

@DatabaseView(
    """
    SELECT sr.entryId, sr.grade, sr.intervalDays, sr.easeFactor, sr.nextReviewAt,
           sr.reviewedAt AS lastReviewedAt
    FROM srs_review sr
    INNER JOIN (
        SELECT entryId, MAX(reviewedAt) AS maxReviewedAt
        FROM srs_review
        GROUP BY entryId
    ) latest ON sr.entryId = latest.entryId AND sr.reviewedAt = latest.maxReviewedAt
    """
)
data class SrsCurrentView(
    val entryId: String,
    val grade: Int,
    val intervalDays: Int,
    val easeFactor: Float,
    val nextReviewAt: Long,
    val lastReviewedAt: Long
)
package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.ProgressEventEntity
import kotlinx.coroutines.flow.Flow

data class DailyActivityCount(
    val day: String,      // yyyy-MM-dd
    val count: Int
)

data class MasteryByTopic(
    val topicId: String,
    val topicName: String,
    val totalScore: Float,
    val quizCount: Int
)

@Dao
interface ProgressEventDao {

    @Insert
    suspend fun insert(event: ProgressEventEntity)

    // Heatmap: activity count per day for the last N days
    @Query("""
        SELECT strftime('%Y-%m-%d', createdAt / 1000, 'unixepoch', 'localtime') AS day,
               COUNT(*) AS count
        FROM progress_events
        WHERE createdAt >= :since
        GROUP BY day
        ORDER BY day
    """)
    suspend fun getActivityByDay(since: Long): List<DailyActivityCount>

    @Query("""
        SELECT COUNT(*) FROM progress_events
        WHERE eventType = 'reviewed' AND createdAt >= :startOfDay
    """)
    fun observeReviewsToday(startOfDay: Long): Flow<Int>

    @Query("""
        SELECT COUNT(DISTINCT strftime('%Y-%m-%d', createdAt / 1000, 'unixepoch', 'localtime'))
        FROM progress_events
        WHERE eventType IN ('reviewed', 'answered')
    """)
    fun observeStudyDayCount(): Flow<Int>

    // Streak: consecutive days with study activity ending today/yesterday
    @Query("""
        SELECT DISTINCT strftime('%Y-%m-%d', createdAt / 1000, 'unixepoch', 'localtime') AS day
        FROM progress_events
        WHERE eventType IN ('reviewed', 'answered')
        ORDER BY day DESC
    """)
    suspend fun getStudyDays(): List<String>
}
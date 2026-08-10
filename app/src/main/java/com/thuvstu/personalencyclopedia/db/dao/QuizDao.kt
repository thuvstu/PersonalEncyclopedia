package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.QuizAttemptEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizMasteryView
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizBankEntity)

    @Insert
    suspend fun insertQuizzes(quizzes: List<QuizBankEntity>)

    @Insert
    suspend fun insertAttempt(attempt: QuizAttemptEntity)

    @Query("SELECT * FROM quiz_bank WHERE id = :id")
    suspend fun getQuizById(id: String): QuizBankEntity?

    @Query("""
        SELECT * FROM quiz_bank
        WHERE isActive = 1
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeAllQuizzes(limit: Int = 50, offset: Int = 0): Flow<List<QuizBankEntity>>

    @Query("""
        SELECT qb.* FROM quiz_bank qb
        LEFT JOIN entry_topic et ON et.entryId = qb.sourceEntryId
        WHERE qb.isActive = 1
        AND (:topicId IS NULL OR et.topicId = :topicId OR qb.topicId = :topicId)
        ORDER BY qb.createdAt DESC
        LIMIT :limit
    """)
    suspend fun getQuizzesByTopic(topicId: String?, limit: Int = 20): List<QuizBankEntity>

    @Query("""
        SELECT qb.* FROM quiz_bank qb
        WHERE qb.isActive = 1
        AND qb.quizType IN (:types)
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getRandomQuizzes(types: List<String>, limit: Int = 10): List<QuizBankEntity>

    @Query("""
        SELECT qb.* FROM quiz_bank qb
        WHERE qb.isActive = 1
        AND qb.id NOT IN (
            SELECT qa.quizId FROM quiz_attempts qa WHERE qa.isCorrect = 1
        )
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getUnmasteredQuizzes(limit: Int = 10): List<QuizBankEntity>

    @Query("""
        SELECT qb.* FROM quiz_bank qb
        WHERE qb.isActive = 1
        AND qb.id IN (
            SELECT qa.quizId FROM quiz_attempts qa
            WHERE qa.isCorrect = 0
            GROUP BY qa.quizId
            HAVING COUNT(*) >= 1
        )
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getWrongQuizzes(limit: Int = 10): List<QuizBankEntity>

    // ★v12.0追加: 弱点分析のtopicId対応
    @Query("""
        SELECT qb.* FROM quiz_bank qb
        LEFT JOIN entry_topic et ON et.entryId = qb.sourceEntryId
        WHERE qb.isActive = 1
        AND qb.id IN (SELECT qa.quizId FROM quiz_attempts qa WHERE qa.isCorrect = 0)
        AND (:topicId IS NULL OR :topicId = 'all' OR qb.topicId = :topicId OR et.topicId = :topicId)
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getWrongQuizzesByTopic(topicId: String?, limit: Int = 20): List<QuizBankEntity>

    @Query("SELECT * FROM QuizMasteryView WHERE quizId = :quizId")
    suspend fun getMastery(quizId: String): QuizMasteryView?

    @Query("SELECT * FROM QuizMasteryView")
    fun observeAllMastery(): Flow<List<QuizMasteryView>>

    @Query("SELECT COUNT(*) FROM quiz_bank WHERE isActive = 1")
    fun observeQuizCount(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM quiz_attempts
        WHERE attemptedAt >= :startOfDay
    """)
    fun observeAttemptsTodayCount(startOfDay: Long): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM quiz_attempts
        WHERE attemptedAt >= :startOfDay AND isCorrect = 1
    """)
    fun observeCorrectTodayCount(startOfDay: Long): Flow<Int>

    @Query("""
        SELECT qa.* FROM quiz_attempts qa
        WHERE qa.quizId = :quizId
        ORDER BY qa.attemptedAt DESC
        LIMIT 1
    """)
    suspend fun getLastAttempt(quizId: String): QuizAttemptEntity?
}
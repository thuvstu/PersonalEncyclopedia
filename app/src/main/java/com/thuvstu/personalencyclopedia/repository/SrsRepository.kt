package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.brain.srs.FsrsAlgorithm
import com.thuvstu.personalencyclopedia.brain.srs.Sm2Algorithm
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.SrsReviewDao
import com.thuvstu.personalencyclopedia.db.entity.SrsCurrentView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SrsRepository @Inject constructor(
    private val srsDao: SrsReviewDao,
    private val definitionDao: EntryDefinitionDao,
    private val settingsRepo: SettingsRepository        // ★追加
) {
    data class ReviewCard(
        val entryId: String,
        val term: String,
        val reading: String?,
        val definition: String,
        val field: String?,
        val currentState: SrsCurrentView?
    )

    suspend fun getDueCards(limit: Int = 30): List<ReviewCard> {
        val dueEntries = srsDao.getDueEntries(limit = limit)
        return dueEntries.mapNotNull { due ->
            val def = definitionDao.getByEntryId(due.id) ?: return@mapNotNull null
            val state = srsDao.getCurrentState(due.id)
            ReviewCard(
                entryId = due.id,
                term = def.term,
                reading = def.reading,
                definition = def.definition,
                field = def.field,
                currentState = state
            )
        }
    }

    suspend fun recordReview(entryId: String, grade: Int) {
        val current = srsDao.getCurrentState(entryId)
        val algorithm = settingsRepo.srsAlgorithm.first()   // ★ "SM2" or "FSRS"

        val review = if (algorithm == "FSRS") {
            val elapsedDays = current?.lastReviewedAt?.let {
                (System.currentTimeMillis() - it) / 86_400_000.0
            } ?: 0.0
            val prevDifficulty = current?.easeFactor?.takeIf { it in 1.0f..10.0f }
            val prevStability = current?.intervalDays?.toFloat()?.takeIf { it > 0f }
            FsrsAlgorithm.createReview(
                entryId = entryId,
                sm2Grade = grade,
                elapsedDays = elapsedDays,
                previousDifficulty = prevDifficulty,
                previousStability = prevStability
            )
        } else {
            Sm2Algorithm.createReview(
                entryId = entryId,
                grade = grade,
                previousInterval = current?.intervalDays ?: 0,
                previousEase = current?.easeFactor ?: 2.5f,
                repetitionCount = if (current != null && current.grade >= 2) {
                    when {
                        current.intervalDays <= 1 -> 1
                        current.intervalDays <= 6 -> 2
                        else -> 3
                    }
                } else 0
            )
        }
        srsDao.insert(review)
    }

    fun observeDueCount(): Flow<Int> = srsDao.observeDueCount()

    fun observeReviewedTodayCount(): Flow<Int> {
        val startOfDay = getStartOfDay()
        return srsDao.observeReviewedTodayCount(startOfDay)
    }

    private fun getStartOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
package com.thuvstu.personalencyclopedia.brain.quiz

import com.thuvstu.personalencyclopedia.db.dao.EraMasterDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和暦→西暦変換器(設計書§8.9)。
 * 変換ロジック: 元年の西暦 + (yearInEra - 1)。
 * 元号が era_master に存在しない場合は null を返す(判定不能)。
 */
@Singleton
class EraConverter @Inject constructor(
    private val eraMasterDao: EraMasterDao
) {
    suspend fun toWesternYear(eraName: String, yearInEra: Int): Int? {
        val era = eraMasterDao.getByName(eraName) ?: return null
        return era.startYear + (yearInEra - 1)
    }

    suspend fun getAll(): List<com.thuvstu.personalencyclopedia.db.entity.EraMasterEntity> =
        eraMasterDao.getAll()
}

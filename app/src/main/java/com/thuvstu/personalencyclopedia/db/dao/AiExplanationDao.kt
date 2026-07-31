package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.AiExplanationEntity

@Dao
interface AiExplanationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiExplanationEntity)

    @Query("SELECT * FROM ai_explanations WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun getCached(sourceType: String, sourceId: String): AiExplanationEntity?

    @Query("DELETE FROM ai_explanations WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun invalidate(sourceType: String, sourceId: String)
}
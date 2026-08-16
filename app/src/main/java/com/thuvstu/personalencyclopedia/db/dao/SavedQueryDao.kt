package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.thuvstu.personalencyclopedia.db.entity.SavedQueryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedQueryDao {

    @Insert
    suspend fun insert(query: SavedQueryEntity)

    @Query("SELECT * FROM saved_query ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedQueryEntity>>

    @Query("DELETE FROM saved_query WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM saved_query WHERE id = :id")
    suspend fun getById(id: String): SavedQueryEntity?
}

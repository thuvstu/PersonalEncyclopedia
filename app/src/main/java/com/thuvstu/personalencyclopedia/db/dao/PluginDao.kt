package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.PluginEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plugin: PluginEntity)

    @Query("SELECT * FROM plugins WHERE isActive = 1")
    fun observeAll(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins WHERE id = :id")
    suspend fun getById(id: String): PluginEntity?

    @Query("DELETE FROM plugins WHERE id = :id")
    suspend fun delete(id: String)
}
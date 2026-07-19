package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thuvstu.personalencyclopedia.db.entity.EntryTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryTypeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(types: List<EntryTypeEntity>)

    @Query("SELECT * FROM entry_type WHERE isActive = 1 ORDER BY sortOrder")
    fun observeAll(): Flow<List<EntryTypeEntity>>

    @Query("SELECT * FROM entry_type WHERE name = :name")
    suspend fun getByName(name: String): EntryTypeEntity?
}
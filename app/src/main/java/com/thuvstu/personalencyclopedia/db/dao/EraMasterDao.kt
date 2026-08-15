package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.thuvstu.personalencyclopedia.db.entity.EraMasterEntity

@Dao
interface EraMasterDao {

    @Query("SELECT * FROM era_master ORDER BY startYear DESC")
    suspend fun getAll(): List<EraMasterEntity>

    @Query("SELECT * FROM era_master WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): EraMasterEntity?
}

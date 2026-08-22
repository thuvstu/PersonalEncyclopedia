package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.thuvstu.personalencyclopedia.db.entity.EntryThoughtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryThoughtDao {
    @Insert
    suspend fun insert(thought: EntryThoughtEntity)

    /** Round 0 (M-1): SyntheticDataSeeder用の一括挿入 */
    @Insert
    suspend fun insertAll(thoughts: List<EntryThoughtEntity>)

    @Update
    suspend fun update(thought: EntryThoughtEntity)

    @Query("SELECT * FROM entry_thought WHERE entryId = :entryId")
    suspend fun getByEntryId(entryId: String): EntryThoughtEntity?

    @Query("SELECT * FROM entry_thought WHERE entryId = :entryId")
    fun observeByEntryId(entryId: String): Flow<EntryThoughtEntity?>
}
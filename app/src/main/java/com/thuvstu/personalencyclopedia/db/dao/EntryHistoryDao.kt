package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.thuvstu.personalencyclopedia.db.entity.EntryHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryHistoryDao {

    @Insert
    suspend fun insert(history: EntryHistoryEntity)

    /** エントリー別に新しい順で履歴を返す（§11.13 編集履歴UI）。 */
    @Query("SELECT * FROM entry_history WHERE entryId = :entryId ORDER BY recordedAt DESC")
    fun observeByEntryId(entryId: String): Flow<List<EntryHistoryEntity>>

    @Query("SELECT * FROM entry_history WHERE entryId = :entryId ORDER BY recordedAt DESC LIMIT 1")
    suspend fun getLatest(entryId: String): EntryHistoryEntity?
}

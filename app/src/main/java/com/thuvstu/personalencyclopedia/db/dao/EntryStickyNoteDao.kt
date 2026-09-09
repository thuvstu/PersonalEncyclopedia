package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.thuvstu.personalencyclopedia.db.entity.EntryStickyNoteEntity
import kotlinx.coroutines.flow.Flow

/** 付箋（wt56）: エントリごとの一言メモ。 */
@Dao
interface EntryStickyNoteDao {

    @Insert
    suspend fun insert(note: EntryStickyNoteEntity)

    @Insert
    suspend fun insertAll(notes: List<EntryStickyNoteEntity>)

    @Update
    suspend fun update(note: EntryStickyNoteEntity)

    @Query("SELECT * FROM entry_sticky_note WHERE id = :id")
    suspend fun getById(id: String): EntryStickyNoteEntity?

    @Query("DELETE FROM entry_sticky_note WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM entry_sticky_note WHERE entryId = :entryId")
    suspend fun deleteByEntryId(entryId: String)

    /** 表示順: ピン → 未解決 → sortOrder → createdAt */
    @Query(
        "SELECT * FROM entry_sticky_note WHERE entryId = :entryId " +
            "ORDER BY isPinned DESC, isResolved ASC, sortOrder ASC, createdAt ASC"
    )
    fun observeByEntryId(entryId: String): Flow<List<EntryStickyNoteEntity>>

    @Query(
        "SELECT * FROM entry_sticky_note WHERE entryId = :entryId " +
            "ORDER BY isPinned DESC, isResolved ASC, sortOrder ASC, createdAt ASC"
    )
    suspend fun getByEntryId(entryId: String): List<EntryStickyNoteEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM entry_sticky_note WHERE entryId = :entryId")
    suspend fun nextSortOrder(entryId: String): Int

    /**
     * 一覧カード用: 全付箋を監視（カード側で entryId ごとにグルーピングする）。
     * 一覧の各カードごとにFlowを張るより購読が1本で済む。
     */
    @Query("SELECT * FROM entry_sticky_note ORDER BY isPinned DESC, isResolved ASC, sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<EntryStickyNoteEntity>>

    /** ダッシュボード「最近の付箋」フィード（未解決のみ・新しい順） */
    // ★wt58: 種付箋(source='seed')は「自分の未解決」ではないのでフィード/件数から除外（カード上には表示される）
    @Query("SELECT * FROM entry_sticky_note WHERE isResolved = 0 AND source != 'seed' ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentUnresolved(limit: Int): Flow<List<EntryStickyNoteEntity>>

    @Query("SELECT COUNT(*) FROM entry_sticky_note WHERE isResolved = 0 AND source != 'seed'")
    fun observeUnresolvedCount(): Flow<Int>

    /** 付箋付きエントリのID集合（検索の「付箋あり」絞り込み用） */
    @Query("SELECT DISTINCT entryId FROM entry_sticky_note")
    fun observeEntryIdsWithNotes(): Flow<List<String>>

    /** 付箋本文の部分一致検索（FTSに載る前の即時フォールバック兼、付箋だけを探したい時用） */
    @Query(
        "SELECT * FROM entry_sticky_note WHERE text LIKE '%' || :q || '%' " +
            "ORDER BY isResolved ASC, createdAt DESC LIMIT :limit"
    )
    suspend fun searchText(q: String, limit: Int): List<EntryStickyNoteEntity>

    @Query("UPDATE entry_sticky_note SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE entry_sticky_note SET isResolved = :resolved, resolvedAt = :resolvedAt, updatedAt = :now WHERE id = :id")
    suspend fun setResolved(id: String, resolved: Boolean, resolvedAt: Long?, now: Long)

    @Query("UPDATE entry_sticky_note SET sortOrder = :sortOrder, updatedAt = :now WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int, now: Long)

    @Query("UPDATE entry_sticky_note SET promotedEntryId = :promotedEntryId, updatedAt = :now WHERE id = :id")
    suspend fun setPromotedEntry(id: String, promotedEntryId: String, now: Long)

    @Query("UPDATE entry_sticky_note SET promotedTaskId = :promotedTaskId, updatedAt = :now WHERE id = :id")
    suspend fun setPromotedTask(id: String, promotedTaskId: String, now: Long)

    @Query("UPDATE entry_sticky_note SET promotedCandidateId = :promotedCandidateId, updatedAt = :now WHERE id = :id")
    suspend fun setPromotedCandidate(id: String, promotedCandidateId: String, now: Long)

    /** 検索文書の鮮度判定用: このentryの付箋の最終更新時刻（付箋なしならnull） */
    @Query("SELECT MAX(updatedAt) FROM entry_sticky_note WHERE entryId = :entryId")
    suspend fun latestUpdatedAt(entryId: String): Long?

    /** バックアップ/エクスポート用 */
    @Query("SELECT * FROM entry_sticky_note ORDER BY createdAt ASC")
    suspend fun getAll(): List<EntryStickyNoteEntity>
}

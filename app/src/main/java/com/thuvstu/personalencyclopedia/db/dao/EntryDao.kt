package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert
    suspend fun insert(entry: EntryEntity)

    /** Round 0 (M-1): SyntheticDataSeeder用の一括挿入 */
    @Insert
    suspend fun insertAll(entries: List<EntryEntity>)

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("UPDATE entry SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE entry SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE entry SET isFavorite = :fav, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE entry SET accessedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun getById(id: String): EntryEntity?

    @Query("SELECT * FROM entry WHERE id = :id")
    fun observeById(id: String): Flow<EntryEntity?>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeAll(limit: Int = 50, offset: Int = 0): Flow<List<EntryEntity>>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL AND type = :type
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeByType(type: String, limit: Int = 50, offset: Int = 0): Flow<List<EntryEntity>>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL
          AND (title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%')
        ORDER BY
          CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END,
          createdAt DESC
        LIMIT :limit
    """)
    fun search(q: String, limit: Int = 50): Flow<List<EntryEntity>>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL AND isFavorite = 1
        ORDER BY createdAt DESC
    """)
    fun observeFavorites(): Flow<List<EntryEntity>>

    @Query("SELECT COUNT(*) FROM entry WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    /** ★wt44: 自動リンク索引の鮮度判定用。件数と最終更新時刻が変わっていなければ再構築不要 */
    @Query("SELECT COUNT(*) || '-' || IFNULL(MAX(updatedAt), 0) FROM entry WHERE deletedAt IS NULL")
    suspend fun linkerFingerprint(): String

    /** ★wt44: 自動リンク索引の構築用(タイトルは全件必要。本文は読まない) */
    @Query("SELECT id, title FROM entry WHERE deletedAt IS NULL")
    suspend fun getAllTitles(): List<EntryTitle>

    /**
     * ★wt46: 自動リンクの表記揺れ用に、タイトル以外の表層形を (id, 表層形) の射影で返す。
     * 定義の読み・人物の正式名/別名JSON・組織の正式名称・場所名・出来事名。
     * 人物の `aliasesJson` はJSON配列文字列のまま返し、`AutoLinkerProvider` 側で分解する。
     * 空文字・タイトルと同一の値はここでは除外しない(Provider/AutoLinker側で捨てる)。
     */
    @Query("""
        SELECT e.id AS id, d.reading AS title FROM entry e INNER JOIN entry_definition d ON d.entryId = e.id
            WHERE e.deletedAt IS NULL AND d.reading IS NOT NULL AND d.reading != ''
        UNION ALL
        SELECT e.id, p.fullName FROM entry e INNER JOIN entry_person p ON p.entryId = e.id
            WHERE e.deletedAt IS NULL AND p.fullName != ''
        UNION ALL
        SELECT e.id, p.aliasesJson FROM entry e INNER JOIN entry_person p ON p.entryId = e.id
            WHERE e.deletedAt IS NULL AND p.aliasesJson != '[]'
        UNION ALL
        SELECT e.id, o.officialName FROM entry e INNER JOIN entry_org o ON o.entryId = e.id
            WHERE e.deletedAt IS NULL AND o.officialName != ''
        UNION ALL
        SELECT e.id, pl.placeName FROM entry e INNER JOIN entry_place pl ON pl.entryId = e.id
            WHERE e.deletedAt IS NULL AND pl.placeName != ''
        UNION ALL
        SELECT e.id, ev.eventName FROM entry e INNER JOIN entry_event ev ON ev.entryId = e.id
            WHERE e.deletedAt IS NULL AND ev.eventName != ''
    """)
    suspend fun getAllAliasForms(): List<EntryTitle>

    @Query("SELECT COUNT(*) FROM entry WHERE deletedAt IS NULL AND type = :type")
    fun observeCountByType(type: String): Flow<Int>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int = 10): Flow<List<EntryEntity>>

    @Query("""
SELECT type, COUNT(*) AS cnt FROM entry
WHERE deletedAt IS NULL GROUP BY type ORDER BY cnt DESC
""")

    fun observeCountsByType(): Flow<List<TypeCount>>

    @Query("SELECT * FROM entry WHERE deletedAt IS NULL ORDER BY createdAt LIMIT :limit OFFSET :offset")
    suspend fun getAllPaged(limit: Int, offset: Int): List<EntryEntity>

    @Query("SELECT * FROM entry WHERE title = :title AND deletedAt IS NULL LIMIT 1")
    suspend fun findByTitle(title: String): EntryEntity?

    @Query("SELECT * FROM entry WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<EntryEntity>

    @Query("SELECT * FROM entry WHERE sourceUrl = :url AND deletedAt IS NULL LIMIT 1")
    suspend fun findBySourceUrl(url: String): EntryEntity?

    /** Round 0 (M-1): 全件数（deletedAtを含む）。シーダーの進捗・検証用 */
    @Query("SELECT COUNT(*) FROM entry")
    suspend fun countAll(): Int

    /** Round 0 (M-1): 合成データ件数。再投入時に置き換え判定に使う */
    @Query("SELECT COUNT(*) FROM entry WHERE metadataJson LIKE '%\"synthetic\":true%'")
    suspend fun countSynthetic(): Int

    /** Round 0 (M-1): SyntheticDataSeeder生成データの一括削除。子テーブルはFK CASCADEで削除される */
    @Query("DELETE FROM entry WHERE metadataJson LIKE '%\"synthetic\":true%'")
    suspend fun deleteSynthetic()
}

data class TypeCount(val type: String, val cnt: Int)

/** ★wt44: 自動リンク索引用の軽量射影 */
data class EntryTitle(val id: String, val title: String)

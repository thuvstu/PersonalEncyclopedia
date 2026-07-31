package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.*

@Dao
interface EntryExtensionDao {

    // ── Webpage ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebpage(entity: EntryWebpageEntity)

    @Query("SELECT * FROM entry_webpage WHERE entryId = :entryId")
    suspend fun getWebpage(entryId: String): EntryWebpageEntity?

    // ── Book ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(entity: EntryBookEntity)

    @Query("SELECT * FROM entry_book WHERE entryId = :entryId")
    suspend fun getBook(entryId: String): EntryBookEntity?

    // ── Video ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(entity: EntryVideoEntity)

    @Query("SELECT * FROM entry_video WHERE entryId = :entryId")
    suspend fun getVideo(entryId: String): EntryVideoEntity?

    // ── Document ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(entity: EntryDocumentEntity)

    @Query("SELECT * FROM entry_document WHERE entryId = :entryId")
    suspend fun getDocument(entryId: String): EntryDocumentEntity?

    // ── Media ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(entity: EntryMediaEntity)

    @Query("SELECT * FROM entry_media WHERE entryId = :entryId")
    suspend fun getMedia(entryId: String): EntryMediaEntity?

    // ── Person ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(entity: EntryPersonEntity)

    @Query("SELECT * FROM entry_person WHERE entryId = :entryId")
    suspend fun getPerson(entryId: String): EntryPersonEntity?

    // ── Org ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrg(entity: EntryOrgEntity)

    @Query("SELECT * FROM entry_org WHERE entryId = :entryId")
    suspend fun getOrg(entryId: String): EntryOrgEntity?

    // ── Place ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(entity: EntryPlaceEntity)

    @Query("SELECT * FROM entry_place WHERE entryId = :entryId")
    suspend fun getPlace(entryId: String): EntryPlaceEntity?

    // ── Event ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(entity: EntryEventEntity)

    @Query("SELECT * FROM entry_event WHERE entryId = :entryId")
    suspend fun getEvent(entryId: String): EntryEventEntity?

    // ── Liked ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiked(entity: EntryLikedEntity)

    @Query("SELECT * FROM entry_liked WHERE entryId = :entryId")
    suspend fun getLiked(entryId: String): EntryLikedEntity?

    // ── AI Conversation ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiConv(entity: EntryAiConvEntity)

    @Query("SELECT * FROM entry_ai_conv WHERE entryId = :entryId")
    suspend fun getAiConv(entryId: String): EntryAiConvEntity?
}
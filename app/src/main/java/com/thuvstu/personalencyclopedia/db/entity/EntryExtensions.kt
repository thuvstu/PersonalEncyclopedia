package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_webpage",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryWebpageEntity(
    @PrimaryKey val entryId: String,
    val url: String,
    val domain: String,
    val scrapedAt: Long? = null,
    val fullText: String? = null,
    val thumbnailPath: String? = null,
    val readingTimeS: Int? = null,
    val author: String? = null,
    val publishedAt: Long? = null,
    val scraperUsed: String? = null
)

@Entity(
    tableName = "entry_book",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryBookEntity(
    @PrimaryKey val entryId: String,
    val isbn: String? = null,
    val authorsJson: String = "[]",
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val totalPages: Int? = null,
    val readStatus: String = "unread",
    val readStartDate: Long? = null,
    val readEndDate: Long? = null,
    val rating: Int? = null,
    val coverPath: String? = null
)

@Entity(
    tableName = "entry_video",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryVideoEntity(
    @PrimaryKey val entryId: String,
    val platform: String,
    val videoId: String? = null,
    val channelName: String? = null,
    val durationS: Int? = null,
    val thumbnailUrl: String? = null,
    val transcript: String? = null,
    val watchedAt: Long? = null,
    val watchProgress: Float? = null
)

@Entity(
    tableName = "entry_document",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryDocumentEntity(
    @PrimaryKey val entryId: String,
    val docType: String,
    val blobPath: String? = null,
    val gdriveId: String? = null,
    val mimeType: String,
    val fileSizeBytes: Long? = null,
    val pageCount: Int? = null,
    val extractedText: String? = null,
    val extractionMethod: String? = null
)

@Entity(
    tableName = "entry_media",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryMediaEntity(
    @PrimaryKey val entryId: String,
    val mediaType: String,
    val blobPath: String,
    val mimeType: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val durationS: Float? = null,
    val ocrText: String? = null,
    val caption: String? = null
)

@Entity(
    tableName = "entry_person",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryPersonEntity(
    @PrimaryKey val entryId: String,
    val fullName: String,
    val aliasesJson: String = "[]",
    val birthYear: Int? = null,
    val deathYear: Int? = null,
    val nationality: String? = null,
    val occupationsJson: String = "[]",
    val biography: String? = null,
    val photoPath: String? = null
)

@Entity(
    tableName = "entry_org",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryOrgEntity(
    @PrimaryKey val entryId: String,
    val officialName: String,
    val orgType: String? = null,
    val foundedYear: Int? = null,
    val country: String? = null,
    val websiteUrl: String? = null,
    val description: String? = null
)

@Entity(
    tableName = "entry_place",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryPlaceEntity(
    @PrimaryKey val entryId: String,
    val placeName: String,
    val placeType: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val visitedDatesJson: String = "[]"
)

@Entity(
    tableName = "entry_event",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryEventEntity(
    @PrimaryKey val entryId: String,
    val eventName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val locationText: String? = null,
    val placeEntryId: String? = null,
    val isPersonal: Boolean = true,
    val participantsJson: String = "[]"
)

@Entity(
    tableName = "entry_liked",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryLikedEntity(
    @PrimaryKey val entryId: String,
    val platform: String,
    val originalId: String,
    val likedAt: Long? = null,
    val contentType: String,
    val authorName: String? = null,
    val fullText: String? = null
)

@Entity(
    tableName = "entry_ai_conv",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryAiConvEntity(
    @PrimaryKey val entryId: String,
    val model: String,
    val provider: String,
    val messagesJson: String = "[]",
    val tokenCount: Int? = null,
    val topic: String? = null,
    val isUseful: Boolean? = null
)
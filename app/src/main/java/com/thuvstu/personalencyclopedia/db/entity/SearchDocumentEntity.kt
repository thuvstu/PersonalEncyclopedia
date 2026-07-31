package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_document",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class SearchDocumentEntity(
    @PrimaryKey val entryId: String,
    val combinedText: String,
    val lang: String = "ja",
    val updatedAt: Long = System.currentTimeMillis()
)

@Fts4
@Entity(tableName = "search_document_fts")
data class SearchDocumentFtsEntity(
    val ftsContent: String
)
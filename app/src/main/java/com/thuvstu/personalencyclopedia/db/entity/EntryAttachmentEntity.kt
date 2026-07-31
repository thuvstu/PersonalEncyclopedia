package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "entry_attachment",
    indices = [Index("entryId")],
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EntryAttachmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val blobPath: String,          // filesDir/blobs/attachments/{entryId}/...（§6.1 BLOB方針準拠）
    val mimeType: String,
    val caption: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
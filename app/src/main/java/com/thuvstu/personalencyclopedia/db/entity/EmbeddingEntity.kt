package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "embedding",
    indices = [Index("entryId", unique = true)],
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)]
)
data class EmbeddingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val vectorBlob: ByteArray,
    val model: String = "gemini-embedding-2-preview",
    val inputText: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingEntity) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

@Entity(tableName = "embedding_job")
data class EmbeddingJobEntity(
    @PrimaryKey val entryId: String,
    val status: String = "queued",   // queued/running/done/failed
    val attempts: Int = 0,
    val error: String? = null,
    val queuedAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null
)
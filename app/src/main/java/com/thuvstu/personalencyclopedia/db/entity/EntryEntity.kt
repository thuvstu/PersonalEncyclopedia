package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "entry",
    indices = [
        Index("type"),
        Index("createdAt"),
        Index("accessedAt"),
        Index("isFavorite"),
        Index("isMuted"),
        Index("deletedAt")
    ]
)
data class EntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String,
    val title: String,
    val content: String? = null,
    val summary: String? = null,
    val sourceUrl: String? = null,
    val lang: String? = null,
    val isFavorite: Boolean = false,
    val isMuted: Boolean = false,
    val accessedAt: Long? = null,
    val deletedAt: Long? = null,
    val metadataJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
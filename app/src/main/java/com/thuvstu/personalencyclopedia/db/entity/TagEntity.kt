package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "tag",
    indices = [Index("name", unique = true)]
)
data class TagEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String? = null
)
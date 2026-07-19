package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_thought",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EntryThoughtEntity(
    @PrimaryKey val entryId: String,
    val mood: String? = null,
    val context: String? = null,
    val isDraft: Boolean = false
)
package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_definition",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EntryDefinitionEntity(
    @PrimaryKey val entryId: String,
    val term: String,
    val reading: String? = null,
    val definition: String,
    val field: String? = null,
    val examplesJson: String = "[]",
    val relatedTermsJson: String = "[]"
)
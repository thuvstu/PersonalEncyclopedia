package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "entry_tag",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class EntryTagEntity(
    val entryId: String,
    val tagId: String
)
package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "topic",
    indices = [Index("name"), Index("parentId")]
)
data class TopicEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,   // null = ジャンル(最上位), 非null = 分野
    val description: String? = null,
    val colorHex: String? = null
)

@Entity(
    tableName = "entry_topic",
    primaryKeys = ["entryId", "topicId"],
    foreignKeys = [
        ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TopicEntity::class, ["id"], ["topicId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("topicId")]
)
data class EntryTopicEntity(
    val entryId: String,
    val topicId: String
)
package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "whiteboard_node", indices = [Index("entryId", unique = true)])
data class WhiteboardNodeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val x: Float,
    val y: Float,
    val createdAt: Long = System.currentTimeMillis()
)
package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * ホワイトボード = 百科事典の「巻」/ 思考空間（§5.8 Heptabase型）。複数持てる。
 */
@Entity(tableName = "whiteboard")
data class WhiteboardEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val summary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 型のない自由記述カード（Heptabaseの"card"）。13型に属さない。
 */
@Entity(tableName = "whiteboard_note")
data class WhiteboardNoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val contentMd: String,   // Markdown + KaTeX + [[wiki-link]] 自由記述
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * カードの「配置」。entry(型付き) か note(型なし) のいずれか一方を参照（排他）。
 * 同じカードを複数ボードに置ける。
 */
@Entity(
    tableName = "whiteboard_node",
    indices = [Index("boardId"), Index("entryId"), Index("noteId"), Index("sectionId")]
)
data class WhiteboardNodeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val boardId: String,
    val entryId: String? = null,
    val noteId: String? = null,
    val sectionId: String? = null,
    val x: Float,
    val y: Float,
    val width: Float = 240f,
    val height: Float = 120f,
    val zIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * セクション = カードを囲む枠（Heptabaseの"section"）。百科では「章」。
 */
@Entity(tableName = "whiteboard_section", indices = [Index("boardId")])
data class WhiteboardSectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val boardId: String,
    val title: String,
    val x: Float,
    val y: Float,
    val width: Float = 600f,
    val height: Float = 400f,
    val colorHex: String? = null,
    val zIndex: Int = -1,
    val createdAt: Long = System.currentTimeMillis()
)
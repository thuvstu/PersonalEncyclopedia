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

/**
 * ★P3-1: ノード間の接続線（Heptabaseの"edge"）。v11で追加。
 * ボード固有の「見た目の線」であり、承認制の `connection` テーブルとは独立
 * （エッジを張っても connection には自動書込しない = AGENTS.md 骨格を維持）。
 * 参考: GachiPKM `whiteboard_edges`(sourceNodeId/targetNodeId/label)。向きは持たない。
 */
@Entity(
    tableName = "whiteboard_edge",
    indices = [Index("boardId"), Index("sourceNodeId"), Index("targetNodeId")]
)
data class WhiteboardEdgeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val boardId: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val label: String? = null,
    val colorHex: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
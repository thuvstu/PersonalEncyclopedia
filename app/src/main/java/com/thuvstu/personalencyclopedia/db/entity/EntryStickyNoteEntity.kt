package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 付箋（wt56）。任意のエントリ(カード)に「思ったこと」を何枚でも貼れる軽量メモ。
 *
 * - entry_thought は「思考型エントリの拡張(1:1)」であり用途が異なるため別テーブル。
 * - 本文(entry.content)を汚さずに一言感想・疑問・TODOを残すのが目的。
 * - entry_custom_field と同じく外部キーは張らない（entryは論理削除が基本のため）。
 * - ライフサイクル: 貼る → (ピン留め/並べ替え) → 解決(✓) → 削除、または昇格
 *   (思考エントリ化 / タスク化 / 接続候補化)。昇格先IDは promotedTo* に残し二重昇格を防ぐ。
 * - 検索: 付箋本文は entry の search_document(combinedText) に連結され FTS/意味検索に載る。
 */
@Entity(
    tableName = "entry_sticky_note",
    indices = [Index("entryId"), Index("createdAt"), Index("isResolved")]
)
data class EntryStickyNoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val text: String,
    /** 付箋の色。"yellow" / "pink" / "blue" / "green" のいずれか（UI側で色に変換） */
    val color: String = "yellow",
    /** どこで貼ったか: detail / list / srs / quiz / whiteboard / wiki / api */
    val source: String = "detail",
    /** 貼った文脈の補助ID（quizId / boardId / wikiArticleId など。無ければnull） */
    val contextId: String? = null,
    val isPinned: Boolean = false,
    /** 解決済み(✓)。疑問が解けた・確認した付箋は消さずに畳んで残す */
    val isResolved: Boolean = false,
    val resolvedAt: Long? = null,
    /** 手動並べ替え。小さいほど上。既定は createdAt と同じ順になるよう挿入時に採番 */
    val sortOrder: Int = 0,
    /** 昇格先。思考エントリ化 → entryId、タスク化 → taskId、接続候補化 → candidateId */
    val promotedEntryId: String? = null,
    val promotedTaskId: String? = null,
    val promotedCandidateId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

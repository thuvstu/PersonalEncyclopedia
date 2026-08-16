package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * タスク管理（設計書§5.9.1 / §8.10 パーキンソンの法則対抗）。
 * entry とは独立させ（タスクは知識ではなく行動の管理対象）、
 * 必要に応じて linkedEntryId / linkedTopicId で知識側と紐付ける。
 */
@Entity(
    tableName = "task",
    indices = [Index("status"), Index("deadlineAt")]
)
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val estimatedMinutes: Int,          // 見積もり時間。入力必須（§8.10の核）
    val deadlineAt: Long,               // ソフトではなくハードな締切
    val status: String = "pending",     // pending/in_progress/done/failed/abandoned
    val postponeCount: Int = 0,         // 先延ばし回数。§8.10で上限を課す
    val linkedEntryId: String? = null,  // 関連する知識entry（任意）
    val linkedTopicId: String? = null,  // StudyPlus送信時、コメント欄に載せる科目名の元（§7.8）
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

/**
 * 見積もりと実績の乖離を記録する（パーキンソンの法則を本人に自覚させる核心データ）。
 */
@Entity(
    tableName = "task_time_log",
    indices = [Index("taskId")]
)
data class TaskTimeLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val startedAt: Long,
    val endedAt: Long?,                 // null = 進行中
    val studyPlusSynced: Boolean = false // §7.8連携の重複送信防止フラグ
)

/**
 * 編集履歴（設計書§5.9.2、SQLiPKMのNoteHistoryEntityをentry統一型向けに一般化）。
 * v15.0では entry.content（ユーザー注釈）のみをスナップショット対象とする
 * （型固有拡張フィールドの履歴化は将来拡張候補）。
 */
@Entity(
    tableName = "entry_history",
    indices = [Index("entryId"), Index("recordedAt")]
)
data class EntryHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val recordedAt: Long = System.currentTimeMillis(),
    val titleSnapshot: String,
    val contentSnapshot: String?,       // entry.contentのスナップショット
    val changeSummary: String = "",     // AI生成 or 空欄可
    val charCountDelta: Int = 0
)

/**
 * SQL Explorer の保存済みクエリ（設計書§11.12、SQLiPKMのSavedQueryEntityを踏襲）。
 */
@Entity(tableName = "saved_query")
data class SavedQueryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sql: String,
    val createdAt: Long = System.currentTimeMillis()
)

package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.thuvstu.personalencyclopedia.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/** 完了タスクの見積もり vs 実績（§8.10.1 乖離集計用）。Roomはネスト型を直接マッピングできないため平坦化。 */
data class TaskWithActualMinutes(
    val id: String,
    val title: String,
    val description: String?,
    val estimatedMinutes: Int,
    val deadlineAt: Long,
    val status: String,
    val postponeCount: Int,
    val linkedEntryId: String?,
    val linkedTopicId: String?,
    val createdAt: Long,
    val completedAt: Long?,
    val actualMinutes: Long
)

@Dao
interface TaskDao {

    @Insert
    suspend fun insert(task: TaskEntity)

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM task WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM task ORDER BY deadlineAt ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE status = :status ORDER BY deadlineAt ASC")
    fun observeByStatus(status: String): Flow<List<TaskEntity>>

    @Query(
        "UPDATE task SET status = :status, completedAt = :completedAt WHERE id = :id"
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        completedAt: Long? = null
    )

    @Query(
        "UPDATE task SET deadlineAt = :newDeadlineAt, postponeCount = :newCount, " +
            "status = 'pending' WHERE id = :id"
    )
    suspend fun updatePostpone(id: String, newDeadlineAt: Long, newCount: Int)

    @Query("SELECT COUNT(*) FROM task WHERE status IN ('pending', 'in_progress')")
    fun observeActiveCount(): Flow<Int>

    /** 完了タスク（time_logを持つもの）を見積もり乖離集計用に取得。 */
    @Query(
        """
        SELECT t.id, t.title, t.description, t.estimatedMinutes, t.deadlineAt, t.status,
               t.postponeCount, t.linkedEntryId, t.linkedTopicId, t.createdAt, t.completedAt,
               tl.actualMinutes
        FROM task t
        INNER JOIN (
            SELECT taskId, SUM(COALESCE(endedAt - startedAt, 0)) / 60000 AS actualMinutes
            FROM task_time_log
            WHERE endedAt IS NOT NULL
            GROUP BY taskId
        ) tl ON tl.taskId = t.id
        WHERE t.status = 'done'
        ORDER BY t.completedAt DESC
        """
    )
    suspend fun getCompletedWithActualMinutes(): List<TaskWithActualMinutes>

    @Query("SELECT COUNT(*) FROM task WHERE status = 'done'")
    suspend fun getDoneCount(): Int
}

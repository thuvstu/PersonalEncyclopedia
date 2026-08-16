package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.thuvstu.personalencyclopedia.db.entity.TaskTimeLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskTimeLogDao {

    @Insert
    suspend fun insert(log: TaskTimeLogEntity)

    /** 進行中のタイムログ（endedAt IS NULL）を取得。 */
    @Query("SELECT * FROM task_time_log WHERE taskId = :taskId AND endedAt IS NULL LIMIT 1")
    suspend fun getOpenLogFor(taskId: String): TaskTimeLogEntity?

    @Query("SELECT * FROM task_time_log WHERE taskId = :taskId AND endedAt IS NULL LIMIT 1")
    fun observeOpenLogFor(taskId: String): Flow<TaskTimeLogEntity?>

    @Query("UPDATE task_time_log SET endedAt = :endedAt WHERE id = :id")
    suspend fun close(id: String, endedAt: Long)

    /** StudyPlus未同期（§7.8）のタイムログ一覧。endedAt確定済みのもののみ対象。 */
    @Query(
        "SELECT * FROM task_time_log WHERE studyPlusSynced = 0 AND endedAt IS NOT NULL " +
            "ORDER BY endedAt ASC"
    )
    suspend fun getPendingSyncLogs(): List<TaskTimeLogEntity>

    @Query("SELECT COUNT(*) FROM task_time_log WHERE studyPlusSynced = 0 AND endedAt IS NOT NULL")
    fun observePendingSyncCount(): Flow<Int>

    @Query("UPDATE task_time_log SET studyPlusSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}

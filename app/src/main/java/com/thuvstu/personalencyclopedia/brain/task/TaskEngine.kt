package com.thuvstu.personalencyclopedia.brain.task

import com.thuvstu.personalencyclopedia.db.dao.ProgressEventDao
import com.thuvstu.personalencyclopedia.db.dao.TaskDao
import com.thuvstu.personalencyclopedia.db.dao.TaskTimeLogDao
import com.thuvstu.personalencyclopedia.db.entity.ProgressEventEntity
import com.thuvstu.personalencyclopedia.db.entity.TaskEntity
import com.thuvstu.personalencyclopedia.db.entity.TaskTimeLogEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * タスクエンジン（設計書§8.10、パーキンソンの法則対抗）。
 *
 * 2本柱:
 * 1. 見積もりの自己申告を記録し続け、実績との乖離を可視化する（§8.10.1）
 * 2. 先延ばしを無限に握りつぶせなくする強制対峙（§8.10.2）
 */
@Singleton
class TaskEngine @Inject constructor(
    private val taskDao: TaskDao,
    private val timeLogDao: TaskTimeLogDao,
    private val progressEventDao: ProgressEventDao
) {
    companion object {
        /** 静かな先延ばしの上限。これを超えると強制選択を要求する（§8.10.2）。 */
        const val MAX_SILENT_POSTPONES = 3
        const val STATUS_PENDING = "pending"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
        const val STATUS_ABANDONED = "abandoned"
    }

    // ── §8.10.1 見積もり記録と乖離の可視化 ──

    suspend fun startTask(taskId: String) {
        timeLogDao.insert(
            TaskTimeLogEntity(taskId = taskId, startedAt = System.currentTimeMillis(), endedAt = null)
        )
        taskDao.updateStatus(taskId, STATUS_IN_PROGRESS)
        progressEventDao.insert(
            ProgressEventEntity(
                entityType = "task", entityId = taskId, eventType = "started"
            )
        )
    }

    /**
     * 完了処理。閉じたタイムログを返す（§7.8 StudyPlus同期の呼び出し元が使用）。
     */
    suspend fun completeTask(taskId: String): TaskTimeLogEntity? {
        val log = timeLogDao.getOpenLogFor(taskId) ?: return null
        val endedAt = System.currentTimeMillis()
        timeLogDao.close(log.id, endedAt)
        taskDao.updateStatus(taskId, STATUS_DONE, completedAt = endedAt)
        progressEventDao.insert(
            ProgressEventEntity(
                entityType = "task", entityId = taskId, eventType = "completed"
            )
        )
        return log.copy(endedAt = endedAt)
    }

    suspend fun abandonTask(taskId: String) {
        timeLogDao.getOpenLogFor(taskId)?.let {
            timeLogDao.close(it.id, endedAt = System.currentTimeMillis())
        }
        taskDao.updateStatus(taskId, STATUS_ABANDONED)
        progressEventDao.insert(
            ProgressEventEntity(
                entityType = "task", entityId = taskId, eventType = "abandoned"
            )
        )
    }

    suspend fun failTask(taskId: String) {
        timeLogDao.getOpenLogFor(taskId)?.let {
            timeLogDao.close(it.id, endedAt = System.currentTimeMillis())
        }
        taskDao.updateStatus(taskId, STATUS_FAILED)
        progressEventDao.insert(
            ProgressEventEntity(
                entityType = "task", entityId = taskId, eventType = "failed"
            )
        )
    }

    /**
     * 見積もりと実績の乖離を集計（§8.10.1）。
     * ダッシュボードで「あなたは見積もりを平均◯倍過小評価しています」と表示する。
     */
    suspend fun estimationBiasReport(): EstimationBias {
        val completed = taskDao.getCompletedWithActualMinutes()
        if (completed.isEmpty()) return EstimationBias(averageRatio = null, sampleSize = 0)
        val ratios = completed.map {
            (it.actualMinutes.toDouble() / it.estimatedMinutes).coerceAtLeast(0.01)
        }
        return EstimationBias(
            averageRatio = ratios.average(),
            sampleSize = ratios.size
        )
    }

    // ── §8.10.2 先延ばし上限の強制対峙（核心ロジック）──

    suspend fun postponeTask(taskId: String, newDeadlineAt: Long): PostponeResult {
        val task = taskDao.getById(taskId) ?: return PostponeResult.NotFound
        val newCount = task.postponeCount + 1
        return if (newCount > MAX_SILENT_POSTPONES) {
            // 静かな先延ばしを許さず、呼び出し元（UI）に強制選択を要求する
            PostponeResult.RequireForcedChoice(taskId)
        } else {
            taskDao.updatePostpone(taskId, newDeadlineAt, newCount)
            progressEventDao.insert(
                ProgressEventEntity(
                    entityType = "task", entityId = taskId, eventType = "postponed"
                )
            )
            PostponeResult.Postponed(newCount)
        }
    }

    /** 強制選択で「今日中に終わらせる」を選んだ場合：締切を今日の23:59:59に固定。 */
    suspend fun forceFinishToday(taskId: String) {
        val task = taskDao.getById(taskId) ?: return
        val endOfToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        taskDao.updatePostpone(taskId, endOfToday, task.postponeCount + 1)
    }
}

/** §8.10.1: 見積もり乖離レポート。averageRatio = 実績/見積もり（1.0=正確、>1=過小評価）。 */
data class EstimationBias(
    val averageRatio: Double?,
    val sampleSize: Int
)

sealed class PostponeResult {
    object NotFound : PostponeResult()
    data class Postponed(val count: Int) : PostponeResult()
    data class RequireForcedChoice(val taskId: String) : PostponeResult()
}

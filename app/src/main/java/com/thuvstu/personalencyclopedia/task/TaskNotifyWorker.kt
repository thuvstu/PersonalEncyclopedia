package com.thuvstu.personalencyclopedia.task

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.thuvstu.personalencyclopedia.brain.task.TaskEngine
import com.thuvstu.personalencyclopedia.db.dao.TaskDao
import com.thuvstu.personalencyclopedia.db.dao.TaskTimeLogDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * ★通知系: ToDo期限・タイムボックス終了の通知Worker（v15 §8.10.3の実装）。
 * - deadline_sweep（15分周期）: 期限1時間以内 or 期限切れ24時間以内の未通知タスクを1回だけ通知。
 * - timebox（ワンショット）: タスク開始時に見積もり時間後で予約。完了・破棄時はキャンセル。
 *   発火時にまだ進行中＋見積もり超過のときだけ通知する（早 firing ガード）。
 * 再通知・スヌーズは将来課題。通知権限が無ければ何も表示されない（クラッシュしない）。
 */
@HiltWorker
class TaskNotifyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskDao: TaskDao,
    private val timeLogDao: TaskTimeLogDao
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val KIND_DEADLINE_SWEEP = "deadline_sweep"
        const val KIND_TIMEBOX = "timebox"
        const val SWEEP_WORK = "task_deadline_sweep"
        const val PREFS = "task_notify"
        private const val HOUR_MS = 60 * 60 * 1000L
        private const val DAY_MS = 24 * HOUR_MS

        fun timeboxTag(taskId: String) = "timebox_$taskId"

        fun scheduleSweep(context: Context) {
            val req = PeriodicWorkRequestBuilder<TaskNotifyWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf("kind" to KIND_DEADLINE_SWEEP))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(SWEEP_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun scheduleTimebox(context: Context, taskId: String, delayMinutes: Int) {
            val req = OneTimeWorkRequestBuilder<TaskNotifyWorker>()
                .setInitialDelay(delayMinutes.toLong().coerceAtLeast(1), TimeUnit.MINUTES)
                .setInputData(workDataOf("kind" to KIND_TIMEBOX, "taskId" to taskId))
                .addTag(timeboxTag(taskId))
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }

        fun cancelTimebox(context: Context, taskId: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag(timeboxTag(taskId))
        }
    }

    override suspend fun doWork(): Result {
        return try {
            ensureChannel()
            when (inputData.getString("kind")) {
                KIND_TIMEBOX -> notifyTimeboxIfActive(inputData.getString("taskId") ?: return Result.success())
                else -> sweepDeadlines()
            }
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private suspend fun sweepDeadlines() {
        val now = System.currentTimeMillis()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tasks = taskDao.observeAll().first()
            .filter { it.status == TaskEngine.STATUS_PENDING || it.status == TaskEngine.STATUS_IN_PROGRESS }
        for (t in tasks) {
            val until = t.deadlineAt - now
            val overdue = until < 0 && now - t.deadlineAt < DAY_MS
            val dueSoon = until in 0..HOUR_MS
            if (!overdue && !dueSoon) continue
            if (prefs.getBoolean("notified_${t.id}", false)) continue
            val text = if (overdue) "「${t.title}」は期限を過ぎています"
            else "「${t.title}」の期限まで残り${until / 60000}分です"
            notify(t.id.hashCode(), if (overdue) "⏰ タスク期限切れ" else "⏰ タスク期限間近", text)
            prefs.edit().putBoolean("notified_${t.id}", true).apply()
        }
    }

    private suspend fun notifyTimeboxIfActive(taskId: String) {
        val t = taskDao.getById(taskId) ?: return
        if (t.status != TaskEngine.STATUS_IN_PROGRESS) return
        val log = timeLogDao.getOpenLogFor(taskId) ?: return
        val elapsedMin = (System.currentTimeMillis() - log.startedAt) / 60000
        if (elapsedMin < t.estimatedMinutes) return
        notify(
            ("timebox$taskId").hashCode(),
            "⏱ タイムボックス終了",
            "「${t.title}」の見積もり時間を使い切りました"
        )
    }

    private fun ensureChannel() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "タスクのリマインダー", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun notify(id: Int, title: String, text: String) {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
        applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)?.let { intent ->
                val pi = PendingIntent.getActivity(
                    applicationContext, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pi)
            }
        NotificationManagerCompat.from(applicationContext).notify(id, builder.build())
    }
}

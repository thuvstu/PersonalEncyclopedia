package com.thuvstu.personalencyclopedia.brain.task

import com.thuvstu.personalencyclopedia.db.dao.ProgressEventDao
import com.thuvstu.personalencyclopedia.db.dao.TaskDao
import com.thuvstu.personalencyclopedia.db.dao.TaskTimeLogDao
import com.thuvstu.personalencyclopedia.db.dao.TaskWithActualMinutes
import com.thuvstu.personalencyclopedia.db.entity.ProgressEventEntity
import com.thuvstu.personalencyclopedia.db.entity.TaskEntity
import com.thuvstu.personalencyclopedia.db.entity.TaskTimeLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * §8.10 タスクエンジン（パーキンソンの法則対抗）のテスト。
 * DAO はインメモリフェイクで代替し、DB 不要の JVM 単体テストとして実行する。
 */
class TaskEngineTest {

    private lateinit var fakeTaskDao: FakeTaskDao
    private lateinit var fakeTimeLogDao: FakeTimeLogDao
    private lateinit var fakeProgressDao: FakeProgressDao
    private lateinit var engine: TaskEngine

    @Before
    fun setup() {
        fakeTaskDao = FakeTaskDao()
        fakeTimeLogDao = FakeTimeLogDao()
        fakeProgressDao = FakeProgressDao()
        engine = TaskEngine(fakeTaskDao, fakeTimeLogDao, fakeProgressDao)
    }

    private fun newTask(id: String = "t1", estimatedMinutes: Int = 60, deadlineAt: Long = System.currentTimeMillis() + 86400_000L) =
        TaskEntity(
            id = id,
            title = "task-$id",
            estimatedMinutes = estimatedMinutes,
            deadlineAt = deadlineAt
        )

    @Test
    fun `startTask creates open time log and sets in_progress`() = runBlocking {
        fakeTaskDao.insert(newTask())
        engine.startTask("t1")

        val task = fakeTaskDao.getById("t1")!!
        assertEquals(TaskEngine.STATUS_IN_PROGRESS, task.status)
        val log = fakeTimeLogDao.getOpenLogFor("t1")
        assertNotNull("進行中のタイムログが存在する", log)
        assertNull(log!!.endedAt)
        assertTrue("progress_events に started が記録される",
            fakeProgressDao.events.any { it.entityId == "t1" && it.eventType == "started" })
    }

    @Test
    fun `completeTask closes log and returns it with endedAt`() = runBlocking {
        fakeTaskDao.insert(newTask())
        engine.startTask("t1")
        val closed = engine.completeTask("t1")

        assertNotNull(closed)
        assertNotNull("endedAt が設定される", closed!!.endedAt)
        assertEquals(TaskEngine.STATUS_DONE, fakeTaskDao.getById("t1")!!.status)
        assertNull("開いたままのログは残らない", fakeTimeLogDao.getOpenLogFor("t1"))
        assertTrue("progress_events に completed が記録される",
            fakeProgressDao.events.any { it.entityId == "t1" && it.eventType == "completed" })
    }

    @Test
    fun `estimationBiasReport computes average of actual over estimate`() = runBlocking {
        fakeTaskDao.insert(newTask(id = "a", estimatedMinutes = 100))
        fakeTaskDao.insert(newTask(id = "b", estimatedMinutes = 100))
        fakeTaskDao.completedWithActual += TaskWithActualMinutes(
            id = "a", title = "a", description = null, estimatedMinutes = 100,
            deadlineAt = 0, status = TaskEngine.STATUS_DONE, postponeCount = 0,
            linkedEntryId = null, linkedTopicId = null, createdAt = 0, completedAt = 1,
            actualMinutes = 200  // 2.0倍: 過小評価
        )
        fakeTaskDao.completedWithActual += TaskWithActualMinutes(
            id = "b", title = "b", description = null, estimatedMinutes = 100,
            deadlineAt = 0, status = TaskEngine.STATUS_DONE, postponeCount = 0,
            linkedEntryId = null, linkedTopicId = null, createdAt = 0, completedAt = 1,
            actualMinutes = 50   // 0.5倍: 過大評価
        )

        val report = engine.estimationBiasReport()
        assertEquals(2, report.sampleSize)
        assertEquals(1.25, report.averageRatio!!, 0.001)
    }

    @Test
    fun `estimationBiasReport with no completed tasks has null ratio`() = runBlocking {
        val report = engine.estimationBiasReport()
        assertEquals(0, report.sampleSize)
        assertNull(report.averageRatio)
    }

    @Test
    fun `postponeTask allows up to MAX_SILENT_POSTPONES then requires forced choice`() = runBlocking {
        fakeTaskDao.insert(newTask(id = "t1"))
        val newDeadline = System.currentTimeMillis() + 2 * 86400_000L

        // 1〜3回目は通常の延期
        for (n in 1..TaskEngine.MAX_SILENT_POSTPONES) {
            val r = engine.postponeTask("t1", newDeadline)
            assertTrue("$n 回目は Postponed", r is PostponeResult.Postponed)
            assertEquals(n, (r as PostponeResult.Postponed).count)
            assertEquals(n, fakeTaskDao.getById("t1")!!.postponeCount)
        }

        // 4回目（上限超え）は強制選択
        val r4 = engine.postponeTask("t1", newDeadline)
        assertTrue("上限超過で RequireForcedChoice", r4 is PostponeResult.RequireForcedChoice)
        assertEquals("強制選択では postponeCount が増えない", 3, fakeTaskDao.getById("t1")!!.postponeCount)
    }

    @Test
    fun `postponeTask for missing task returns NotFound`() = runBlocking {
        val r = engine.postponeTask("nope", System.currentTimeMillis())
        assertTrue(r is PostponeResult.NotFound)
    }

    @Test
    fun `forceFinishToday pins deadline to end of today`() = runBlocking {
        fakeTaskDao.insert(newTask(id = "t1"))
        engine.forceFinishToday("t1")

        val task = fakeTaskDao.getById("t1")!!
        val endOfToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("今日23:59:59に固定される", endOfToday, task.deadlineAt)
        assertEquals("postponeCount が1増える", 1, task.postponeCount)
    }

    @Test
    fun `abandonTask closes open log and marks abandoned`() = runBlocking {
        fakeTaskDao.insert(newTask(id = "t1"))
        engine.startTask("t1")
        engine.abandonTask("t1")

        assertEquals(TaskEngine.STATUS_ABANDONED, fakeTaskDao.getById("t1")!!.status)
        assertNull(fakeTimeLogDao.getOpenLogFor("t1"))
        assertTrue("progress_events に abandoned が記録される",
            fakeProgressDao.events.any { it.entityId == "t1" && it.eventType == "abandoned" })
    }

    // ── インメモリフェイク ──

    private class FakeTaskDao : TaskDao {
        val store = LinkedHashMap<String, TaskEntity>()
        val completedWithActual = mutableListOf<TaskWithActualMinutes>()

        override suspend fun insert(task: TaskEntity) { store[task.id] = task }

        override suspend fun getById(id: String): TaskEntity? = store[id]

        override fun observeById(id: String): Flow<TaskEntity?> =
            MutableStateFlow(store[id])

        override fun observeAll(): Flow<List<TaskEntity>> =
            MutableStateFlow(store.values.sortedBy { it.deadlineAt })

        override fun observeByStatus(status: String): Flow<List<TaskEntity>> =
            observeAll().map { list -> list.filter { it.status == status } }

        override suspend fun updateStatus(id: String, status: String, completedAt: Long?) {
            store[id]?.let {
                store[id] = it.copy(status = status, completedAt = completedAt)
            }
        }

        override suspend fun updatePostpone(id: String, newDeadlineAt: Long, newCount: Int) {
            store[id]?.let {
                store[id] = it.copy(
                    deadlineAt = newDeadlineAt,
                    postponeCount = newCount,
                    status = TaskEngine.STATUS_PENDING
                )
            }
        }

        override fun observeActiveCount(): Flow<Int> = observeAll().map { list ->
            list.count { it.status == TaskEngine.STATUS_PENDING || it.status == TaskEngine.STATUS_IN_PROGRESS }
        }

        override suspend fun getCompletedWithActualMinutes(): List<TaskWithActualMinutes> = completedWithActual

        override suspend fun getDoneCount(): Int = store.values.count { it.status == TaskEngine.STATUS_DONE }
    }

    private class FakeTimeLogDao : TaskTimeLogDao {
        val store = LinkedHashMap<String, TaskTimeLogEntity>()

        override suspend fun insert(log: TaskTimeLogEntity) { store[log.id] = log }

        override suspend fun getOpenLogFor(taskId: String): TaskTimeLogEntity? =
            store.values.firstOrNull { it.taskId == taskId && it.endedAt == null }

        override fun observeOpenLogFor(taskId: String): Flow<TaskTimeLogEntity?> =
            MutableStateFlow(store.values.firstOrNull { it.taskId == taskId && it.endedAt == null })

        override suspend fun close(id: String, endedAt: Long) {
            store[id]?.let { store[id] = it.copy(endedAt = endedAt) }
        }

        override suspend fun getPendingSyncLogs(): List<TaskTimeLogEntity> =
            store.values.filter { !it.studyPlusSynced && it.endedAt != null }

        override fun observePendingSyncCount(): Flow<Int> =
            MutableStateFlow(store.values.count { !it.studyPlusSynced && it.endedAt != null })

        override suspend fun markSynced(id: String) {
            store[id]?.let { store[id] = it.copy(studyPlusSynced = true) }
        }
    }

    private class FakeProgressDao : ProgressEventDao {
        val events = mutableListOf<ProgressEventEntity>()

        override suspend fun insert(event: ProgressEventEntity) { events.add(event) }

        override suspend fun getActivityByDay(since: Long): List<com.thuvstu.personalencyclopedia.db.dao.DailyActivityCount> = emptyList()
        override fun observeReviewsToday(startOfDay: Long): Flow<Int> = MutableStateFlow(0)
        override fun observeStudyDayCount(): Flow<Int> = MutableStateFlow(0)
        override suspend fun getStudyDays(): List<String> = emptyList()
    }
}

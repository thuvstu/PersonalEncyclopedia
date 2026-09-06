package com.thuvstu.personalencyclopedia.brain.task

import com.thuvstu.personalencyclopedia.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * おまかせ提案の決定論組み立てテスト。DB不要のJVM単体テスト。
 */
class TaskSuggesterTest {

    private val now = 1_700_000_000_000L

    private fun emptySignals() = TaskSignals(
        dueReviewCount = 0,
        dueReviewTitles = emptyList(),
        wrongQuizCount = 0,
        wrongQuizTopicName = null,
        unattemptedQuizCount = 0,
        imminentTasks = emptyList(),
        upcomingEvents = emptyList(),
        activeTaskCount = 0
    )

    @Test
    fun `信号ゼロなら提案ゼロ`() {
        assertTrue(TaskSuggestionBuilder.build(emptySignals(), now).isEmpty())
    }

    @Test
    fun `復習到来が最優先の1件目`() {
        val out = TaskSuggestionBuilder.build(
            emptySignals().copy(dueReviewCount = 7, dueReviewTitles = listOf("枕草子", "方丈記")),
            now
        )
        assertEquals(1, out.size)
        assertEquals("review", out[0].key)
        assertTrue(out[0].title.contains("7件"))
        assertEquals(TaskSuggestionBuilder.REVIEW_MINUTES, out[0].estimatedMinutes)
        assertTrue("締切は未来", out[0].deadlineAt > now)
    }

    @Test
    fun `苦手と未習は別提案になる`() {
        val out = TaskSuggestionBuilder.build(
            emptySignals().copy(wrongQuizCount = 3, wrongQuizTopicName = "数学", unattemptedQuizCount = 12),
            now
        )
        assertEquals(2, out.size)
        assertEquals("weakness", out[0].key)
        assertTrue(out[0].title.contains("数学"))
        assertEquals("catchup", out[1].key)
    }

    @Test
    fun `直近イベントは準備タスクになりentryに紐付く`() {
        val start = now + 3 * 24 * 60 * 60 * 1000L
        val out = TaskSuggestionBuilder.build(
            emptySignals().copy(
                upcomingEvents = listOf(UpcomingEvent("e1", "中間テスト", start))
            ),
            now
        )
        assertEquals(1, out.size)
        assertTrue(out[0].title.contains("中間テスト"))
        assertEquals("e1", out[0].linkedEntryId)
        assertTrue("締切はイベント前", out[0].deadlineAt < start)
        assertTrue("締切は現在より後", out[0].deadlineAt > now)
    }

    @Test
    fun `締切間近タスクは仕上げ提案に1件だけ`() {
        val t1 = TaskEntity(id = "t1", title = "読書感想文", estimatedMinutes = 60, deadlineAt = now + 5 * 60 * 60 * 1000L)
        val t2 = TaskEntity(id = "t2", title = "掃除", estimatedMinutes = 15, deadlineAt = now + 6 * 60 * 60 * 1000L)
        val out = TaskSuggestionBuilder.build(
            emptySignals().copy(imminentTasks = listOf(t1, t2)),
            now
        )
        assertEquals(1, out.size)
        assertTrue(out[0].key.startsWith("followup-"))
        assertTrue(out[0].title.contains("読書感想文"))
    }

    @Test
    fun `最大5件に切り詰められる`() {
        val start = now + 2 * 24 * 60 * 60 * 1000L
        val out = TaskSuggestionBuilder.build(
            emptySignals().copy(
                dueReviewCount = 5,
                wrongQuizCount = 4,
                unattemptedQuizCount = 9,
                upcomingEvents = listOf(
                    UpcomingEvent("e1", "A", start),
                    UpcomingEvent("e2", "B", start),
                    UpcomingEvent("e3", "C", start)
                ),
                imminentTasks = listOf(
                    TaskEntity(id = "t1", title = "X", estimatedMinutes = 10, deadlineAt = now + 3600_000L)
                )
            ),
            now
        )
        assertEquals(TaskSuggestionBuilder.MAX_SUGGESTIONS, out.size)
    }
}

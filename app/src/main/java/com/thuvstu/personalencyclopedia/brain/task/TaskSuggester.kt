package com.thuvstu.personalencyclopedia.brain.task

import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.brain.quiz.QuizFormats
import com.thuvstu.personalencyclopedia.db.dao.EntryExtensionDao
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.dao.SrsReviewDao
import com.thuvstu.personalencyclopedia.db.dao.TaskDao
import com.thuvstu.personalencyclopedia.db.dao.TopicDao
import com.thuvstu.personalencyclopedia.db.entity.TaskEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * おまかせタスク提案（先読み自動生成の承認制版）。
 *
 * 骨格「自動処理は必ず提案→承認」に従い、タスクを直接作らず提案止まりにする。
 * 採用は UI のワンタップで通常タスク化する (TaskViewModel.adoptSuggestion)。
 *
 * 決定論を最優先し、LLM は「言い回しの整形」にだけ使う。
 * API未設定時も決定論テンプレートでフルに動く (graceful degradation)。
 */
data class UpcomingEvent(
    val entryId: String,
    val eventName: String,
    val startedAt: Long
)

/** 提案の材料。DB から集めた先読み信号のスナップショット。 */
data class TaskSignals(
    val dueReviewCount: Int,
    val dueReviewTitles: List<String>,
    val wrongQuizCount: Int,
    val wrongQuizTopicName: String?,
    val unattemptedQuizCount: Int,
    val imminentTasks: List<TaskEntity>,
    val upcomingEvents: List<UpcomingEvent>,
    val activeTaskCount: Int
)

/** 1件の提案。採用時にそのまま TaskEntity 化できる。 */
data class SuggestedTask(
    val key: String,
    val title: String,
    val description: String,
    val estimatedMinutes: Int,
    val deadlineAt: Long,
    val linkedEntryId: String? = null,
    val linkedTopicId: String? = null,
    val reason: String
)

/**
 * 決定論の提案組み立て。純粋関数 (JVMテスト可能)。
 * LLM は後段で title/description の言い回しだけを磨く。
 */
object TaskSuggestionBuilder {
    const val REVIEW_MINUTES = 25
    const val WEAKNESS_MINUTES = 30
    const val CATCHUP_MINUTES = 20
    const val EVENT_PREP_MINUTES = 30
    const val FOLLOWUP_MINUTES = 15
    const val MAX_SUGGESTIONS = 5
    const val IMMINENT_WINDOW_MS = 48 * 60 * 60 * 1000L
    const val EVENT_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L

    fun build(signals: TaskSignals, now: Long = System.currentTimeMillis()): List<SuggestedTask> {
        val out = ArrayList<SuggestedTask>()

        // 1. 期限到来の復習 (SRS)。溜めると忘却が進むため最優先
        if (signals.dueReviewCount > 0) {
            val head = signals.dueReviewTitles.take(3).joinToString("・")
            out.add(
                SuggestedTask(
                    key = "review",
                    title = "溜まった復習を片づける (${signals.dueReviewCount}件)",
                    description = if (head.isNotBlank()) "対象: $head ほか" else "期限到来の単語帳を復習する",
                    estimatedMinutes = REVIEW_MINUTES,
                    deadlineAt = endOfNextDay(now),
                    reason = "復習期限が ${signals.dueReviewCount} 件到来中"
                )
            )
        }

        // 2. 苦手 (誤答はあるが正解なし)。放置すると弱点が固定化する
        if (signals.wrongQuizCount > 0) {
            val topic = signals.wrongQuizTopicName?.let { "（${it}）" } ?: ""
            out.add(
                SuggestedTask(
                    key = "weakness",
                    title = "苦手問題を解き直す$topic (${signals.wrongQuizCount}問)",
                    description = "誤答→未克服の問題に再挑戦する",
                    estimatedMinutes = WEAKNESS_MINUTES,
                    deadlineAt = endOfNextDay(now),
                    reason = "未克服の誤答が ${signals.wrongQuizCount} 問ある"
                )
            )
        }

        // 3. 未習 (一度も解いていない)。新しい教材への着手を促す
        if (signals.unattemptedQuizCount > 0) {
            out.add(
                SuggestedTask(
                    key = "catchup",
                    title = "未挑戦のクイズに手を付ける (${signals.unattemptedQuizCount}問)",
                    description = "未回答の問題から出題して基礎を固める",
                    estimatedMinutes = CATCHUP_MINUTES,
                    deadlineAt = endOfNextDay(now),
                    reason = "未挑戦の問題が ${signals.unattemptedQuizCount} 問ある"
                )
            )
        }

        // 4. 直近イベントの準備 (7日以内に始まるもの)
        for (ev in signals.upcomingEvents.take(2)) {
            out.add(
                SuggestedTask(
                    key = "event-${ev.entryId}",
                    title = "「${ev.eventName}」の準備をする",
                    description = "関連知識の確認・持ち物・段取りを固める",
                    estimatedMinutes = EVENT_PREP_MINUTES,
                    deadlineAt = (ev.startedAt - 24 * 60 * 60 * 1000L).coerceAtLeast(now + 60 * 60 * 1000L),
                    linkedEntryId = ev.entryId,
                    reason = "イベントが ${daysUntil(now, ev.startedAt)} 日後に始まる"
                )
            )
        }

        // 5. 48時間以内の締切タスクの仕上げ (既存タスクの積み増し防止に1件まで)
        signals.imminentTasks.firstOrNull()?.let { t ->
            out.add(
                SuggestedTask(
                    key = "followup-${t.id}",
                    title = "「${t.title}」を締切前に進める",
                    description = "残作業を切り分けて着手する",
                    estimatedMinutes = FOLLOWUP_MINUTES,
                    deadlineAt = t.deadlineAt,
                    linkedEntryId = t.linkedEntryId,
                    linkedTopicId = t.linkedTopicId,
                    reason = "締切が ${hoursUntil(now, t.deadlineAt)} 時間後に迫っている"
                )
            )
        }

        return out.take(MAX_SUGGESTIONS)
    }

    private fun endOfNextDay(now: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun daysUntil(now: Long, at: Long): Long =
        ((at - now) / (24 * 60 * 60 * 1000L)).coerceAtLeast(0)

    private fun hoursUntil(now: Long, at: Long): Long =
        ((at - now) / (60 * 60 * 1000L)).coerceAtLeast(0)
}

@Singleton
class TaskSuggester @Inject constructor(
    private val taskDao: TaskDao,
    private val srsReviewDao: SrsReviewDao,
    private val quizDao: QuizDao,
    private val entryExtensionDao: EntryExtensionDao,
    private val topicDao: TopicDao,
    private val geminiClient: GeminiClient
) {
    /**
     * 先読み信号を集める。重い全文走査はせず件数・先頭のみ。
     */
    suspend fun collectSignals(now: Long = System.currentTimeMillis()): TaskSignals {
        val due = srsReviewDao.getDueEntries(now = now, limit = 50)
        val types = QuizFormats.IDS.toList()
        val wrong = quizDao.getWrongUnmasteredQuizzes(
            topicId = null, difficultyMin = null, types = types, limit = 50
        )
        val wrongTopicName = wrong.firstOrNull()?.topicId
            ?.let { runCatching { topicDao.getById(it)?.name }.getOrNull() }
        val unattempted = quizDao.getNeverAttemptedQuizzes(
            topicId = null, difficultyMin = null, types = types, limit = 50
        )
        val allTasks = runCatching {
            taskDao.observeAll().first()
        }.getOrNull() ?: emptyList()
        val imminent = allTasks.filter {
            (it.status == TaskEngine.STATUS_PENDING || it.status == TaskEngine.STATUS_IN_PROGRESS) &&
                it.deadlineAt in now..(now + TaskSuggestionBuilder.IMMINENT_WINDOW_MS)
        }.sortedBy { it.deadlineAt }
        val events = entryExtensionDao.getUpcomingEvents(
            from = now, to = now + TaskSuggestionBuilder.EVENT_WINDOW_MS, limit = 5
        ).map { UpcomingEvent(it.entryId, it.eventName, it.startedAt) }
        val activeCount = allTasks.count {
            it.status == TaskEngine.STATUS_PENDING || it.status == TaskEngine.STATUS_IN_PROGRESS
        }
        return TaskSignals(
            dueReviewCount = due.size,
            dueReviewTitles = due.take(3).map { it.title },
            wrongQuizCount = wrong.size,
            wrongQuizTopicName = wrongTopicName,
            unattemptedQuizCount = unattempted.size,
            imminentTasks = imminent,
            upcomingEvents = events,
            activeTaskCount = activeCount
        )
    }

    /**
     * 提案を生成する。決定論で組み立て、LLM設定時のみ言い回しを磨く。
     * LLM失敗時・未設定時は決定論のまま返す。
     */
    suspend fun suggest(now: Long = System.currentTimeMillis()): List<SuggestedTask> {
        val base = TaskSuggestionBuilder.build(collectSignals(now), now)
        if (base.isEmpty() || !geminiClient.isConfigured()) return base
        return refineWithLlm(base) ?: base
    }

    /**
     * LLM整形。各提案の title/description のみ上書きし、
     * 見積もり・締切・紐付けは決定論の値を保持する (安全な統合)。
     * 1件でも解釈不能ならその件は決定論のまま。
     */
    private suspend fun refineWithLlm(base: List<SuggestedTask>): List<SuggestedTask>? {
        val prompt = buildString {
            appendLine("あなたはタスク管理アシスタントです。以下のタスク案のタイトルと説明を、")
            appendLine("やる気が出る簡潔な日本語に磨いてください。意味・順番・件数は変えないこと。")
            appendLine("出力は1案につき1行、「タイトル | 説明」の形式のみ。余計な文章は書かないこと。")
            base.forEachIndexed { i, s ->
                appendLine("${i + 1}. ${s.title} | ${s.description}")
            }
        }
        val response = runCatching { geminiClient.generate(prompt) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        val lines = response.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        return base.mapIndexed { i, s ->
            val line = lines.getOrNull(i) ?: return@mapIndexed s
            val body = line.replaceFirst(Regex("^\\d+[.)、]\\s*"), "")
            val parts = body.split("|", "｜").map { it.trim() }
            if (parts.size < 2 || parts[0].isBlank()) s
            else s.copy(title = parts[0].take(60), description = parts[1].take(120))
        }
    }
}

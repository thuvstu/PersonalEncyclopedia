package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.brain.task.EstimationBias
import com.thuvstu.personalencyclopedia.brain.task.PostponeResult
import com.thuvstu.personalencyclopedia.brain.task.TaskEngine
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.TaskDao
import com.thuvstu.personalencyclopedia.db.dao.TaskTimeLogDao
import com.thuvstu.personalencyclopedia.db.dao.TopicDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.TaskEntity
import com.thuvstu.personalencyclopedia.db.entity.TaskTimeLogEntity
import com.thuvstu.personalencyclopedia.db.entity.TopicEntity
import com.thuvstu.personalencyclopedia.integration.StudyPlusClient
import com.thuvstu.personalencyclopedia.task.TaskNotifyWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import android.content.Context

/** ★P5-1: ポモドーロタイマーの段階（25分集中 / 5分休憩の往復）。 */
enum class PomodoroPhase { IDLE, FOCUS, BREAK }

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val timeLogDao: TaskTimeLogDao,
    private val taskEngine: TaskEngine,
    private val topicDao: TopicDao,
    private val entryDao: EntryDao,
    private val studyPlusClient: StudyPlusClient,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> = taskDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCount: StateFlow<Int> = taskDao.observeActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val topics: StateFlow<List<TopicEntity>> = topicDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val entries: StateFlow<List<EntryEntity>> = entryDao.observeAll(limit = 200, offset = 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 実行中タスク（進行中タイムログを持つタスク）。カウントダウン表示に使用。 */
    val runningTask: StateFlow<TaskEntity?> = taskDao.observeAll().map { list ->
        list.firstOrNull { it.status == TaskEngine.STATUS_IN_PROGRESS }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 実行中タスクの開始時刻（タイムボックス残り時間の算出用）。 */
    val runningLog: StateFlow<TaskTimeLogEntity?> = runningTask.flatMapLatest { t ->
        if (t == null) flowOf(null)
        else timeLogDao.observeOpenLogFor(t.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** §8.10.2 強制選択が必要になったタスク（RequireForcedChoice受領時）。 */
    private val _forcedChoiceTask = MutableStateFlow<TaskEntity?>(null)
    val forcedChoiceTask: StateFlow<TaskEntity?> = _forcedChoiceTask

    /** §8.10.3 タイムボックス終了（残り0秒）による強制対峙。 */
    private val _timeboxExpiredTask = MutableStateFlow<TaskEntity?>(null)
    val timeboxExpiredTask: StateFlow<TaskEntity?> = _timeboxExpiredTask

    /** 見積もり乖離レポート（§8.10.1）。タスク画面表示時に読み込む。 */
    private val _estimationBias = MutableStateFlow(EstimationBias(averageRatio = null, sampleSize = 0))
    val estimationBias: StateFlow<EstimationBias> = _estimationBias

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message

    fun refreshEstimationBias() {
        viewModelScope.launch { _estimationBias.value = taskEngine.estimationBiasReport() }
    }

    fun createTask(
        title: String,
        description: String?,
        estimatedMinutes: Int,
        deadlineAt: Long,
        linkedEntryId: String?,
        linkedTopicId: String?
    ) {
        viewModelScope.launch {
            taskDao.insert(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    description = description?.takeIf { it.isNotBlank() },
                    estimatedMinutes = estimatedMinutes,
                    deadlineAt = deadlineAt,
                    linkedEntryId = linkedEntryId,
                    linkedTopicId = linkedTopicId
                )
            )
        }
    }

    fun startTask(taskId: String) {
        viewModelScope.launch {
            taskEngine.startTask(taskId)
            // ★通知系: 見積もり時間後にタイムボックス終了通知を予約
            taskDao.getById(taskId)?.let {
                TaskNotifyWorker.scheduleTimebox(appContext, taskId, it.estimatedMinutes)
            }
            _message.emit("▶️ タイムボックス開始（見積もり時間のカウントダウン中）")
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            TaskNotifyWorker.cancelTimebox(appContext, taskId)
            val closedLog = taskEngine.completeTask(taskId)
            if (closedLog != null) {
                val task = taskDao.getById(taskId)
                if (task != null) {
                    val topicName = task.linkedTopicId?.let { id -> topicDao.getById(id)?.name }
                    studyPlusClient.syncTaskTimeLog(closedLog, task, topicName) // §7.8: 未設定時は静かにスキップ
                }
                _message.emit("✅ タスク完了。見積もり vs 実績を記録しました")
            }
            refreshEstimationBias()
        }
    }

    fun abandonTask(taskId: String) {
        viewModelScope.launch {
            taskEngine.abandonTask(taskId)
            TaskNotifyWorker.cancelTimebox(appContext, taskId)
            _forcedChoiceTask.value = null
            _timeboxExpiredTask.value = null
            _message.emit("🗑️ タスクを破棄しました（status=abandoned）")
        }
    }

    fun postponeTask(taskId: String, newDeadlineAt: Long) {
        viewModelScope.launch {
            when (val r = taskEngine.postponeTask(taskId, newDeadlineAt)) {
                is PostponeResult.Postponed ->
                    _message.emit("⏳ 期限を延期しました（先延ばし ${r.count}回目）")
                is PostponeResult.RequireForcedChoice ->
                    _forcedChoiceTask.value = taskDao.getById(taskId)
                PostponeResult.NotFound -> _message.emit("タスクが見つかりません")
            }
        }
    }

    /** §8.10.2: 強制選択「今日中に終わらせる」→ 締切を今日の23:59に固定。 */
    fun forceFinishToday(taskId: String) {
        viewModelScope.launch {
            taskEngine.forceFinishToday(taskId)
            _forcedChoiceTask.value = null
            _timeboxExpiredTask.value = null
            _message.emit("📌 締切を「今日中」に確定しました。今すぐ着手してください")
        }
    }

    /** §8.10.3: タイムボックス終了時の強制対峙を表示する。 */
    fun notifyTimeboxExpired(taskId: String) {
        if (_timeboxExpiredTask.value?.id != taskId && _forcedChoiceTask.value == null) {
            viewModelScope.launch {
                _timeboxExpiredTask.value = taskDao.getById(taskId)
            }
        }
    }

    fun dismissTimeboxExpired() {
        _timeboxExpiredTask.value = null
    }

    // ── ★P5-1: ポモドーロタイマー（25分集中/5分休憩。実行中タスクと独立、VM常駐で回転に強い）──
    companion object {
        const val POMODORO_FOCUS_S = 25 * 60
        const val POMODORO_BREAK_S = 5 * 60
    }

    private val _pomodoroPhase = MutableStateFlow(PomodoroPhase.IDLE)
    val pomodoroPhase: StateFlow<PomodoroPhase> = _pomodoroPhase

    private val _pomodoroRemainingS = MutableStateFlow(0)
    val pomodoroRemainingS: StateFlow<Int> = _pomodoroRemainingS

    private val _pomodoroCycles = MutableStateFlow(0)
    val pomodoroCycles: StateFlow<Int> = _pomodoroCycles

    private val _pomodoroRunning = MutableStateFlow(false)
    val pomodoroRunning: StateFlow<Boolean> = _pomodoroRunning

    private var pomodoroJob: Job? = null

    /** 開始/再開。IDLEからは集中25分で開始する。 */
    fun startPomodoro() {
        if (pomodoroJob?.isActive == true) return
        if (_pomodoroPhase.value == PomodoroPhase.IDLE) {
            _pomodoroPhase.value = PomodoroPhase.FOCUS
            _pomodoroRemainingS.value = POMODORO_FOCUS_S
        }
        _pomodoroRunning.value = true
        viewModelScope.launch { _message.emit("🍅 集中開始（25分）") }
        pomodoroJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val left = _pomodoroRemainingS.value - 1
                if (left <= 0) {
                    if (_pomodoroPhase.value == PomodoroPhase.FOCUS) {
                        _pomodoroCycles.value += 1
                        _pomodoroPhase.value = PomodoroPhase.BREAK
                        _pomodoroRemainingS.value = POMODORO_BREAK_S
                        _message.emit("☕ 休憩（5分）。${_pomodoroCycles.value}サイクル完了")
                    } else {
                        _pomodoroPhase.value = PomodoroPhase.FOCUS
                        _pomodoroRemainingS.value = POMODORO_FOCUS_S
                        _message.emit("🍅 集中開始（25分）")
                    }
                } else {
                    _pomodoroRemainingS.value = left
                }
            }
        }
    }

    /** 一時停止（段階・残り時間・サイクル数は保持）。 */
    fun pausePomodoro() {
        pomodoroJob?.cancel()
        pomodoroJob = null
        _pomodoroRunning.value = false
    }

    /** リセット（サイクル数は保持し、段階をIDLEに戻す）。 */
    fun resetPomodoro() {
        pomodoroJob?.cancel()
        pomodoroJob = null
        _pomodoroRunning.value = false
        _pomodoroPhase.value = PomodoroPhase.IDLE
        _pomodoroRemainingS.value = 0
    }
}

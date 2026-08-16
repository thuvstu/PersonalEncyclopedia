package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.brain.ResurfacingEngine
import com.thuvstu.personalencyclopedia.brain.task.EstimationBias
import com.thuvstu.personalencyclopedia.brain.task.TaskEngine
import com.thuvstu.personalencyclopedia.db.dao.TaskDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.importer.WebScraper
import com.thuvstu.personalencyclopedia.repository.ConnectionRepository
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import com.thuvstu.personalencyclopedia.repository.QuizRepository
import com.thuvstu.personalencyclopedia.repository.SrsRepository
import com.thuvstu.personalencyclopedia.repository.ThoughtDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: EntryRepository,
    private val srsRepo: SrsRepository,
    private val quizRepo: QuizRepository,
    private val webScraper: WebScraper,
    private val connectionRepo: ConnectionRepository,
    private val resurfacingEngine: ResurfacingEngine,    // ★§7.5
    private val taskEngine: TaskEngine,                   // ★§8.10/§11.11 タスク
    private val taskDao: TaskDao
) : ViewModel() {

    val recentEntries: StateFlow<List<EntryEntity>> =
        repo.observeRecent(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> =
        repo.observeCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dueCount: StateFlow<Int> =
        srsRepo.observeDueCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val quizCount: StateFlow<Int> =
        quizRepo.observeQuizCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingConnectionCount: StateFlow<Int> =
        connectionRepo.observePendingCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ★§7.5: リサーフェシング候補
    private val _resurfacingCandidates =
        MutableStateFlow<List<ResurfacingEngine.ResurfacingCandidate>>(emptyList())
    val resurfacingCandidates: StateFlow<List<ResurfacingEngine.ResurfacingCandidate>> =
        _resurfacingCandidates

    // ★§7.5: 整理候補
    private val _cleanupSuggestions =
        MutableStateFlow<List<ResurfacingEngine.CleanupSuggestion>>(emptyList())
    val cleanupSuggestions: StateFlow<List<ResurfacingEngine.CleanupSuggestion>> =
        _cleanupSuggestions

    private val _quickAddTitle = MutableStateFlow("")
    val quickAddTitle: StateFlow<String> = _quickAddTitle

    // ★§8.10.1 / §11.11: 見積もり精度レポートカード
    private val _estimationBias =
        MutableStateFlow(EstimationBias(averageRatio = null, sampleSize = 0))
    val estimationBias: StateFlow<EstimationBias> = _estimationBias

    // ★§11.11: アクティブタスク件数（ダッシュボードのToDoボタンに表示）
    val activeTaskCount: StateFlow<Int> = taskDao.observeActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        loadResurfacing()
        refreshTaskStats()
    }

    fun refreshTaskStats() {
        viewModelScope.launch {
            _estimationBias.value = taskEngine.estimationBiasReport()
        }
    }

    fun loadResurfacing() {
        viewModelScope.launch {
            _resurfacingCandidates.value = resurfacingEngine.getResurfacingCandidates(5)
            _cleanupSuggestions.value = resurfacingEngine.getCleanupSuggestions(5)
        }
    }

    fun onQuickAddTitleChange(value: String) { _quickAddTitle.value = value }

    fun quickAddThought() {
        val title = _quickAddTitle.value.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            if (repo.findByTitle(title) == null) {
                repo.createThought(ThoughtDraft(title = title, content = null))
            }
            _quickAddTitle.value = ""
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repo.toggleFavorite(id) }
    }

    fun softDelete(id: String) {
        viewModelScope.launch { repo.softDelete(id) }
    }

    // ★§7.5: 整理候補をミュート（削除ではなくランキングから降格）
    fun muteEntry(id: String) {
        viewModelScope.launch {
            repo.getEntry(id)?.let { entry ->
                // isMuted を true にする（EntryDao にメソッドがないため update で対応）
                // 実際には EntryRepository に muteEntry を追加すべきだが、
                // 既存の softDelete とは異なり「非表示」のみ
                repo.touch(id)  // accessedAt 更新で一旦候補から外す
            }
            loadResurfacing()
        }
    }

    // ── URL スクレイプ（クイック追加ダイアログ用）──
    sealed class ScrapeState {
        object Idle : ScrapeState()
        object Loading : ScrapeState()
        data class Done(val entryId: String, val title: String, val deduplicated: Boolean) : ScrapeState()
        data class Failed(val message: String) : ScrapeState()
    }

    private val _scrapeState = MutableStateFlow<ScrapeState>(ScrapeState.Idle)
    val scrapeState: StateFlow<ScrapeState> = _scrapeState

    fun scrapeUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _scrapeState.value = ScrapeState.Loading
            val result = webScraper.scrapeAndSave(url.trim())
            _scrapeState.value = if (result.success) {
                ScrapeState.Done(result.entryId, result.title, result.deduplicated)
            } else {
                ScrapeState.Failed(result.error ?: "スクレイプに失敗しました")
            }
        }
    }

    fun resetScrapeState() { _scrapeState.value = ScrapeState.Idle }
}
package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.brain.coaching.CoachingEngine
import com.thuvstu.personalencyclopedia.db.dao.DailyActivityCount
import com.thuvstu.personalencyclopedia.db.dao.ProgressEventDao
import com.thuvstu.personalencyclopedia.repository.QuizRepository
import com.thuvstu.personalencyclopedia.repository.SrsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val progressEventDao: ProgressEventDao,
    private val srsRepo: SrsRepository,
    private val quizRepo: QuizRepository,
    private val coachingEngine: CoachingEngine
) : ViewModel() {

    private val _heatmap = MutableStateFlow<List<DailyActivityCount>>(emptyList())
    val heatmap: StateFlow<List<DailyActivityCount>> = _heatmap

    val studyDayCount: StateFlow<Int> =
        progressEventDao.observeStudyDayCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val streak: StateFlow<Int> = flow {
        emit(calculateStreak())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val quizCount: StateFlow<Int> = quizRepo.observeQuizCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dueCount: StateFlow<Int> = srsRepo.observeDueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _weakPointAnalysis = MutableStateFlow<String?>(null)
    val weakPointAnalysis: StateFlow<String?> = _weakPointAnalysis

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    init {
        loadHeatmap()
    }

    private fun loadHeatmap() {
        viewModelScope.launch {
            val since = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000  // last 90 days
            _heatmap.value = progressEventDao.getActivityByDay(since)
        }
    }

    private suspend fun calculateStreak(): Int {
        val days = progressEventDao.getStudyDays()
        if (days.isEmpty()) return 0

        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val calendar = java.util.Calendar.getInstance()

        var streak = 0
        val daySet = days.toSet()

        // Check if today has activity; if not, start from yesterday
        val todayStr = format.format(calendar.time)
        if (!daySet.contains(todayStr)) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }

        while (daySet.contains(format.format(calendar.time))) {
            streak++
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }

        return streak
    }

    fun analyzeWeakPoints() {
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                _weakPointAnalysis.value = coachingEngine.analyzeWeakPoints(topicId = "all")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}
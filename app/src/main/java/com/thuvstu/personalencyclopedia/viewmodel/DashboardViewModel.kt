package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import com.thuvstu.personalencyclopedia.repository.ThoughtDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: EntryRepository,
    private val srsRepo: SrsRepository,
    private val quizRepo: QuizRepository
) : ViewModel() {


    val recentEntries: StateFlow<List<EntryEntity>> =
        repo.observeRecent(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> =
        repo.observeCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _quickAddTitle = MutableStateFlow("")
    val quickAddTitle: StateFlow<String> = _quickAddTitle

    fun onQuickAddTitleChange(value: String) { _quickAddTitle.value = value }

    fun quickAddThought() {
        val title = _quickAddTitle.value.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.createThought(ThoughtDraft(title = title, content = null))
            _quickAddTitle.value = ""
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repo.toggleFavorite(id) }
    }

    fun softDelete(id: String) {
        viewModelScope.launch { repo.softDelete(id) }
    }
}
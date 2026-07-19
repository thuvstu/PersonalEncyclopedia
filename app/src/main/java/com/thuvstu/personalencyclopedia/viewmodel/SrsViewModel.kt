package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.repository.SrsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SrsViewModel @Inject constructor(
    private val srsRepo: SrsRepository
) : ViewModel() {

    sealed class SrsUiState {
        object Loading : SrsUiState()
        object Empty : SrsUiState()
        data class Reviewing(
            val cards: List<SrsRepository.ReviewCard>,
            val currentIndex: Int,
            val isAnswerRevealed: Boolean
        ) : SrsUiState()
        data class Completed(val reviewedCount: Int) : SrsUiState()
    }

    private val _uiState = MutableStateFlow<SrsUiState>(SrsUiState.Loading)
    val uiState: StateFlow<SrsUiState> = _uiState

    val dueCount: StateFlow<Int> = srsRepo.observeDueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var reviewedCount = 0

    init {
        loadDueCards()
    }

    fun loadDueCards() {
        viewModelScope.launch {
            _uiState.value = SrsUiState.Loading
            val cards = srsRepo.getDueCards(limit = 30)
            _uiState.value = if (cards.isEmpty()) {
                SrsUiState.Empty
            } else {
                SrsUiState.Reviewing(cards = cards, currentIndex = 0, isAnswerRevealed = false)
            }
        }
    }

    fun revealAnswer() {
        val state = _uiState.value as? SrsUiState.Reviewing ?: return
        _uiState.value = state.copy(isAnswerRevealed = true)
    }

    fun gradeCard(grade: Int) {
        val state = _uiState.value as? SrsUiState.Reviewing ?: return
        val card = state.cards[state.currentIndex]

        viewModelScope.launch {
            srsRepo.recordReview(card.entryId, grade)
            reviewedCount++

            val nextIndex = state.currentIndex + 1
            _uiState.value = if (nextIndex >= state.cards.size) {
                SrsUiState.Completed(reviewedCount)
            } else {
                SrsUiState.Reviewing(
                    cards = state.cards,
                    currentIndex = nextIndex,
                    isAnswerRevealed = false
                )
            }
        }
    }
}
package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class QuizListViewModel @Inject constructor(
    private val quizDao: QuizDao
) : ViewModel() {
    private val _typeFilter = MutableStateFlow<String?>(null)
    val typeFilter: StateFlow<String?> = _typeFilter

    val filteredQuizzes: StateFlow<List<QuizBankEntity>> =
        combine(quizDao.observeAllQuizzes(limit = 1000), _typeFilter) { quizzes, filter ->
            if (filter == null) quizzes else quizzes.filter { it.quizType == filter }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTypeFilter(type: String?) { _typeFilter.value = type }
}
package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.brain.search.SearchMode
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepo: SearchRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _typeFilter = MutableStateFlow<String?>(null)
    val typeFilter: StateFlow<String?> = _typeFilter

    private val _searchMode = MutableStateFlow(SearchMode.HYBRID)
    val searchMode: StateFlow<SearchMode> = _searchMode

    private val _results = MutableStateFlow<List<EntryEntity>>(emptyList())
    val results: StateFlow<List<EntryEntity>> = _results

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private var searchJob: Job? = null

    init {
        // Observe query changes with debounce
        viewModelScope.launch {
            _query
                .debounce(400)
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _results.value = emptyList()
                        return@collectLatest
                    }
                    performSearch(q)
                }
        }
    }

    private suspend fun performSearch(q: String) {
        _isSearching.value = true
        try {
            var entries = searchRepo.search(q, _searchMode.value, limit = 30)
            val filter = _typeFilter.value
            if (filter != null) {
                entries = entries.filter { it.type == filter }
            }
            _results.value = entries
        } catch (e: Exception) {
            _results.value = emptyList()
        } finally {
            _isSearching.value = false
        }
    }

    fun onQueryChange(value: String) { _query.value = value }
    fun setTypeFilter(type: String?) {
        _typeFilter.value = type
        val q = _query.value
        if (q.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(q) }
        }
    }
    fun setSearchMode(mode: SearchMode) {
        _searchMode.value = mode
        val q = _query.value
        if (q.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(q) }
        }
    }
}
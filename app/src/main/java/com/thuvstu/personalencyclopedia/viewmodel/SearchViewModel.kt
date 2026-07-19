package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: EntryRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _typeFilter = MutableStateFlow<String?>(null)
    val typeFilter: StateFlow<String?> = _typeFilter

    val results: StateFlow<List<EntryEntity>> = _query
        .distinctUntilChanged()
        .debounce(300)
        .combine(_typeFilter) { q, t -> q to t }
        .distinctUntilChanged()
        .flatMapLatest { (q, typeFilter) ->

            when {
                q.isBlank() && typeFilter == null -> repo.observeAll()
                q.isBlank() && typeFilter != null -> repo.observeByType(typeFilter)
                else -> repo.search(q)
                    .map { entries ->
                        if (typeFilter != null) entries.filter { it.type == typeFilter }
                        else entries
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun onQueryChange(value: String) { _query.value = value }
    fun setTypeFilter(type: String?) { _typeFilter.value = type }
}
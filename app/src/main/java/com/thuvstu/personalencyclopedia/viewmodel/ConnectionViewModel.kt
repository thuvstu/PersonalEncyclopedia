package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.dao.ConnectionListItem
import com.thuvstu.personalencyclopedia.db.entity.ConnectionTypeDefEntity
import com.thuvstu.personalencyclopedia.repository.CandidateWithEntries
import com.thuvstu.personalencyclopedia.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val connectionRepo: ConnectionRepository
) : ViewModel() {

    // ── 既存: 候補承認 ──
    val typeDefs: StateFlow<List<ConnectionTypeDefEntity>> =
        connectionRepo.observeTypeDefs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> =
        connectionRepo.observePendingCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _candidates = MutableStateFlow<List<CandidateWithEntries>>(emptyList())
    val candidates: StateFlow<List<CandidateWithEntries>> = _candidates

    init { loadCandidates() }

    fun loadCandidates() {
        viewModelScope.launch {
            _candidates.value = connectionRepo.getPendingCandidatesWithEntries()
        }
    }

    fun approve(candidateId: String) {
        viewModelScope.launch {
            connectionRepo.approveCandidate(candidateId)
            loadCandidates()
        }
    }

    fun reject(candidateId: String) {
        viewModelScope.launch {
            connectionRepo.rejectCandidate(candidateId)
            loadCandidates()
        }
    }

    // ── 追加: 全接続一覧・フィルタ ──
    private val _relationFilter = MutableStateFlow<String?>(null)
    val relationFilter: StateFlow<String?> = _relationFilter

    val allConnections: StateFlow<List<ConnectionListItem>> =
        combine(connectionRepo.observeAllConnections(), _relationFilter) { list, filter ->
            if (filter == null) list else list.filter { it.relationType == filter }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setRelationFilter(filter: String?) { _relationFilter.value = filter }

    fun remove(connectionId: String) {
        viewModelScope.launch { connectionRepo.removeConnection(connectionId) }
    }
}
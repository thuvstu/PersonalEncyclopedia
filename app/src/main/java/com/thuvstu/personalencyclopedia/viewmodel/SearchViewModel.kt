package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.brain.search.SearchMode
import com.thuvstu.personalencyclopedia.brain.search.SearchRefiner
import com.thuvstu.personalencyclopedia.db.dao.TagDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.TagEntity
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import com.thuvstu.personalencyclopedia.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 検索画面のVM。
 * ★mismatch §3.4: 並べ替え(5種)・お気に入り・期間・タグ(AND)の条件を `SearchRefiner` で後段適用する。
 * エンジンは関連度順の候補を多め(limit=100)に返し、条件はメモリ上で純粋関数として適用する
 * (条件変更のたびに再検索しない = 体感が速い)。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepo: SearchRepository,
    private val entryRepo: EntryRepository,
    private val tagDao: TagDao
) : ViewModel() {

    companion object {
        /** 条件で削られる分を見込んで多めに取る。表示はこの中から絞る */
        private const val CANDIDATE_LIMIT = 100
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _searchMode = MutableStateFlow(SearchMode.HYBRID)
    val searchMode: StateFlow<SearchMode> = _searchMode

    private val _criteria = MutableStateFlow(SearchRefiner.Criteria())
    val criteria: StateFlow<SearchRefiner.Criteria> = _criteria

    /** 互換: 型チップは criteria.type を見る */
    val typeFilter: StateFlow<String?> = _criteria.map { it.type }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 関連度順の生候補(エンジン出力)。条件適用前 */
    private val _candidates = MutableStateFlow<List<EntryEntity>>(emptyList())

    /** 候補のタグ(AND絞り込み用)。候補が変わるたびに一括取得(N+1回避) */
    private val _tagsByEntry = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** 画面に出す最終結果 = 候補 × 条件 */
    val results: StateFlow<List<EntryEntity>> =
        combine(_candidates, _criteria, _tagsByEntry) { cands, crit, tags ->
            SearchRefiner.apply(cands, crit, tags)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 候補件数(条件適用前)。「N件中M件」表示用 */
    val candidateCount: StateFlow<Int> = _candidates.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** タグチップ用: 全タグ */
    val allTags: StateFlow<List<TagEntity>> = tagDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _query
                .debounce(400)
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _candidates.value = emptyList()
                        _tagsByEntry.value = emptyMap()
                        return@collectLatest
                    }
                    performSearch(q)
                }
        }
    }

    private suspend fun performSearch(q: String) {
        _isSearching.value = true
        try {
            val entries = searchRepo.search(q, _searchMode.value, limit = CANDIDATE_LIMIT)
            _candidates.value = entries
            _tagsByEntry.value = if (entries.isEmpty()) emptyMap() else
                tagDao.getTagsForEntries(entries.map { it.id })
                    .groupBy({ it.entryId }, { it.tagName })
                    .mapValues { it.value.toSet() }
        } catch (e: Exception) {
            _candidates.value = emptyList()
            _tagsByEntry.value = emptyMap()
        } finally {
            _isSearching.value = false
        }
    }

    private fun research() {
        val q = _query.value
        if (q.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(q) }
        }
    }

    fun onQueryChange(value: String) { _query.value = value }

    fun setSearchMode(mode: SearchMode) {
        _searchMode.value = mode
        research()   // モードはエンジン側の条件なので再検索が要る
    }

    // ── 条件(再検索不要。メモリ上で即時反映) ──
    fun setTypeFilter(type: String?) { _criteria.update { it.copy(type = type) } }
    fun setSort(sort: SearchRefiner.SortKey) { _criteria.update { it.copy(sort = sort) } }
    fun setPeriod(period: SearchRefiner.Period) { _criteria.update { it.copy(period = period) } }
    fun setFavoritesOnly(on: Boolean) { _criteria.update { it.copy(favoritesOnly = on) } }
    fun toggleTag(name: String) {
        _criteria.update { c -> c.copy(tags = if (name in c.tags) c.tags - name else c.tags + name) }
    }
    fun clearCriteria() { _criteria.value = SearchRefiner.Criteria() }

    /** 結果カードの☆。候補リスト内の値も差し替えて即時反映する */
    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            entryRepo.toggleFavorite(id)
            _candidates.update { list -> list.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it } }
        }
    }
}

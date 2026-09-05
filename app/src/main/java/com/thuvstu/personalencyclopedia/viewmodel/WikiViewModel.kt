package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.entity.WikiArticleEntity
import com.thuvstu.personalencyclopedia.importer.AutoLinkerProvider
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import com.thuvstu.personalencyclopedia.repository.WikiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WikiViewModel @Inject constructor(
    private val wikiRepo: WikiRepository,
    private val entryRepo: EntryRepository,
    private val autoLinkerProvider: AutoLinkerProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val articleId: String? = savedStateHandle["articleId"]
    val isNew: Boolean = articleId == null || articleId == "new"

    val articles: StateFlow<List<WikiArticleEntity>> =
        wikiRepo.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val article: StateFlow<WikiArticleEntity?> = if (isNew) {
        MutableStateFlow(null)
    } else {
        wikiRepo.observeById(articleId!!)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    fun save(title: String, content: String) {
        viewModelScope.launch {
            wikiRepo.save(title, content, id = if (isNew) null else articleId)
        }
    }

    fun draftFromEntry(entryId: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            onDone(wikiRepo.draftFromEntry(entryId))
        }
    }

    suspend fun findByTitle(title: String) = wikiRepo.findByTitle(title)

    // ★P2-B: 記事本文への自動リンク埋め込み（entryタイトル対象・DB不変・linker未構築時は原文）
    val autoLinkedContentMd: StateFlow<String?> = article.flatMapLatest { a ->
        flow {
            if (a == null) emit(null)
            else {
                val linker = try { autoLinkerProvider.get() } catch (_: Exception) { null }
                emit(if (linker == null) a.contentMd else linker.applyAsWikiLinks(a.contentMd))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** リンク解決：wiki記事→なければentry→なければmissing（P2-Bでentry遷移に対応） */
    fun resolveLink(
        title: String,
        onWiki: (String) -> Unit,
        onEntry: (String) -> Unit,
        onMissing: () -> Unit
    ) {
        viewModelScope.launch {
            val w = try { wikiRepo.findByTitle(title) } catch (_: Exception) { null }
            if (w != null) { onWiki(w.id); return@launch }
            val e = try { entryRepo.findByTitle(title) } catch (_: Exception) { null }
            if (e != null) onEntry(e.id) else onMissing()
        }
    }
}
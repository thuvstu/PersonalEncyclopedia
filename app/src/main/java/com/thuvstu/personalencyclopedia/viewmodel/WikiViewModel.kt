package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.entity.WikiArticleEntity
import com.thuvstu.personalencyclopedia.repository.WikiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WikiViewModel @Inject constructor(
    private val wikiRepo: WikiRepository,
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
}
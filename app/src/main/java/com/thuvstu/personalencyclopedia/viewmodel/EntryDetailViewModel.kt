package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryThoughtEntity
import com.thuvstu.personalencyclopedia.db.entity.TagEntity
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    private val repo: EntryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId: String = savedStateHandle["entryId"] ?: ""

    val entry: StateFlow<EntryEntity?> =
        repo.observeEntry(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val thought: StateFlow<EntryThoughtEntity?> =
        repo.observeThought(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val definition: StateFlow<EntryDefinitionEntity?> =
        repo.observeDefinition(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tags: StateFlow<List<TagEntity>> =
        repo.observeTagsForEntry(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { repo.touch(entryId) }
    }

    fun toggleFavorite() {
        viewModelScope.launch { repo.toggleFavorite(entryId) }
    }

    fun softDelete() {
        viewModelScope.launch { repo.softDelete(entryId) }
    }

    fun addTag(tagName: String) {
        if (tagName.isBlank()) return
        viewModelScope.launch { repo.addTag(entryId, tagName.trim()) }
    }

    fun removeTag(tagId: String) {
        viewModelScope.launch { repo.removeTag(entryId, tagId) }
    }
}
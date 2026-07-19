package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import com.thuvstu.personalencyclopedia.repository.ThoughtDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThoughtEditViewModel @Inject constructor(
    private val repo: EntryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId: String? = savedStateHandle["entryId"]
    val isNew: Boolean = entryId == null

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private val _mood = MutableStateFlow("")
    val mood: StateFlow<String> = _mood

    private val _saved = MutableSharedFlow<String>()
    val saved: SharedFlow<String> = _saved

    init {
        if (entryId != null) {
            viewModelScope.launch {
                repo.observeEntry(entryId).first()?.let { entry ->
                    _title.value = entry.title
                    _content.value = entry.content ?: ""
                }
                repo.observeThought(entryId).first()?.let { thought ->
                    _mood.value = thought.mood ?: ""
                }
            }
        }
    }

    fun onTitleChange(v: String) { _title.value = v }
    fun onContentChange(v: String) { _content.value = v }
    fun onMoodChange(v: String) { _mood.value = v }

    fun save() {
        val titleVal = _title.value.trim()
        if (titleVal.isBlank()) return
        viewModelScope.launch {
            val draft = ThoughtDraft(
                title = titleVal,
                content = _content.value.takeIf { it.isNotBlank() },
                mood = _mood.value.takeIf { it.isNotBlank() }
            )
            if (isNew) {
                val id = repo.createThought(draft)
                _saved.emit(id)
            } else {
                repo.updateThought(entryId!!, draft)
                _saved.emit(entryId)
            }
        }
    }
}
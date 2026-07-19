package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.repository.DefinitionDraft
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DefinitionEditViewModel @Inject constructor(
    private val repo: EntryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId: String? = savedStateHandle["entryId"]
    val isNew: Boolean = entryId == null

    private val _term = MutableStateFlow("")
    val term: StateFlow<String> = _term

    private val _reading = MutableStateFlow("")
    val reading: StateFlow<String> = _reading

    private val _definition = MutableStateFlow("")
    val definition: StateFlow<String> = _definition

    private val _field = MutableStateFlow("")
    val field: StateFlow<String> = _field

    private val _saved = MutableSharedFlow<String>()
    val saved: SharedFlow<String> = _saved

    init {
        if (entryId != null) {
            viewModelScope.launch {
                repo.observeDefinition(entryId).first()?.let { def ->
                    _term.value = def.term
                    _reading.value = def.reading ?: ""
                    _definition.value = def.definition
                    _field.value = def.field ?: ""
                }
            }
        }
    }

    fun onTermChange(v: String) { _term.value = v }
    fun onReadingChange(v: String) { _reading.value = v }
    fun onDefinitionChange(v: String) { _definition.value = v }
    fun onFieldChange(v: String) { _field.value = v }

    fun save() {
        val termVal = _term.value.trim()
        val defVal = _definition.value.trim()
        if (termVal.isBlank() || defVal.isBlank()) return
        viewModelScope.launch {
            val draft = DefinitionDraft(
                term = termVal,
                reading = _reading.value.takeIf { it.isNotBlank() },
                definition = defVal,
                field = _field.value.takeIf { it.isNotBlank() }
            )
            if (isNew) {
                val id = repo.createDefinition(draft)
                _saved.emit(id)
            } else {
                repo.updateDefinition(entryId!!, draft)
                _saved.emit(entryId)
            }
        }
    }
}
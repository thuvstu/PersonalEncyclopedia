package com.thuvstu.personalencyclopedia.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.importer.ImportPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importPipeline: ImportPipeline
) : ViewModel() {

    sealed class ImportState {
        object Idle : ImportState()
        object Importing : ImportState()
        data class Done(val result: ImportPipeline.ImportResult) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportState.Importing
            try {
                val result = importPipeline.importDefinitionsCsv(uri)
                _state.value = ImportState.Done(result)
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun importMarkdown(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportState.Importing
            try {
                val result = importPipeline.importMarkdown(uri)
                _state.value = ImportState.Done(result)
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun reset() {
        _state.value = ImportState.Idle
    }
}
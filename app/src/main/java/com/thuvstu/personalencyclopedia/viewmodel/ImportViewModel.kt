package com.thuvstu.personalencyclopedia.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.brain.quiz.LlmQuizGenerator
import com.thuvstu.personalencyclopedia.importer.ImportPipeline
import com.thuvstu.personalencyclopedia.importer.ObsidianImporter
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importPipeline: ImportPipeline,
    private val obsidianImporter: ObsidianImporter,
    private val llmQuizGenerator: LlmQuizGenerator,
    private val entryRepo: EntryRepository
) : ViewModel() {

    sealed class ImportState {
        object Idle : ImportState()
        object Importing : ImportState()
        data class Done(val summaryText: String) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportState.Importing
            try {
                val result = importPipeline.importDefinitionsCsv(uri)
                _state.value = ImportState.Done("インポート完了: ${result.successCount}件の定義を作成しました。")
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
                _state.value = ImportState.Done("インポート完了: ${result.successCount}件のエントリーを作成しました。")
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun importObsidianNote(title: String, content: String) {
        viewModelScope.launch {
            _state.value = ImportState.Importing
            try {
                val parsed = obsidianImporter.parseMarkdown(title, content)
                val result = obsidianImporter.importNotes(listOf(parsed))
                _state.value = ImportState.Done("Obsidianノート取込完了: ${result.createdEntries}件作成、${result.createdConnections}件の[[wiki-link]]接続を作成しました。")
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "インポートエラー")
            }
        }
    }

    fun generateQuizzesFromEntries() {
        viewModelScope.launch {
            _state.value = ImportState.Importing
            try {
                val entries = entryRepo.observeRecent(20).first()
                var totalQuizzes = 0
                for (entry in entries) {
                    val count = llmQuizGenerator.generateFromEntry(entry, count = 2)
                    totalQuizzes += count
                }
                _state.value = ImportState.Done("AIクイズ一括生成完了: 計 ${totalQuizzes} 問のクイズを自動作成しました。")
            } catch (e: Exception) {
                _state.value = ImportState.Error("AI生成中にエラーが発生しました: ${e.message}")
            }
        }
    }

    fun reset() {
        _state.value = ImportState.Idle
    }

    // クラス内に追加:
    fun importJson(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportState.Importing
            try {
                val result = importPipeline.importEntriesJson(uri)
                _state.value = ImportState.Done("JSONインポート完了: ${result.successCount}件")
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun importUrlList(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportState.Importing
            try {
                val result = importPipeline.importUrlList(uri)
                _state.value = ImportState.Done("URL一括取り込み完了: ${result.successCount}件")
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
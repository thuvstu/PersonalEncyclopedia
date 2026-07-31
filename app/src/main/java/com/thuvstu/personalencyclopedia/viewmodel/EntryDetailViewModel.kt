package com.thuvstu.personalencyclopedia.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.brain.TagSuggestionEngine
import com.thuvstu.personalencyclopedia.db.dao.ConnectionWithEntry
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.entity.*
import com.thuvstu.personalencyclopedia.importer.AutoLinker
import com.thuvstu.personalencyclopedia.repository.AttachmentRepository
import com.thuvstu.personalencyclopedia.repository.ConnectionRepository
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import com.thuvstu.personalencyclopedia.repository.QuizRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    private val repo: EntryRepository,
    private val connectionRepo: ConnectionRepository,
    private val attachmentRepo: AttachmentRepository,
    private val quizRepo: QuizRepository,
    private val tagSuggestionEngine: TagSuggestionEngine,
    private val entryDao: EntryDao,    // ★ AutoLinker 構築用
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

    val extension: StateFlow<Any?> = entry.flatMapLatest { e ->
        flow {
            if (e == null) emit(null) else emit(when (e.type) {
                "webpage"  -> repo.getWebpage(entryId)
                "book"     -> repo.getBook(entryId)
                "video"    -> repo.getVideo(entryId)
                "document" -> repo.getDocument(entryId)
                "media"    -> repo.getMedia(entryId)
                "person"   -> repo.getPerson(entryId)
                "org"      -> repo.getOrg(entryId)
                "place"    -> repo.getPlace(entryId)
                "event"    -> repo.getEvent(entryId)
                "liked"    -> repo.getLiked(entryId)
                "ai_conv"  -> repo.getAiConv(entryId)
                else       -> null
            })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tags: StateFlow<List<TagEntity>> =
        repo.observeTagsForEntry(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connections: StateFlow<List<ConnectionWithEntry>> =
        connectionRepo.observeConnectionsForEntry(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionTypeDefs: StateFlow<List<ConnectionTypeDefEntity>> =
        connectionRepo.observeTypeDefs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val relatedEntries: StateFlow<List<EntryEntity>> = flow {
        emit(connectionRepo.getRelatedEntries(entryId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attachments: StateFlow<List<EntryAttachmentEntity>> =
        attachmentRepo.observeForEntry(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<EntryEntity>>(emptyList())
    val searchResults: StateFlow<List<EntryEntity>> = _searchResults

    private val _tagSuggestions =
        MutableStateFlow<List<TagSuggestionEngine.TagSuggestion>>(emptyList())
    val tagSuggestions: StateFlow<List<TagSuggestionEngine.TagSuggestion>> = _tagSuggestions

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message

    // ★§12.5: AutoLinker（起動時に全エントリーから構築）
    private val _autoLinker = MutableStateFlow<AutoLinker?>(null)
    val autoLinker: StateFlow<AutoLinker?> = _autoLinker

    init {
        viewModelScope.launch {
            repo.touch(entryId)  // §7.5: accessedAt 更新（リサーフェシングの基盤）
        }
        // AutoLinker 構築（バックグラウンド）
        viewModelScope.launch {
            val allEntries = entryDao.observeAll(limit = 5000, offset = 0).first()
            _autoLinker.value = AutoLinker.build(allEntries)
        }
    }

    fun searchEntriesForConnection(query: String) {
        viewModelScope.launch {
            _searchResults.value = if (query.isBlank()) emptyList()
            else repo.search(query).first().filter { it.id != entryId }
        }
    }

    fun addConnection(targetEntryId: String, relationType: String,
                      strength: Float = 0.5f, note: String? = null) {
        viewModelScope.launch {
            connectionRepo.createManualConnection(
                entryAId = entryId, entryBId = targetEntryId,
                relationType = relationType, strength = strength, note = note
            )
        }
    }

    fun toggleFavorite() { viewModelScope.launch { repo.toggleFavorite(entryId) } }
    fun softDelete() { viewModelScope.launch { repo.softDelete(entryId) } }

    fun addTag(tagName: String) {
        if (tagName.isBlank()) return
        viewModelScope.launch { repo.addTag(entryId, tagName.trim()) }
    }

    fun removeTag(tagId: String) { viewModelScope.launch { repo.removeTag(entryId, tagId) } }

    fun removeConnection(connectionId: String) {
        viewModelScope.launch { connectionRepo.removeConnection(connectionId) }
    }

    fun onTagInputChange(input: String) {
        viewModelScope.launch {
            _tagSuggestions.value = if (input.trim().length >= 2)
                tagSuggestionEngine.suggestSimilarTags(input) else emptyList()
        }
    }

    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            val id = attachmentRepo.importFromUri(entryId, uri)
            _message.emit(if (id != null) "✅ 画像を添付しました" else "❌ 添付に失敗しました")
        }
    }

    fun removeAttachment(attachment: EntryAttachmentEntity) {
        viewModelScope.launch { attachmentRepo.remove(attachment) }
    }

    fun generateQuizzesFromThisEntry() {
        viewModelScope.launch {
            val count = quizRepo.generateFromEntry(entryId)
            _message.emit(
                if (count > 0) "✅ $count 問のクイズを生成しました"
                else "生成できませんでした（definition型はルール生成、他はGemini設定が必要）"
            )
        }
    }

    /** [[wiki-link]] タイトル → entry解決 */
    fun resolveWikiLink(title: String, onResolved: (String?) -> Unit) {
        viewModelScope.launch {
            onResolved(repo.findByTitle(title)?.id)
        }
    }
}
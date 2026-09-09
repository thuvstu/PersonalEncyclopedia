package com.thuvstu.personalencyclopedia.viewmodel

import com.thuvstu.personalencyclopedia.db.entity.EntryStickyNoteEntity
import com.thuvstu.personalencyclopedia.repository.StickyNoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * 付箋操作をViewModel間で共有するための薄い委譲オブジェクト（wt56）。
 * 各ViewModelは `val sticky = StickyNoteController(repo, viewModelScope)` を持ち、
 * 画面は `sticky.event` を Snackbar に流す（削除Undo・昇格完了通知）。
 */
class StickyNoteController(
    private val repo: StickyNoteRepository,
    private val scope: CoroutineScope,
    private val source: String
) {
    sealed class Event {
        data class Deleted(val note: EntryStickyNoteEntity) : Event()
        data class PromotedToThought(val entryId: String) : Event()
        data class PromotedToTask(val taskId: String) : Event()
        data class PromotedToCandidate(val candidateId: String) : Event()
        data class Message(val text: String) : Event()
        /** ★wt58: 新カード作成（定義化 / [[リンク]]からのスタブ作成） */
        data class CardCreated(val entryId: String, val title: String) : Event()
        data class Connected(val connectionId: String) : Event()
    }

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val event: SharedFlow<Event> = _event

    fun add(entryId: String, text: String, color: String, contextId: String? = null) {
        scope.launch { repo.add(entryId, text, color, source, contextId) }
    }

    fun update(note: EntryStickyNoteEntity, text: String, color: String) {
        scope.launch { repo.updateText(note, text, color) }
    }

    fun delete(note: EntryStickyNoteEntity) {
        scope.launch {
            repo.delete(note)
            _event.tryEmit(Event.Deleted(note))
        }
    }

    fun undoDelete() {
        scope.launch { repo.undoDelete() }
    }

    fun togglePin(note: EntryStickyNoteEntity) {
        scope.launch { repo.setPinned(note, !note.isPinned) }
    }

    fun toggleResolved(note: EntryStickyNoteEntity) {
        scope.launch { repo.setResolved(note, !note.isResolved) }
    }

    fun moveUp(note: EntryStickyNoteEntity) { scope.launch { repo.move(note, up = true) } }
    fun moveDown(note: EntryStickyNoteEntity) { scope.launch { repo.move(note, up = false) } }

    fun promoteToThought(note: EntryStickyNoteEntity) {
        scope.launch {
            val id = repo.promoteToThought(note)
            _event.tryEmit(Event.PromotedToThought(id))
        }
    }

    fun promoteToTask(note: EntryStickyNoteEntity, estimatedMinutes: Int, deadlineAt: Long) {
        scope.launch {
            val id = repo.promoteToTask(note, estimatedMinutes, deadlineAt)
            _event.tryEmit(Event.PromotedToTask(id))
        }
    }

    // ── ★wt58 増殖 ──

    fun promoteToDefinition(note: EntryStickyNoteEntity) {
        scope.launch {
            val id = repo.promoteToDefinition(note)
            _event.tryEmit(Event.CardCreated(id, StickyNoteRepository.titleFrom(note.text)))
        }
    }

    fun createCardFromLink(note: EntryStickyNoteEntity, title: String) {
        scope.launch {
            val id = repo.createCardFromLink(note, title)
            _event.tryEmit(Event.CardCreated(id, title))
        }
    }

    fun connectNow(note: EntryStickyNoteEntity, targetEntryId: String? = null, relationType: String = "related") {
        scope.launch {
            val id = repo.connectNow(note, targetEntryId, relationType)
            if (id != null) _event.tryEmit(Event.Connected(id))
            else _event.tryEmit(Event.Message("接続できません（相手不明 or 既に同じ接続あり）。付箋に [[カード名]] を書いてください"))
        }
    }

    fun copyTo(note: EntryStickyNoteEntity, targetEntryId: String) {
        scope.launch {
            val n = repo.copyTo(note, targetEntryId)
            _event.tryEmit(Event.Message(if (n != null) "付箋を複製しました" else "同じカードには複製できません"))
        }
    }

    suspend fun resolveLinks(note: EntryStickyNoteEntity): List<Pair<String, String?>> =
        repo.resolveLinks(note).map { it.title to it.entry?.id }

    suspend fun suggestTitles(prefix: String, excludeEntryId: String?): List<String> =
        repo.suggestTitles(prefix, excludeEntryId).map { it.title }

    /** ピッカー用: (title, entryId) */
    suspend fun suggestEntries(prefix: String, excludeEntryId: String?): List<Pair<String, String>> =
        repo.suggestTitles(prefix, excludeEntryId).map { it.title to it.id }

    fun promoteToCandidate(note: EntryStickyNoteEntity, targetEntryId: String? = null) {
        scope.launch {
            val id = repo.promoteToConnectionCandidate(note, targetEntryId)
            if (id != null) _event.tryEmit(Event.PromotedToCandidate(id))
            else _event.tryEmit(Event.Message("接続先が見つかりません。付箋に [[カード名]] を書いてください"))
        }
    }
}

package com.thuvstu.personalencyclopedia

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * イベントシステム (§9.3).
 * アプリ内のデータ変更・学習・接続イベントの軽量なイベントバス。
 */
sealed class AppEvent {
    data class EntryCreated(val entryId: String) : AppEvent()
    data class EntryUpdated(val entryId: String) : AppEvent()
    data class EntryDeleted(val entryId: String) : AppEvent()

    data class QuizAnswered(val quizId: String, val isCorrect: Boolean, val score: Float) : AppEvent()
    data class SrsReviewed(val entryId: String, val grade: Int) : AppEvent()

    data class ConnectionApproved(val candidateId: String, val connectionId: String) : AppEvent()
    data class ImportCompleted(val source: String, val count: Int) : AppEvent()
}

@Singleton
class AppEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: AppEvent): Boolean {
        return _events.tryEmit(event)
    }
}

package com.thuvstu.personalencyclopedia

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §11.4 Android共有メニュー等、Activity → Compose Navigation の橋渡し。
 *
 * 設計意図:
 * - Activity は Compose の navController に直接アクセスできない
 * - 共有インテントは Activity のライフサイクルで届く
 * - この Singleton が「保存完了 → 詳細画面へ遷移」のキューを持つ
 * - MainContent の LaunchedEffect が pending を監視して遷移を実行
 *
 * 将来の拡張:
 * - pendingAction: SharedFlow<NavigationAction> にすれば
 *   「共有→編集画面へ」「共有→検索結果へ」等の多様な遷移に対応可能
 */
@Singleton
class IncomingNavigation @Inject constructor() {

    private val _pendingEntryId = MutableStateFlow<String?>(null)
    val pendingEntryId: StateFlow<String?> = _pendingEntryId.asStateFlow()

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    private val _pendingNotice = MutableStateFlow<IncomingNotice?>(null)
    val pendingNotice: StateFlow<IncomingNotice?> = _pendingNotice.asStateFlow()

    fun setPendingEntry(entryId: String) {
        _pendingEntryId.value = entryId
    }

    fun clear() {
        _pendingEntryId.value = null
    }

    fun setPendingRoute(route: String) {
        _pendingRoute.value = route
    }

    fun clearRoute() {
        _pendingRoute.value = null
    }

    fun setNotice(message: String, entryId: String? = null) {
        _pendingNotice.value = IncomingNotice(message, entryId)
    }

    fun clearNotice() {
        _pendingNotice.value = null
    }
}

data class IncomingNotice(
    val message: String,
    val entryId: String? = null
)

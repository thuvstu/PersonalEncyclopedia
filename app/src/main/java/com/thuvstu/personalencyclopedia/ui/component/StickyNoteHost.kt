package com.thuvstu.personalencyclopedia.ui.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import com.thuvstu.personalencyclopedia.db.entity.EntryStickyNoteEntity
import com.thuvstu.personalencyclopedia.viewmodel.StickyNoteController
import kotlinx.coroutines.flow.collectLatest

/**
 * 画面側の付箋配線を1か所にまとめる（wt56）。
 *
 * - `StickyNoteController` の操作を `StickyNoteActions` に束ねる
 * - 削除→Snackbar「元に戻す」、昇格→Snackbar通知（思考化は「開く」）
 * - タスク化ダイアログの表示状態を持つ
 *
 * 使い方:
 * ```
 * val actions = rememberStickyNoteActions(vm.sticky, snackbarHostState, entryIdOf = { it.entryId },
 *     onOpenEntry = onNavigateToEntry)
 * ```
 * `entryIdOf` は「追加」時にどのentryへ貼るかを決める（詳細画面なら固定ID、一覧なら各カード）。
 * 一覧画面では `actions.forEntry(entryId)` で1カード分のActionsを取り出す。
 */
class StickyNoteBinding internal constructor(
    private val controller: StickyNoteController,
    private val onOpenEntry: ((String) -> Unit)?,
    private val requestTaskDialog: (EntryStickyNoteEntity) -> Unit
) {
    fun forEntry(entryId: String, contextId: String? = null): StickyNoteActions = StickyNoteActions(
        onAdd = { text, color -> controller.add(entryId, text, color, contextId) },
        onDelete = { controller.delete(it) },
        onUpdate = { n, t, c -> controller.update(n, t, c) },
        onTogglePin = { controller.togglePin(it) },
        onToggleResolved = { controller.toggleResolved(it) },
        onMoveUp = { controller.moveUp(it) },
        onMoveDown = { controller.moveDown(it) },
        onPromoteToThought = { n ->
            val existing = n.promotedEntryId
            if (existing != null && onOpenEntry != null) onOpenEntry?.invoke(existing) else controller.promoteToThought(n)
        },
        onPromoteToTask = { requestTaskDialog(it) },
        onPromoteToCandidate = { controller.promoteToCandidate(it) },
        onOpenPromoted = onOpenEntry,
        // ★wt58 増殖
        onPromoteToDefinition = { controller.promoteToDefinition(it) },
        onConnectNow = { n, target -> controller.connectNow(n, target) },
        onCreateFromLink = { n, title -> controller.createCardFromLink(n, title) },
        onCopyTo = { n, target -> controller.copyTo(n, target) },
        onOpenLink = onOpenEntry,
        resolveLinks = { controller.resolveLinks(it) },
        suggestTitles = { prefix -> controller.suggestTitles(prefix, entryId) },
        suggestEntries = { prefix -> controller.suggestEntries(prefix, entryId) }
    )
}

@Composable
fun rememberStickyNoteBinding(
    controller: StickyNoteController,
    snackbarHostState: SnackbarHostState,
    onOpenEntry: ((String) -> Unit)? = null
): StickyNoteBinding {
    var taskTarget by remember { mutableStateOf<EntryStickyNoteEntity?>(null) }

    // イベント → Snackbar
    LaunchedEffect(controller) {
        controller.event.collectLatest { ev ->
            when (ev) {
                is StickyNoteController.Event.Deleted -> {
                    val r = snackbarHostState.showSnackbar("付箋を削除しました", actionLabel = "元に戻す", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) controller.undoDelete()
                }
                is StickyNoteController.Event.PromotedToThought -> {
                    val r = snackbarHostState.showSnackbar("思考エントリを作成しました", actionLabel = if (onOpenEntry != null) "開く" else null)
                    if (r == SnackbarResult.ActionPerformed) onOpenEntry?.invoke(ev.entryId)
                }
                is StickyNoteController.Event.PromotedToTask ->
                    snackbarHostState.showSnackbar("タスクを作成しました（ToDo画面で確認）")
                is StickyNoteController.Event.PromotedToCandidate ->
                    snackbarHostState.showSnackbar("接続候補に追加しました（承認待ち）")
                is StickyNoteController.Event.Message ->
                    snackbarHostState.showSnackbar(ev.text)
                is StickyNoteController.Event.CardCreated -> {
                    val r = snackbarHostState.showSnackbar("カード「${ev.title}」を作成・接続しました", actionLabel = if (onOpenEntry != null) "開く" else null)
                    if (r == SnackbarResult.ActionPerformed) onOpenEntry?.invoke(ev.entryId)
                }
                is StickyNoteController.Event.Connected ->
                    snackbarHostState.showSnackbar("接続を作成しました")
            }
        }
    }

    taskTarget?.let { note ->
        StickyNotePromoteTaskDialog(
            note = note,
            onConfirm = { m, d -> controller.promoteToTask(note, m, d); taskTarget = null },
            onDismiss = { taskTarget = null }
        )
    }

    return remember(controller, onOpenEntry) {
        StickyNoteBinding(controller, onOpenEntry) { taskTarget = it }
    }
}

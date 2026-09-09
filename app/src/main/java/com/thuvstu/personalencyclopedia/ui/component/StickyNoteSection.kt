@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.thuvstu.personalencyclopedia.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thuvstu.personalencyclopedia.db.entity.EntryStickyNoteEntity

/** 付箋の色キー → 実色。DBには色キーだけ保存する。 */
val stickyNoteColorKeys = listOf("yellow", "pink", "blue", "green")

fun stickyNoteColor(key: String): Color = when (key) {
    "pink" -> Color(0xFFFFD6E0)
    "blue" -> Color(0xFFD6ECFF)
    "green" -> Color(0xFFDDF5D6)
    else -> Color(0xFFFFF4B8)   // yellow
}

private val stickyTextColor = Color(0xFF3A3A2A)
/** エディタ末尾の未閉じ [[xxx を検出（[[ 補完用） */
private val OPEN_LINK_REGEX = Regex("""\[\[([^\[\]]*)$""")
private val stickyShape = RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 2.dp)

/**
 * 付箋1枚に対する操作の束。画面ごとに使う操作だけ渡し、渡さなかった操作はメニューに出ない。
 */
data class StickyNoteActions(
    val onAdd: (text: String, color: String) -> Unit,
    val onDelete: (EntryStickyNoteEntity) -> Unit,
    val onUpdate: ((EntryStickyNoteEntity, text: String, color: String) -> Unit)? = null,
    val onTogglePin: ((EntryStickyNoteEntity) -> Unit)? = null,
    val onToggleResolved: ((EntryStickyNoteEntity) -> Unit)? = null,
    val onMoveUp: ((EntryStickyNoteEntity) -> Unit)? = null,
    val onMoveDown: ((EntryStickyNoteEntity) -> Unit)? = null,
    val onPromoteToThought: ((EntryStickyNoteEntity) -> Unit)? = null,
    val onPromoteToTask: ((EntryStickyNoteEntity) -> Unit)? = null,
    val onPromoteToCandidate: ((EntryStickyNoteEntity) -> Unit)? = null,
    val onOpenPromoted: ((entryId: String) -> Unit)? = null,
    // ── ★wt58 増殖: 付箋からカード・リンク・接続を生やす ──
    /** 付箋を定義カードにして元カードと extends で接続 */
    val onPromoteToDefinition: ((EntryStickyNoteEntity) -> Unit)? = null,
    /** 接続を即作成（target=null なら [[リンク]]先） */
    val onConnectNow: ((EntryStickyNoteEntity, targetEntryId: String?) -> Unit)? = null,
    /** 付箋内の未作成 [[タイトル]] からスタブカードを作って接続 */
    val onCreateFromLink: ((EntryStickyNoteEntity, title: String) -> Unit)? = null,
    /** 別カードへ複製 */
    val onCopyTo: ((EntryStickyNoteEntity, targetEntryId: String) -> Unit)? = null,
    /** [[リンク]]チップのタップで既存カードを開く */
    val onOpenLink: ((entryId: String) -> Unit)? = null,
    /** 付箋内 [[リンク]] の解決: (title, entryId?) の一覧 */
    val resolveLinks: (suspend (EntryStickyNoteEntity) -> List<Pair<String, String?>>)? = null,
    /** エディタの [[ 補完用のタイトル候補 */
    val suggestTitles: (suspend (String) -> List<String>)? = null,
    /** 接続先/複製先ピッカー用: (title, entryId) */
    val suggestEntries: (suspend (String) -> List<Pair<String, String>>)? = null
)

/**
 * 一覧カード用の折りたたみ付箋（wt56）。
 *
 * 畳んだ状態: 「📝 最新の未解決付箋1行 +N ▼」。
 * タップ: 全枚を展開表示し、その場で追加・解決・ピン・削除・昇格ができる。
 * カード本体の onClick とは別に付箋だけをタップで開閉できるよう、clickable をここで完結させる。
 */
@Composable
fun StickyNoteTab(
    notes: List<EntryStickyNoteEntity>,
    actions: StickyNoteActions,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val unresolved = notes.filter { !it.isResolved }
    val headline = (notes.firstOrNull { it.isPinned && !it.isResolved } ?: unresolved.lastOrNull() ?: notes.lastOrNull())

    Column(modifier = modifier.fillMaxWidth().animateContentSize()) {
        // ── タブ（常時表示・タップで開閉）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = if (expanded) 0.dp else 8.dp, bottomEnd = if (expanded) 0.dp else 8.dp))
                .background(
                    if (headline == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else stickyNoteColor(headline.color)
                )
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📝", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(6.dp))
            if (headline == null) {
                Text(
                    "付箋を貼る",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            } else {
                if (headline.isPinned) {
                    Icon(Icons.Default.PushPin, contentDescription = null, tint = stickyTextColor.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    text = if (expanded) "付箋 ${notes.size}枚" + (if (unresolved.size != notes.size) "（未解決 ${unresolved.size}）" else "")
                    else headline.text.lineSequence().firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = stickyTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!expanded && notes.size > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text("+${notes.size - 1}", style = MaterialTheme.typography.labelSmall, color = stickyTextColor.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelSmall,
                color = if (headline == null) MaterialTheme.colorScheme.onSurfaceVariant else stickyTextColor.copy(alpha = 0.7f)
            )
        }

        // ── 展開部 ──
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StickyNoteList(notes = notes, actions = actions, compact = true)
                if (adding) {
                    StickyNoteEditor(
                        onConfirm = { text, color -> actions.onAdd(text, color); adding = false },
                        onCancel = { adding = false },
                        suggestTitles = actions.suggestTitles
                    )
                } else {
                    TextButton(
                        onClick = { adding = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("付箋を追加", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * 詳細画面用: 常時展開のセクション。
 */
@Composable
fun StickyNoteSection(
    notes: List<EntryStickyNoteEntity>,
    actions: StickyNoteActions,
    modifier: Modifier = Modifier,
    title: String = "📝 付箋"
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var showResolved by rememberSaveable { mutableStateOf(false) }
    val unresolved = notes.filter { !it.isResolved }
    val resolved = notes.filter { it.isResolved }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (notes.isNotEmpty()) {
                    Text(
                        "${unresolved.size}枚" + (if (resolved.isNotEmpty()) " / 解決済み${resolved.size}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (notes.isEmpty() && !adding) {
                Text(
                    "読んで思ったこと・疑問・あとで確かめたいことを一言メモ。長押しでメニュー（ピン・解決・昇格）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StickyNoteList(notes = unresolved, actions = actions, compact = false)
            if (resolved.isNotEmpty()) {
                TextButton(onClick = { showResolved = !showResolved }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(
                        (if (showResolved) "▲ " else "▼ ") + "解決済み ${resolved.size}枚",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                AnimatedVisibility(visible = showResolved) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StickyNoteList(notes = resolved, actions = actions, compact = false)
                    }
                }
            }
            if (adding) {
                StickyNoteEditor(
                    onConfirm = { text, color -> actions.onAdd(text, color); adding = false },
                    onCancel = { adding = false },
                    suggestTitles = actions.suggestTitles
                )
            } else {
                OutlinedButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("付箋を追加")
                }
            }
        }
    }
}

/**
 * 学習中(SRS/クイズ)向けの最小フォーム: 「💭 思ったことを貼る」ボタン → 入力欄。
 * 回答の流れを止めないよう、貼ったら即畳む。
 */
@Composable
fun StickyNoteQuickAdd(
    onAdd: (text: String, color: String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "💭 思ったことを付箋に",
    existingCount: Int = 0
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var justAdded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().animateContentSize()) {
        if (!open) {
            TextButton(onClick = { open = true; justAdded = false }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(
                    if (justAdded) "✓ 貼りました（もう1枚）" else label + (if (existingCount > 0) "（$existingCount）" else ""),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        } else {
            StickyNoteEditor(
                onConfirm = { t, c -> onAdd(t, c); open = false; justAdded = true },
                onCancel = { open = false }
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
// 内部部品
// ────────────────────────────────────────────────────────────

@Composable
private fun StickyNoteList(
    notes: List<EntryStickyNoteEntity>,
    actions: StickyNoteActions,
    compact: Boolean
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    notes.forEachIndexed { i, note ->
        if (editingId == note.id && actions.onUpdate != null) {
            StickyNoteEditor(
                initialText = note.text,
                initialColor = note.color,
                onConfirm = { t, c -> actions.onUpdate?.invoke(note, t, c); editingId = null },
                onCancel = { editingId = null },
                suggestTitles = actions.suggestTitles
            )
        } else {
            StickyNoteItem(
                note = note,
                actions = actions,
                canMoveUp = i > 0,
                canMoveDown = i < notes.lastIndex,
                onEdit = if (actions.onUpdate != null) ({ editingId = note.id }) else null,
                compact = compact
            )
        }
    }
}

/** 付箋1枚。タップで全文/省略、長押しまたは ⋮ でメニュー。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickyNoteItem(
    note: EntryStickyNoteEntity,
    actions: StickyNoteActions,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: (() -> Unit)?,
    compact: Boolean
) {
    var showFull by remember(note.id) { mutableStateOf(false) }
    var menu by remember(note.id) { mutableStateOf(false) }
    var picker by remember(note.id) { mutableStateOf<String?>(null) }   // ★wt58 "connect" / "copy"

    picker?.let { mode ->
        StickyCardPickerDialog(
            title = if (mode == "connect") "接続先のカードを選ぶ" else "複製先のカードを選ぶ",
            suggestEntries = actions.suggestEntries,
            onPick = { entryId ->
                if (mode == "connect") actions.onConnectNow?.invoke(note, entryId) else actions.onCopyTo?.invoke(note, entryId)
                picker = null
            },
            onDismiss = { picker = null }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(stickyShape)
            .background(stickyNoteColor(note.color))
            .alpha(if (note.isResolved) 0.55f else 1f)
            .combinedClickable(onClick = { showFull = !showFull }, onLongClick = { menu = true })
            .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.Top
    ) {
        // 解決チェック（渡されていれば）
        if (actions.onToggleResolved != null) {
            Checkbox(
                checked = note.isResolved,
                onCheckedChange = { actions.onToggleResolved?.invoke(note) },
                modifier = Modifier.size(28.dp).padding(end = 4.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(top = if (actions.onToggleResolved != null) 4.dp else 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned) {
                    Icon(Icons.Default.PushPin, contentDescription = "ピン留め", tint = stickyTextColor.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = note.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = stickyTextColor,
                    textDecoration = if (note.isResolved) TextDecoration.LineThrough else null,
                    maxLines = if (showFull) Int.MAX_VALUE else if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // ★wt58 [[リンク]] チップ: 既存 → 開く、未作成 → ＋で作成
            StickyLinkChips(note, actions)
            // 昇格済みバッジ
            val promoted = buildList {
                if (note.promotedEntryId != null) add("💭 思考化済")
                if (note.promotedTaskId != null) add("✅ タスク化済")
                if (note.promotedCandidateId != null) add("🔗 接続候補")
            }
            if (promoted.isNotEmpty() || note.source != "detail") {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val promotedId = note.promotedEntryId
                    promoted.forEach { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = stickyTextColor.copy(alpha = 0.7f),
                            modifier = if (label.startsWith("💭") && actions.onOpenPromoted != null && promotedId != null)
                                Modifier.clickable { actions.onOpenPromoted.invoke(promotedId) }
                            else Modifier
                        )
                    }
                    if (note.source != "detail") {
                        Text(sourceLabel(note.source), style = MaterialTheme.typography.labelSmall, color = stickyTextColor.copy(alpha = 0.5f))
                    }
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "メニュー", tint = stickyTextColor.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                onEdit?.let {
                    DropdownMenuItem(text = { Text("編集") }, onClick = { menu = false; it() })
                }
                actions.onTogglePin?.let {
                    DropdownMenuItem(text = { Text(if (note.isPinned) "ピンを外す" else "ピン留め") }, onClick = { menu = false; it(note) })
                }
                actions.onToggleResolved?.let {
                    DropdownMenuItem(text = { Text(if (note.isResolved) "未解決に戻す" else "解決済みにする ✓") }, onClick = { menu = false; it(note) })
                }
                if (actions.onMoveUp != null && canMoveUp) {
                    DropdownMenuItem(text = { Text("上へ") }, onClick = { menu = false; actions.onMoveUp?.invoke(note) })
                }
                if (actions.onMoveDown != null && canMoveDown) {
                    DropdownMenuItem(text = { Text("下へ") }, onClick = { menu = false; actions.onMoveDown?.invoke(note) })
                }
                if (actions.onPromoteToThought != null || actions.onPromoteToTask != null || actions.onPromoteToCandidate != null) {
                    HorizontalDivider()
                }
                actions.onPromoteToThought?.let {
                    DropdownMenuItem(
                        text = { Text(if (note.promotedEntryId != null) "💭 思考エントリを開く" else "💭 思考エントリにする") },
                        onClick = { menu = false; it(note) }
                    )
                }
                actions.onPromoteToTask?.let {
                    DropdownMenuItem(
                        text = { Text("✅ タスクにする") },
                        enabled = note.promotedTaskId == null,
                        onClick = { menu = false; it(note) }
                    )
                }
                actions.onPromoteToCandidate?.let {
                    DropdownMenuItem(
                        text = { Text("🔗 接続候補にする（[[リンク]]先）") },
                        enabled = note.promotedCandidateId == null,
                        onClick = { menu = false; it(note) }
                    )
                }
                // ★wt58 増殖メニュー
                if (actions.onPromoteToDefinition != null || actions.onConnectNow != null || actions.onCopyTo != null) {
                    HorizontalDivider()
                }
                actions.onPromoteToDefinition?.let {
                    DropdownMenuItem(
                        text = { Text(if (note.promotedEntryId != null) "📖 カードを開く" else "📖 新しいカードにする（派生）") },
                        onClick = {
                            menu = false
                            val pid = note.promotedEntryId
                            if (pid != null && actions.onOpenPromoted != null) actions.onOpenPromoted.invoke(pid) else it(note)
                        }
                    )
                }
                actions.onConnectNow?.let {
                    DropdownMenuItem(text = { Text("🔗 今すぐ接続（[[リンク]]先）") }, onClick = { menu = false; it(note, null) })
                    if (actions.suggestEntries != null) {
                        DropdownMenuItem(text = { Text("🔗 カードを選んで接続…") }, onClick = { menu = false; picker = "connect" })
                    }
                }
                actions.onCopyTo?.let {
                    if (actions.suggestEntries != null) {
                        DropdownMenuItem(text = { Text("📋 別カードにも貼る…") }, onClick = { menu = false; picker = "copy" })
                    }
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("削除", color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; actions.onDelete(note) }
                )
            }
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "list" -> "一覧で"
    "srs" -> "復習中に"
    "quiz" -> "クイズ中に"
    "whiteboard" -> "白板で"
    "wiki" -> "Wikiで"
    "api" -> "PCから"
    "seed" -> "🌱 種付箋"
    else -> ""
}

/** 付箋の入力欄（色選択つき）。 */
@Composable
fun StickyNoteEditor(
    onConfirm: (text: String, color: String) -> Unit,
    onCancel: () -> Unit,
    initialText: String = "",
    initialColor: String = "yellow",
    suggestTitles: (suspend (String) -> List<String>)? = null   // ★wt58 [[ 補完
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var color by rememberSaveable { mutableStateOf(initialColor) }
    // ★wt58: 末尾に "[[xxx" (未閉じ) があれば候補を出す
    val openLink = remember(text) { OPEN_LINK_REGEX.find(text)?.groupValues?.get(1) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(openLink) {
        suggestions = if (openLink != null && suggestTitles != null) {
            try { suggestTitles(openLink) } catch (_: Exception) { emptyList() }
        } else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(stickyShape)
            .background(stickyNoteColor(color))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("思ったことを一言…（[[タイトル]]で他カードを参照）", style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodySmall,
            minLines = 2,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        if (openLink != null) {
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    suggestions.forEach { t ->
                        SuggestionChip(
                            onClick = { text = text.substring(0, text.length - openLink.length) + t + "]]" },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        )
                    }
                }
            } else if (openLink.isNotBlank()) {
                Text(
                    "「$openLink」のカードは未作成 → ]] で閉じて保存すると ＋ から作れます",
                    style = MaterialTheme.typography.labelSmall, color = stickyTextColor.copy(alpha = 0.7f)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            stickyNoteColorKeys.forEach { key ->
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(stickyNoteColor(key))
                        .border(
                            width = if (key == color) 2.dp else 1.dp,
                            color = if (key == color) stickyTextColor else stickyTextColor.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .clickable { color = key }
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "キャンセル", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim(), color) },
                enabled = text.isNotBlank(),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "保存", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * タスク化ダイアログ: 見積分数と締切(今日/明日/3日後/1週間後)を選ぶ。
 * TaskEntity は estimatedMinutes と deadlineAt が必須のため、ここで確定させる。
 */
@Composable
fun StickyNotePromoteTaskDialog(
    note: EntryStickyNoteEntity,
    onConfirm: (estimatedMinutes: Int, deadlineAt: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var minutes by rememberSaveable { mutableStateOf("15") }
    var deadlineDays by rememberSaveable { mutableStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✅ タスクにする") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(note.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("見積もり（分）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("締切", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "今日", 1 to "明日", 3 to "3日後", 7 to "1週間後").forEach { (d, label) ->
                        FilterChip(selected = deadlineDays == d, onClick = { deadlineDays = d }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (minutes.toIntOrNull() ?: 0) > 0,
                onClick = {
                    val m = minutes.toIntOrNull() ?: 15
                    val cal = java.util.Calendar.getInstance().apply {
                        add(java.util.Calendar.DAY_OF_YEAR, deadlineDays)
                        set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59); set(java.util.Calendar.SECOND, 0)
                    }
                    onConfirm(m, cal.timeInMillis)
                }
            ) { Text("作成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}


// ────────────────────────────────────────────────────────────
// ★wt58 増殖部品
// ────────────────────────────────────────────────────────────

/**
 * 付箋本文の [[リンク]] をチップ化。既存カード → タップで開く。未作成 → 「＋作る」でスタブ定義を作って接続。
 * resolveLinks が無い画面では何も出さない。
 */
@Composable
private fun StickyLinkChips(note: EntryStickyNoteEntity, actions: StickyNoteActions) {
    val resolver = actions.resolveLinks ?: return
    var links by remember(note.id, note.text) { mutableStateOf<List<Pair<String, String?>>>(emptyList()) }
    LaunchedEffect(note.id, note.text) {
        links = try { resolver(note) } catch (_: Exception) { emptyList() }
    }
    if (links.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        links.forEach { (title, entryId) ->
            if (entryId != null) {
                AssistChip(
                    onClick = { actions.onOpenLink?.invoke(entryId) },
                    label = { Text("🔗 $title", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    modifier = Modifier.height(24.dp)
                )
            } else if (actions.onCreateFromLink != null) {
                AssistChip(
                    onClick = { actions.onCreateFromLink.invoke(note, title) },
                    label = { Text("＋ $title を作る", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

/** カード検索ピッカー（接続先／複製先）。タイトルで検索→タップで確定。 */
@Composable
private fun StickyCardPickerDialog(
    title: String,
    suggestEntries: (suspend (String) -> List<Pair<String, String>>)?,
    onPick: (entryId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(query) {
        results = if (query.isBlank() || suggestEntries == null) emptyList()
        else try { suggestEntries(query) } catch (_: Exception) { emptyList() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("カード名で検索") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (results.isEmpty()) {
                    Text(
                        if (query.isBlank()) "入力すると候補が出ます" else "候補なし",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                results.forEach { (t, id) ->
                    TextButton(onClick = { onPick(id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(t, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

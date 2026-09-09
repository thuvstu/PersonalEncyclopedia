package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.brain.search.SearchMode
import com.thuvstu.personalencyclopedia.brain.search.SearchRefiner
import com.thuvstu.personalencyclopedia.ui.component.EmptyState
import com.thuvstu.personalencyclopedia.ui.component.EntryCard
import com.thuvstu.personalencyclopedia.ui.component.rememberStickyNoteBinding
import com.thuvstu.personalencyclopedia.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",          // ★追加（末尾カンマ必須）
    onBack: () -> Unit,
    onNavigateToEntry: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val stickyNotesByEntry by viewModel.stickyNotesByEntry.collectAsState()   // ★wt56
    val stickySnackbar = remember { SnackbarHostState() }
    val stickyBinding = rememberStickyNoteBinding(viewModel.sticky, stickySnackbar, onOpenEntry = onNavigateToEntry)
    val results by viewModel.resultsWithSticky.collectAsState()   // ★wt56: 付箋ヒット込み
    val onlyWithStickyNotes by viewModel.onlyWithStickyNotes.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    // ★mismatch §3.4: 並べ替え・期間・お気に入り・タグ条件
    val criteria by viewModel.criteria.collectAsState()
    val candidateCount by viewModel.candidateCount.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    var showRefine by remember { mutableStateOf(false) }

    // ★追加: タグ/分野タップからの初期クエリを反映
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) viewModel.onQueryChange(initialQuery)
    }

    val types = listOf(
        null to "すべて",
        "thought" to "メモ", "definition" to "単語帳", "webpage" to "Web",
        "book" to "本", "video" to "動画", "document" to "文書",
        "media" to "メディア", "person" to "人物", "org" to "組織",
        "place" to "場所", "event" to "イベント", "liked" to "いいね",
        "ai_conv" to "AI会話"
    )
    val modes = listOf(
        SearchMode.HYBRID to "統合",
        SearchMode.FULLTEXT to "全文",
        SearchMode.SEMANTIC to "意味",
        SearchMode.LIKE to "部分一致"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(stickySnackbar) },
        topBar = {
            TopAppBar(
                title = { Text("検索", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    // 絞り込みパネルの開閉。条件が既定以外なら強調
                    IconButton(onClick = { showRefine = !showRefine }) {
                        Icon(
                            Icons.Default.Tune, contentDescription = "並べ替え・絞り込み",
                            tint = if (!criteria.isDefault || showRefine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("キーワードを検索…") },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "クリア")
                        }
                    }
                }
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modes.forEach { (mode, label) ->
                    FilterChip(
                        selected = searchMode == mode,
                        onClick = { viewModel.setSearchMode(mode) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                types.forEach { (type, label) ->
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = { viewModel.setTypeFilter(type) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                // ★wt56: 付箋ありのみ
                FilterChip(
                    selected = onlyWithStickyNotes,
                    onClick = { viewModel.toggleOnlyWithStickyNotes() },
                    label = { Text("📝 付箋あり", style = MaterialTheme.typography.labelSmall) }
                )
            }
            // ★mismatch §3.4: 並べ替え・期間・お気に入り・タグ(AND)。条件はメモリ上で即時反映(再検索なし)
            if (showRefine) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("並べ替え", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SearchRefiner.SortKey.entries.forEach { key ->
                            FilterChip(
                                selected = criteria.sort == key,
                                onClick = { viewModel.setSort(key) },
                                label = { Text(key.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Text("期間(更新日)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SearchRefiner.Period.entries.forEach { p ->
                            FilterChip(
                                selected = criteria.period == p,
                                onClick = { viewModel.setPeriod(p) },
                                label = { Text(p.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        FilterChip(
                            selected = criteria.favoritesOnly,
                            onClick = { viewModel.setFavoritesOnly(!criteria.favoritesOnly) },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("お気に入りのみ", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (allTags.isNotEmpty()) {
                        Text("タグ(すべて含む)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allTags.forEach { t ->
                                FilterChip(
                                    selected = t.name in criteria.tags,
                                    onClick = { viewModel.toggleTag(t.name) },
                                    label = { Text("#${t.name}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    if (!criteria.isDefault) {
                        TextButton(onClick = { viewModel.clearCriteria() }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("条件をクリア", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (candidateCount > 0 && !isSearching) {
                // 候補は関連度上位100件。条件で絞られた場合は「候補N件中M件」と正直に出す
                Text(
                    if (results.size == candidateCount) "${results.size}件ヒット"
                    else "候補${candidateCount}件中 ${results.size}件（条件適用）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (results.isEmpty() && !isSearching) {
                    item {
                        EmptyState(
                            emoji = "🔍",
                            title = if (query.isBlank()) "キーワードを入力してください"
                            else if (candidateCount > 0) "条件に一致する結果がありません"
                            else "「$query」に一致する結果がありません",
                            subtitle = if (candidateCount > 0) "候補${candidateCount}件が条件で除外されました。条件をクリアしてください"
                            else if (searchMode == SearchMode.SEMANTIC)
                                "意味検索にはGemini APIキーの設定が必要です（設定画面）"
                            else null
                        )
                    }
                }
                items(results, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = { onNavigateToEntry(entry.id) },
                        onFavoriteClick = { viewModel.toggleFavorite(entry.id) },   // 従来は空クロージャだった
                        stickyNotes = stickyNotesByEntry[entry.id].orEmpty(),
                        stickyNoteActions = stickyBinding.forEntry(entry.id)
                    )
                }
            }
        }
    }
}
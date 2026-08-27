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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.brain.search.SearchMode
import com.thuvstu.personalencyclopedia.ui.component.EmptyState
import com.thuvstu.personalencyclopedia.ui.component.EntryCard
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
    val results by viewModel.results.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

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
        topBar = {
            TopAppBar(
                title = { Text("検索", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
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
            }
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (results.isNotEmpty() && !isSearching) {
                Text(
                    "${results.size}件ヒット",
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
                            else "「$query」に一致する結果がありません",
                            subtitle = if (searchMode == SearchMode.SEMANTIC)
                                "意味検索にはGemini APIキーの設定が必要です（設定画面）"
                            else null
                        )
                    }
                }
                items(results, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = { onNavigateToEntry(entry.id) },
                        onFavoriteClick = {}
                    )
                }
            }
        }
    }
}
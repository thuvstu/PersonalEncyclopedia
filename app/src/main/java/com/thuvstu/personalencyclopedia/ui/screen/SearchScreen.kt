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
import com.thuvstu.personalencyclopedia.ui.component.EmptyState
import com.thuvstu.personalencyclopedia.ui.component.EntryCard
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeLabelJa
import com.thuvstu.personalencyclopedia.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToEntry: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()

    val types = listOf(
        null to "すべて",
        "thought" to "メモ",
        "definition" to "単語帳"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("キーワードを検索…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "クリア")
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Type filter chips
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { (type, label) ->
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = { viewModel.setTypeFilter(type) },
                        label = { Text(label) }
                    )
                }
            }

            // Results
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (results.isEmpty()) {
                    item {
                        EmptyState(
                            emoji = "🔍",
                            title = if (query.isBlank()) "エントリーがありません" else "「$query」に一致する結果がありません"
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
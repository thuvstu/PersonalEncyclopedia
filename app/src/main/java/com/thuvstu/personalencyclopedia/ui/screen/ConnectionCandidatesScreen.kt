package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.component.EmptyState
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.viewmodel.ConnectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionCandidatesScreen(
    onBack: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val candidates by viewModel.candidates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新着接続候補") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (candidates.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🔗",
                        title = "接続候補はありません",
                        subtitle = "意味検索が有効になると、類似度の高いエントリーが自動的に候補として提案されます"
                    )
                }
            } else {
                items(candidates, key = { it.candidate.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Similarity badge
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("類似度 ${String.format("%.0f", item.candidate.similarity * 100)}%") }
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Entry A
                            Text(
                                "${entryTypeIcon(item.entryA?.type ?: "")} ${item.entryA?.title ?: "?"}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "↕ ${item.candidate.suggestedType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            // Entry B
                            Text(
                                "${entryTypeIcon(item.entryB?.type ?: "")} ${item.entryB?.title ?: "?"}",
                                style = MaterialTheme.typography.titleSmall
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.approve(item.candidate.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("承認")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.reject(item.candidate.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("却下")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
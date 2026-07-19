package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.component.EmptyState
import com.thuvstu.personalencyclopedia.ui.component.EntryCard
import com.thuvstu.personalencyclopedia.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToEntry: (String) -> Unit,
    onNavigateToNewThought: () -> Unit,
    onNavigateToNewDefinition: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSrs: () -> Unit,
    onNavigateToQuiz: () -> Unit,
    onNavigateToImport: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val recentEntries by viewModel.recentEntries.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val quickAddTitle by viewModel.quickAddTitle.collectAsState()

    var showQuickAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Encyclopedia") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick add
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickAddTitle,
                            onValueChange = viewModel::onQuickAddTitleChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("何か思いついたら...") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = viewModel::quickAddThought,
                            enabled = quickAddTitle.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "保存")
                        }
                    }
                }
            }

            // Stats
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem("合計", "$totalCount 件")
                        StatItem("復習期限", "$dueCount 枚")
                        StatItem("クイズ", "$quizCount 問")
                    }
                }
            }

// Quick actions (SRS / Quiz buttons)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onNavigateToSrs,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📖 復習 ($dueCount)")
                    }
                    FilledTonalButton(
                        onClick = onNavigateToQuiz,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("✏️ クイズ")
                    }
                }
            }

            // Section header
            item {
                Text(
                    text = "最近の追加",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Entries
            if (recentEntries.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "📝",
                        title = "まだエントリーがありません",
                        subtitle = "上のフォームから最初のメモを追加しましょう"
                    )
                }
            } else {
                items(recentEntries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = { onNavigateToEntry(entry.id) },
                        onFavoriteClick = { viewModel.toggleFavorite(entry.id) }
                    )
                }
            }
        }
    }

    // Quick Add Dialog
    if (showQuickAddDialog) {
        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text("新規追加") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            showQuickAddDialog = false
                            onNavigateToNewThought()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("💭 メモを書く")
                    }
                    FilledTonalButton(
                        onClick = {
                            showQuickAddDialog = false
                            onNavigateToNewDefinition()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📖 単語帳に追加")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
package com.thuvstu.personalencyclopedia.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.component.EmptyState
import com.thuvstu.personalencyclopedia.ui.component.EntryCard
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeColor
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeLabelJa
import com.thuvstu.personalencyclopedia.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToEntry: (String) -> Unit,
    onNavigateToNewThought: () -> Unit,
    onNavigateToNewDefinition: () -> Unit,
    onNavigateToNewEntry: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSrs: () -> Unit,
    onNavigateToQuiz: () -> Unit,
    onNavigateToQuizNew: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToConnectionCandidates: () -> Unit,
    onNavigateToConnections: () -> Unit,
    onNavigateToWhiteboard: () -> Unit,
    onNavigateToWiki: () -> Unit,          // ★追加
    onNavigateToQuizList: () -> Unit,      // ★追加
    viewModel: DashboardViewModel = hiltViewModel()

) {
    val scrapeState by viewModel.scrapeState.collectAsState()
    var urlInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val recentEntries by viewModel.recentEntries.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val dueCount by viewModel.dueCount.collectAsState()
    val quizCount by viewModel.quizCount.collectAsState()
    val pendingConnectionCount by viewModel.pendingConnectionCount.collectAsState()
    val quickAddTitle by viewModel.quickAddTitle.collectAsState()
    var showQuickAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scrapeState) {
        when (val s = scrapeState) {
            is DashboardViewModel.ScrapeState.Done -> {
                viewModel.resetScrapeState()
                showQuickAddDialog = false
                urlInput = ""
                if (s.deduplicated) {
                    Toast.makeText(context, "既に保存済み — 開きます", Toast.LENGTH_SHORT).show()
                }
                onNavigateToEntry(s.entryId)
            }
            is DashboardViewModel.ScrapeState.Failed -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                viewModel.resetScrapeState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Encyclopedia") },
                actions = {
                    IconButton(onClick = onNavigateToImport) {
                        Icon(Icons.Default.Download, contentDescription = "インポート")
                    }
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            placeholder = { Text("思いついたことを記録...") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = viewModel::quickAddThought,
                            enabled = quickAddTitle.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "送信")
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        DashboardStatItem("総計", "$totalCount 件")
                        DashboardStatItem("今日の復習", "$dueCount 件")
                        DashboardStatItem("クイズ", "$quizCount 問")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onNavigateToSrs,
                            modifier = Modifier.weight(1f)
                        ) { Text("📚 復習 ($dueCount)") }
                        FilledTonalButton(
                            onClick = onNavigateToQuiz,
                            modifier = Modifier.weight(1f)
                        ) { Text("📝 クイズ") }
                    }
                    // ★最適化R4: 重複していた「ホワイトボード」「クイズ一覧」ボタンを統合し2列構成に整理
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onNavigateToWhiteboard,
                            modifier = Modifier.weight(1f)
                        ) { Text("🗒️ ホワイトボード") }
                        FilledTonalButton(
                            onClick = onNavigateToWiki,
                            modifier = Modifier.weight(1f)
                        ) { Text("📚 Wiki") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onNavigateToQuizList,
                            modifier = Modifier.weight(1f)
                        ) { Text("📋 クイズ一覧") }
                        FilledTonalButton(
                            onClick = onNavigateToConnections,
                            modifier = Modifier.weight(1f)
                        ) { Text("🕸️ すべての接続") }
                    }
                    if (pendingConnectionCount > 0) {
                        OutlinedButton(
                            onClick = onNavigateToConnectionCandidates,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("🔗 新規接続候補 ($pendingConnectionCount 件)") }
                    }
                }
            }
            item {
                Text(
                    text = "最近追加",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            if (recentEntries.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "📖",
                        title = "まだエントリーがありません",
                        subtitle = "下の＋ボタンから最初の記録を追加しましょう"
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

    if (showQuickAddDialog) {
        val allTypes = listOf(
            "thought", "definition", "webpage", "book", "video",
            "document", "media", "person", "org", "place",
            "event", "liked", "ai_conv"
        )
        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text("クイック追加") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("URLを取り込む") },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.scrapeUrl(urlInput) },
                        enabled = urlInput.isNotBlank() &&
                                scrapeState !is DashboardViewModel.ScrapeState.Loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (scrapeState is DashboardViewModel.ScrapeState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Webページとして保存")
                    }
                    HorizontalDivider()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height(260.dp).fillMaxWidth(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(allTypes) { type ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        showQuickAddDialog = false
                                        onNavigateToNewEntry(type)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(entryTypeIcon(type), fontSize = 26.sp, color = entryTypeColor(type))
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    entryTypeLabelJa(type),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        showQuickAddDialog = false
                                        onNavigateToQuizNew()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text("📝", fontSize = 26.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("クイズ作成", style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center)
                            }
                        }
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        showQuickAddDialog = false
                                        onNavigateToQuizList()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text("📋", fontSize = 26.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("クイズ一覧", style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) { Text("閉じる") }
            }
        )
    }
}

@Composable
private fun DashboardStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
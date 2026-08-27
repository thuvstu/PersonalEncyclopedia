package com.thuvstu.personalencyclopedia.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    onNavigateToTodo: () -> Unit,          // ★v15.0 §11.11
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
    val activeTaskCount by viewModel.activeTaskCount.collectAsState()
    val estimationBias by viewModel.estimationBias.collectAsState()
    var showQuickAddDialog by remember { mutableStateOf(false) }
    val seedState by viewModel.seedState.collectAsState()
    val isSeeding by viewModel.isSeeding.collectAsState()

    LaunchedEffect(seedState) {
        seedState?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearSeedState() }
    }

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
                title = {
                    Text(
                        "Personal Encyclopedia",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "検索")
                    }
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
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── クイック追加 ──
            item(key = "quick-add") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedTextField(
                            value = quickAddTitle,
                            onValueChange = viewModel::onQuickAddTitleChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("思いついたことを記録...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.primaryContainer,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilledIconButton(
                            onClick = viewModel::quickAddThought,
                            enabled = quickAddTitle.isNotBlank(),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "送信", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── 統計カード ──
            item(key = "stats") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardStatCard(
                        emoji = "📦",
                        label = "総計",
                        value = "$totalCount",
                        unit = "件",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardStatCard(
                        emoji = "📚",
                        label = "復習",
                        value = "$dueCount",
                        unit = "件",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardStatCard(
                        emoji = "📝",
                        label = "クイズ",
                        value = "$quizCount",
                        unit = "問",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // ── 初期データ / データ概況 ──
            item(key = "data-status") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (totalCount == 0) {
                            Text(
                                "📚 初期データがまだありません",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "古典/数学/英語/地歴/法/経済の135件を一括投入できます",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = viewModel::seedInitialData,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isSeeding
                            ) {
                                if (isSeeding) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Text("📥 初期データを投入")
                            }
                        } else {
                            Text(
                                "📊 DBに ${totalCount} 件のエントリーがあります",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = viewModel::seedInitialData,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isSeeding
                            ) {
                                Text("📥 初期データを追記（重複を避けて追加）")
                            }
                        }
                    }
                }
            }

            // ── 見積もり精度レポート ──
            estimationBias.averageRatio?.let { ratio ->
                item(key = "estimation-bias") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "📏 見積もり精度: 平均 ${String.format("%.1f", ratio)}倍（${estimationBias.sampleSize}件）",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                when {
                                    ratio > 1.2 -> "⚠️ 過小評価傾向。タスク画面で実績を確認しましょう"
                                    ratio < 0.8 -> "⚠️ 余裕を持ちすぎの傾向です"
                                    else -> "✅ 良好な見積もり精度です"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── セクションヘッダー: アクション ──
            item(key = "section-actions") {
                Text(
                    text = "アクション",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                )
            }

            // ── 機能カードグリッド (2列) ──
            item(key = "action-grid") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionCard(
                            emoji = "📚",
                            label = "復習",
                            badge = if (dueCount > 0) "$dueCount" else null,
                            onClick = onNavigateToSrs,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        ActionCard(
                            emoji = "📝",
                            label = "クイズ",
                            onClick = onNavigateToQuiz,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionCard(
                            emoji = "🗒️",
                            label = "ホワイトボード",
                            onClick = onNavigateToWhiteboard,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        ActionCard(
                            emoji = "📚",
                            label = "Wiki",
                            onClick = onNavigateToWiki,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionCard(
                            emoji = "📋",
                            label = "クイズ一覧",
                            onClick = onNavigateToQuizList,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        ActionCard(
                            emoji = "🕸️",
                            label = "接続",
                            onClick = onNavigateToConnections,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionCard(
                            emoji = "✅",
                            label = "タスク",
                            badge = if (activeTaskCount > 0) "$activeTaskCount" else null,
                            onClick = onNavigateToTodo,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        ActionCard(
                            emoji = "🗄️",
                            label = "DB管理",
                            onClick = onNavigateToSettings,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 接続候補バッジ
                    if (pendingConnectionCount > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { onNavigateToConnectionCandidates() }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔗", fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "新規接続候補",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        "${pendingConnectionCount} 件の候補があります",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Text("→", fontSize = 18.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            // ── セクションヘッダー: 最近追加 ──
            item(key = "section-recent") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最近追加",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (recentEntries.isNotEmpty()) {
                        Text(
                            "${recentEntries.size}件",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
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
                        onFavoriteClick = { viewModel.toggleFavorite(entry.id) },
                        modifier = Modifier.padding(horizontal = 16.dp)
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
            title = { Text("クイック追加", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Text("新しいエントリーを作成", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height(260.dp).fillMaxWidth(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(allTypes, key = { it }) { type ->
                            QuickAddTypeChip(
                                type = type,
                                onClick = {
                                    showQuickAddDialog = false
                                    onNavigateToNewEntry(type)
                                }
                            )
                        }
                        item {
                            QuickAddTypeChip(
                                type = "📝",
                                label = "クイズ作成",
                                onClick = {
                                    showQuickAddDialog = false
                                    onNavigateToQuizNew()
                                }
                            )
                        }
                        item {
                            QuickAddTypeChip(
                                type = "📋",
                                label = "クイズ一覧",
                                onClick = {
                                    showQuickAddDialog = false
                                    onNavigateToQuizList()
                                }
                            )
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

// ★UI大改良: 統計カード — カラフルで目立つデザイン
@Composable
private fun DashboardStatCard(
    emoji: String,
    label: String,
    value: String,
    unit: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = "$label（$unit）",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

// ★UI大改良: アクションカード — アイコン+ラベル+バッジ
@Composable
private fun ActionCard(
    emoji: String,
    label: String,
    badge: String? = null,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(
                        badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ★UI大改良: クイック追加用の型チャップ
@Composable
private fun QuickAddTypeChip(
    type: String,
    label: String? = null,
    onClick: () -> Unit
) {
    val displayLabel = label ?: entryTypeLabelJa(type)
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            if (label == null) {
                Text(entryTypeIcon(type), fontSize = 24.sp, color = entryTypeColor(type))
            } else {
                Text(type, fontSize = 24.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                displayLabel,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
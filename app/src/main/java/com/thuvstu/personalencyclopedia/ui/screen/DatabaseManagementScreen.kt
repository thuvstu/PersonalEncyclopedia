package com.thuvstu.personalencyclopedia.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.backup.ExportFormat
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeColor
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeLabelJa
import com.thuvstu.personalencyclopedia.viewmodel.DatabaseManagementViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseManagementScreen(
    onBack: () -> Unit,
    viewModel: DatabaseManagementViewModel = hiltViewModel()
) {
    val typeCounts by viewModel.typeCounts.collectAsState()
    val totalEntries by viewModel.totalEntries.collectAsState()
    val connectionCount by viewModel.connectionCount.collectAsState()
    val quizCount by viewModel.quizCount.collectAsState()
    val reviewCount by viewModel.reviewCount.collectAsState()
    val storage by viewModel.storage.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val context = LocalContext.current

    val dateStr = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()) }
    val mdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { it?.let { viewModel.export(it, ExportFormat.MARKDOWN) } }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { it?.let { viewModel.export(it, ExportFormat.CSV) } }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { it?.let { viewModel.export(it, ExportFormat.JSON) } }


    val safBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        // Driveアプリのフォルダ選択時にも永続的に書き込めるよう権限を固定
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            viewModel.exportBackupToSaf(it)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.exportMessage.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("データベース管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshStorage() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "再読み込み")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── ストレージ監視 ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💾 ストレージ", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    storage?.let { s ->
                        StorageRow("Room DB 本体", fmtBytes(s.dbBytes))
                        StorageRow("WAL ログ", fmtBytes(s.walBytes))
                        StorageRow("バックアップ", fmtBytes(s.backupBytes))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        StorageRow("合計", fmtBytes(s.totalBytes + s.backupBytes))
                        Spacer(Modifier.height(6.dp))
                        Text(s.dbPath, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } ?: CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }

            // ── 学習データ統計 ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📊 データ統計", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround) {
                        StatMini("エントリー", "$totalEntries")
                        StatMini("接続", "$connectionCount")
                        StatMini("クイズ", "$quizCount")
                        StatMini("復習ログ", "$reviewCount")
                    }
                }
            }

            // ── 型別件数 ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🗂️ 型別エントリー数", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    if (typeCounts.isEmpty()) {
                        Text("データなし", style = MaterialTheme.typography.bodySmall)
                    }
                    typeCounts.forEach { tc ->
                        val ratio = if (totalEntries > 0)
                            tc.cnt.toFloat() / totalEntries else 0f
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(entryTypeIcon(tc.type), modifier = Modifier.width(28.dp))
                            Text(entryTypeLabelJa(tc.type),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(88.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.weight(1f).height(8.dp)
                                    .padding(horizontal = 8.dp),
                                color = entryTypeColor(tc.type)
                            )
                            Text("${tc.cnt}", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(48.dp))
                        }
                    }
                }
            }

            // ── エクスポート(§6.3 可搬バックアップ) ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📤 可搬エクスポート", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "オープン標準形式で保存します。10年後にアプリが存在しなくても読み書きできる保険です。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { mdLauncher.launch("encyclopedia_$dateStr.md") },
                            enabled = !isExporting, modifier = Modifier.weight(1f)
                        ) { Text("Markdown") }
                        Button(
                            onClick = { csvLauncher.launch("encyclopedia_defs_$dateStr.csv") },
                            enabled = !isExporting, modifier = Modifier.weight(1f)
                        ) { Text("CSV") }
                        Button(
                            onClick = { jsonLauncher.launch("encyclopedia_$dateStr.json") },
                            enabled = !isExporting, modifier = Modifier.weight(1f)
                        ) { Text("JSON") }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { safBackupLauncher.launch(null) },
                            enabled = !isExporting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📁 フォルダへバックアップ（SAF / Drive対応）")
                        }
                    }
                    if (isExporting) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("エクスポート中...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatMini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun fmtBytes(b: Long) = when {
    b < 1024 -> "${b}B"
    b < 1024 * 1024 -> "%.1fKB".format(b / 1024.0)
    else -> "%.1fMB".format(b / (1024.0 * 1024.0))
}
package com.thuvstu.personalencyclopedia.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.viewmodel.ServerViewModel
import kotlinx.coroutines.flow.collectLatest

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToDbManagement: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val isRunning by viewModel.isRunning.collectAsState()
    val token by viewModel.token.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val autoConnect by viewModel.autoConnectEnabled.collectAsState()
    val threshold by viewModel.autoConnectThreshold.collectAsState()
    val safUri by viewModel.backupSafUri.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val lastBackupStatus by viewModel.lastBackupStatus.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var apiKeyInput by remember { mutableStateOf("") }

    val safFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            viewModel.setBackupSafUri(it.toString())
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.restoreFromBackup(it)
        }
    }

    val isBackupOutdated = remember(lastBackupTime) {
        val time = lastBackupTime
        if (time == null || time <= 0) true
        else (System.currentTimeMillis() - time) > 24 * 60 * 60 * 1000L
    }

    val formattedBackupTime = remember(lastBackupTime) {
        lastBackupTime?.let {
            SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(it))
        } ?: "未実施"
    }

    LaunchedEffect(Unit) {
        viewModel.actionMessage.collectLatest {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
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
            // ── SAF バックアップ（GAP-1 / §6.5）──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💾 自動バックアップ (SAF)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (!isBackupOutdated && lastBackupStatus?.startsWith("SUCCESS") == true) {
                            AssistChip(
                                onClick = {},
                                label = { Text("正常稼働") },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                            )
                        } else {
                            AssistChip(
                                onClick = {},
                                label = { Text("要確認") },
                                leadingIcon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (safUri.isNullOrBlank()) "⚠️ バックアップ先フォルダが未設定です。端末紛失時のデータ消失を防ぐため、Google Driveまたは外部フォルダを設定してください。"
                        else "バックアップ先: ${Uri.decode(safUri)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (safUri.isNullOrBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("最終バックアップ: $formattedBackupTime (${lastBackupStatus ?: "未記録"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isBackupOutdated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { safFolderLauncher.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Folder, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (safUri.isNullOrBlank()) "フォルダを選択" else "フォルダ変更")
                        }
                        if (!safUri.isNullOrBlank()) {
                            OutlinedButton(onClick = { viewModel.setBackupSafUri(null) }) {
                                Text("解除")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.backupNow() },
                            modifier = Modifier.weight(1f)
                        ) { Text("今すぐバックアップ") }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("暗号化バックアップから復元") }
                    }
                }
            }

            // ── Gemini API ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🤖 Gemini API", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (apiKey.isNullOrBlank()) "未設定 — Embedding・LLM・意味的採点が無効です"
                        else "設定済み: ${apiKey!!.take(8)}…（保存済み）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (apiKey.isNullOrBlank()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        singleLine = true,
                        placeholder = { Text("AIza…（Google AI Studioで無料取得）") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.saveApiKey(apiKeyInput)
                            apiKeyInput = ""
                        },
                        enabled = apiKeyInput.isNotBlank()
                    ) { Text("保存") }
                }
            }

            // ── 自動接続（§5.5.3）──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🔗 自動接続候補", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "類似エントリーをconnection_candidateに提案（承認制・直接接続はしない）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoConnect, onCheckedChange = viewModel::setAutoConnect)
                    }
                    if (autoConnect) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("類似度しきい値: %.2f".format(threshold),
                            style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = threshold,
                            onValueChange = viewModel::setThreshold,
                            valueRange = 0.70f..0.98f
                        )
                    }
                }
            }

            // ── Ktorサーバー ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🌐 Ktorローカルサーバー", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (isRunning) "起動中（PCからLAN経由アクセス可）" else "停止中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = isRunning, onCheckedChange = { viewModel.toggleServer() })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("🔑 アクセストークン", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(token ?: "生成中…", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            token?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("コピー")
                        }
                        OutlinedButton(onClick = { viewModel.regenerateToken() }) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("再発行")
                        }
                    }
                }
            }

            // ── メンテナンス ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🛠️ メンテナンス", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.rebuildSearchIndex() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("検索インデックス再構築（FTS4+search_document）") }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.exportNow() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("今すぐ可搬エクスポート（Markdown/JSON）") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToDbManagement,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🗄️ データベース管理") }
                }
            }

            // ── アプリ情報 ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ℹ️ アプリ情報", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Personal Encyclopedia v0.5.0", style = MaterialTheme.typography.bodyMedium)
                    Text("Phase 3 — 知識接続・全13型・統合エディタ",
                        style = MaterialTheme.typography.bodySmall)
                    Text("データは端末内SQLiteに保存。UIが消えてもデータは無傷（§6.4）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
package com.thuvstu.personalencyclopedia.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward   // ★追加
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.viewmodel.ImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { viewModel.importCsv(it) }
    }
    val mdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { viewModel.importMarkdown(it) }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { viewModel.importJson(it) }
    }
    val urlListLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { viewModel.importUrlList(it) }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let { treeUri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* 権限保持に失敗しても単発で試す */ }
            viewModel.importSafFolder(treeUri)
        }
    }
    val bookmarkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { viewModel.importBookmarkHtml(it) }
    }
    var showObsidianDialog by remember { mutableStateOf(false) }
    var obsidianTitle by remember { mutableStateOf("") }
    var obsidianContent by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        when (val s = state) {
            is ImportViewModel.ImportState.Done ->
                Toast.makeText(context, s.summaryText, Toast.LENGTH_LONG).show()
            is ImportViewModel.ImportState.Error ->
                Toast.makeText(context, "エラー: ${s.message}", Toast.LENGTH_LONG).show()
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("インポート") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── セクション1: ファイル一括 ──
            Text("📥 ファイルから一括", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            ImportRow("📊 CSV（単語帳）", "term/definition 列。既存の用語はスキップ") {
                csvLauncher.launch(arrayOf("text/csv", "*/*"))
            }
            ImportRow("📝 Markdown（メモ）", "H1/H2 見出しごとに1エントリー") {
                mdLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*"))
            }
            ImportRow("🧾 JSON（エクスポートと対称）", "DB管理の出力を再取り込み") {
                jsonLauncher.launch(arrayOf("application/json", "*/*"))
            }
            ImportRow("🔗 URLリスト", "1行1URL。既存URLはスキップ") {
                urlListLauncher.launch(arrayOf("text/plain", "text/csv", "*/*"))
            }
            ImportRow("🔖 bookmark.html", "ブラウザのエクスポート。フォルダ・登録日時を復元（本文取得なし高速登録）") {
                bookmarkLauncher.launch(arrayOf("text/html", "*/*"))
            }
            ImportRow("📁 フォルダ一括（SAF）", "フォルダ内のmd/txt/csv/json/htmlを拡張子で振り分け（Drive API不使用）") {
                folderLauncher.launch(null)
            }

            HorizontalDivider()

            // ── セクション2: 貼り付け ──
            Text("📋 テキストから", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            ImportRow("🟣 Obsidian ノート", "[[wiki-link]] を解析して参照接続を作成") {
                showObsidianDialog = true
            }

            HorizontalDivider()

            // ── セクション3: AI ──
            Text("🤖 AI で生成", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            ImportRow("クイズ一括生成", "登録済みエントリーからGeminiで4択問題を作成") {
                viewModel.generateQuizzesFromEntries()
            }

            // ステータス
            when (val s = state) {
                is ImportViewModel.ImportState.Importing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("処理中...")
                    }
                }
                is ImportViewModel.ImportState.Done -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("✅ 完了", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(s.summaryText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                else -> {}
            }
        }
    }

    if (showObsidianDialog) {
        AlertDialog(
            onDismissRequest = { showObsidianDialog = false },
            title = { Text("Obsidian ノート取込") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = obsidianTitle,
                        onValueChange = { obsidianTitle = it },
                        label = { Text("ノートタイトル") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = obsidianContent,
                        onValueChange = { obsidianContent = it },
                        label = { Text("ノート本文（[[wiki-link]]含む）") },
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (obsidianTitle.isNotBlank()) {
                            viewModel.importObsidianNote(obsidianTitle, obsidianContent)
                            showObsidianDialog = false
                            obsidianTitle = ""; obsidianContent = ""
                        }
                    },
                    enabled = obsidianTitle.isNotBlank()
                ) { Text("インポート") }
            },
            dismissButton = {
                TextButton(onClick = { showObsidianDialog = false }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun ImportRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}
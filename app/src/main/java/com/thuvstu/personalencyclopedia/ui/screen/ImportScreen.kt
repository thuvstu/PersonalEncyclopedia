package com.thuvstu.personalencyclopedia.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importCsv(it) }
    }

    val mdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importMarkdown(it) }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is ImportViewModel.ImportState.Done -> {
                Toast.makeText(
                    context,
                    "インポート完了: ${s.result.successCount}件成功, ${s.result.errorCount}件エラー",
                    Toast.LENGTH_LONG
                ).show()
            }
            is ImportViewModel.ImportState.Error -> {
                Toast.makeText(context, "エラー: ${s.message}", Toast.LENGTH_LONG).show()
            }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CSV Import
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📊 CSVインポート（単語帳）", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "列: term, reading(任意), definition, field(任意)\n" +
                                "ヘッダー行必須。UTF-8エンコーディング。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                        },
                        enabled = state !is ImportViewModel.ImportState.Importing
                    ) {
                        Text("CSVファイルを選択")
                    }
                }
            }

            // Markdown Import
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📝 Markdownインポート（メモ）", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "H1/H2見出しごとに1エントリーとして取り込みます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            mdLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*"))
                        },
                        enabled = state !is ImportViewModel.ImportState.Importing
                    ) {
                        Text("Markdownファイルを選択")
                    }
                }
            }

            // Status
            when (val s = state) {
                is ImportViewModel.ImportState.Importing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("インポート中...")
                    }
                }
                is ImportViewModel.ImportState.Done -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("✅ 結果", style = MaterialTheme.typography.titleSmall)
                            Text("成功: ${s.result.successCount} 件")
                            if (s.result.errorCount > 0) {
                                Text(
                                    "エラー: ${s.result.errorCount} 件",
                                    color = MaterialTheme.colorScheme.error
                                )
                                s.result.errors.take(5).forEach { err ->
                                    Text(
                                        "  • $err",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
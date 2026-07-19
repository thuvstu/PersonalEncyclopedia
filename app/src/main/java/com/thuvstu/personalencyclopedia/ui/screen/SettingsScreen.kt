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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val isRunning by viewModel.isRunning.collectAsState()
    val token by viewModel.token.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server section
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🌐 Ktorローカルサーバー", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRunning) "サーバー稼働中" else "サーバー停止中",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (isRunning) {
                                Text(
                                    text = "ポート: 8080",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isRunning,
                            onCheckedChange = { viewModel.toggleServer() }
                        )
                    }
                }
            }

            // Token section
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔑 アクセストークン", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = token ?: "生成中...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            token?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("コピー")
                        }
                        OutlinedButton(onClick = { viewModel.regenerateToken() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("再生成")
                        }
                    }
                }
            }

            // App info
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ℹ️ アプリ情報", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Personal Encyclopedia v0.1.0", style = MaterialTheme.typography.bodyMedium)
                    Text("Phase 0 — 毎日使える最小版", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "データはすべてこの端末内のSQLiteデータベースに保存されています。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
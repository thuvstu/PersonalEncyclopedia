package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.db.ReadOnlySqlExecutor
import com.thuvstu.personalencyclopedia.viewmodel.SqlExplorerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqlExplorerScreen(
    onBack: () -> Unit,
    viewModel: SqlExplorerViewModel = hiltViewModel()
) {
    val schema by viewModel.schema.collectAsState()
    val columns by viewModel.columns.collectAsState()
    val selectedTable by viewModel.selectedTable.collectAsState()
    val dbStats by viewModel.dbStats.collectAsState()
    val integrity by viewModel.integrity.collectAsState()
    val result by viewModel.result.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val savedQueries by viewModel.savedQueries.collectAsState()

    var queryText by remember { mutableStateOf("SELECT * FROM entry LIMIT 10;") }
    var saveName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SQL Explorer（読み取り専用）") },
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
            // ── 読み取り専用警告（§11.12）──
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "この画面はデバッグ用です。SELECT / WITH のみ実行でき、" +
                            "接続は PRAGMA query_only で書き込み禁止になります。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── クエリ実行 ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚡ クエリ実行", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        label = { Text("SELECT / WITH 文") },
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.runQuery(queryText) },
                            enabled = !isRunning && queryText.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text("実行")
                        }
                        OutlinedButton(
                            onClick = { showSaveDialog = true },
                            enabled = queryText.isNotBlank()
                        ) {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("保存")
                        }
                    }
                }
            }

            // ── 実行結果 ──
            result?.let { r ->
                when (r) {
                    is ReadOnlySqlExecutor.SqlExecutionResult.Error -> {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                "❌ ${r.message}",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    is ReadOnlySqlExecutor.SqlExecutionResult.Success -> {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "✅ ${r.rows.size}行 / ${r.elapsedMs}ms",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                if (r.columns.isEmpty()) {
                                    Text("結果なし", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        r.columns.forEach { col ->
                                            Text(
                                                col,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.width(140.dp).padding(4.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider()
                                    r.rows.forEach { row ->
                                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                            row.forEach { cell ->
                                                Text(
                                                    cell.take(60),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.width(140.dp).padding(4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── スキーマブラウザ ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🗂️ スキーマブラウザ", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    schema.forEach { obj ->
                        val selected = selectedTable == obj.name
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable { viewModel.selectTable(obj.name) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (obj.type == "テーブル") "📄" else "👁️",
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(obj.name, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.weight(1f))
                                Text(obj.type, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected && columns.isNotEmpty()) {
                                columns.forEach { col ->
                                    Text(
                                        "  ${if (col.primaryKey) "🔑" else "•"} ${col.name}  " +
                                            "${col.type}${if (col.notNull) " NOT NULL" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 36.dp, bottom = 2.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }

            // ── DB統計 ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📊 DB統計", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    dbStats.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(k, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(v, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    integrity?.let {
                        Text(
                            if (it == "ok") "✅ integrity_check: ok" else "⚠️ integrity_check: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it == "ok") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedButton(onClick = { viewModel.runIntegrityCheck() }, modifier = Modifier.fillMaxWidth()) {
                        Text("integrity_check を実行")
                    }
                }
            }

            // ── 保存済みクエリ ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💾 保存済みクエリ", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (savedQueries.isEmpty()) {
                        Text("まだ保存されていません", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    savedQueries.forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { queryText = q.sql }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(q.name, style = MaterialTheme.typography.bodySmall)
                                Text(q.sql.take(60), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.deleteQuery(q.id) }) {
                                Icon(Icons.Default.Delete, null, Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("クエリを保存") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text("名前（例: 直近エントリー一覧）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(queryText.take(80), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveQuery(saveName, queryText)
                        saveName = ""
                        showSaveDialog = false
                    },
                    enabled = saveName.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("キャンセル") }
            }
        )
    }
}

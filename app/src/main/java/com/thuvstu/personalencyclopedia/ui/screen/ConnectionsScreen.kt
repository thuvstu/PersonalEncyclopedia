package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.db.dao.ConnectionListItem
import com.thuvstu.personalencyclopedia.db.entity.ConnectionTypeDefEntity
import com.thuvstu.personalencyclopedia.ui.component.EmptyState
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.viewmodel.ConnectionViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    onBack: () -> Unit,
    onNavigateToEntry: (String) -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val connections by viewModel.allConnections.collectAsState()
    val typeDefs by viewModel.typeDefs.collectAsState()
    val filter by viewModel.relationFilter.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ナレッジ接続") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 関係種別フィルタチップ
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { viewModel.setRelationFilter(null) },
                    label = { Text("すべて", style = MaterialTheme.typography.labelSmall) }
                )
                typeDefs.forEach { def ->
                    FilterChip(
                        selected = filter == def.name,
                        onClick = {
                            viewModel.setRelationFilter(
                                if (filter == def.name) null else def.name
                            )
                        },
                        label = { Text(def.labelJa, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Text(
                "${connections.size} 件の接続",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (connections.isEmpty()) {
                EmptyState(
                    emoji = "🕸️",
                    title = "接続がまだありません",
                    subtitle = "エントリー詳細の「接続」セクションから手動接続を作成できます"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(connections, key = { it.connectionId }) { item ->
                        ConnectionItem(
                            item = item,
                            typeDefs = typeDefs,
                            onNavigate = onNavigateToEntry,
                            onDelete = { viewModel.remove(item.connectionId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionItem(
    item: ConnectionListItem,
    typeDefs: List<ConnectionTypeDefEntity>,
    onNavigate: (String) -> Unit,
    onDelete: () -> Unit
) {
    val typeDef = typeDefs.find { it.name == item.relationType }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Entry A
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(item.entryAId) }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(entryTypeIcon(item.entryAType),
                            style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Text(item.entryATitle,
                            style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    // 関係ラベル
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Text(
                            if (item.isDirected) "↓" else "↕",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    "${typeDef?.labelJa ?: item.relationType} · ${(item.strength * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                    // Entry B
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(item.entryBId) }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(entryTypeIcon(item.entryBType),
                            style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Text(item.entryBTitle,
                            style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "接続を削除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp))
            }
            Text(
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                    .format(Date(item.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            )
        }
    }
}
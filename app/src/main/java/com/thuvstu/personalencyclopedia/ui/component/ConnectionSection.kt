package com.thuvstu.personalencyclopedia.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thuvstu.personalencyclopedia.db.dao.ConnectionWithEntry
import com.thuvstu.personalencyclopedia.db.entity.ConnectionTypeDefEntity
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionSection(
    connections: List<ConnectionWithEntry>,
    typeDefs: List<ConnectionTypeDefEntity>,
    onRemoveConnection: (String) -> Unit,
    onNavigateToEntry: (String) -> Unit,
    onAddConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔗 接続", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onAddConnection, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "接続を追加", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (connections.isEmpty()) {
                Text(
                    "接続なし。他のエントリーと関連付けて知識を繋げましょう。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    connections.forEach { conn ->
                        val typeDef = typeDefs.find { it.name == conn.relationType }
                        InputChip(
                            selected = false,
                            onClick = { onNavigateToEntry(conn.otherEntryId) },
                            label = {
                                Text(
                                    "${entryTypeIcon(conn.otherEntryType)} ${conn.otherEntryTitle.take(15)}" +
                                            "（${typeDef?.labelJa ?: conn.relationType}）",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveConnection(conn.connectionId) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "削除", modifier = Modifier.size(14.dp))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
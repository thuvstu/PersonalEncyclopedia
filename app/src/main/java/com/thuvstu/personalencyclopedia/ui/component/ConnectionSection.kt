package com.thuvstu.personalencyclopedia.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thuvstu.personalencyclopedia.db.dao.ConnectionWithEntry
import com.thuvstu.personalencyclopedia.db.entity.ConnectionTypeDefEntity
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon

/**
 * 接続セクション（★wt58 再設計）。
 *
 * 旧: 全接続を1つの FlowRow に平置き（強度の数字付き）。高校シードで1カード10〜20本になると意味が読めない。
 * 新: **関係の意味でグループ化**し、学習動線として読める順に並べる。
 *   1. ⬅ 前提（先にやる）      prerequisite で自分が終点
 *   2. ➡ 次に進む               prerequisite で自分が始点 / extends で自分が始点
 *   3. ↔ 対比・矛盾             contrast / contradicts
 *   4. 🔗 関連                  related
 *   5. 📎 参照・例示・その他     references / exemplifies / authored_by …（向き付き）
 * 各グループは6件まで表示、それ以上は「＋N」で展開。強度は色の濃さ（≥0.9 強調）で表し数字は出さない。
 */
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
    val typeMap = remember(typeDefs) { typeDefs.associateBy { it.name } }
    val groups = remember(connections) { groupConnections(connections) }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔗 接続", style = MaterialTheme.typography.labelLarge)
                if (connections.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text("${connections.size}本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onAddConnection, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "接続を追加", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            if (connections.isEmpty()) {
                Text(
                    "接続なし。付箋に [[カード名]] を書くか、＋で関連付けて知識を繋げましょう。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                groups.forEach { g ->
                    if (g.items.isNotEmpty()) {
                        ConnectionGroupRow(
                            group = g, typeMap = typeMap,
                            onRemoveConnection = onRemoveConnection, onNavigateToEntry = onNavigateToEntry
                        )
                    }
                }
            }
        }
    }
}

/** 表示グループ。 */
data class ConnectionGroup(val key: String, val label: String, val items: List<ConnectionWithEntry>)

fun groupConnections(all: List<ConnectionWithEntry>): List<ConnectionGroup> {
    val before = mutableListOf<ConnectionWithEntry>()
    val next = mutableListOf<ConnectionWithEntry>()
    val contrast = mutableListOf<ConnectionWithEntry>()
    val related = mutableListOf<ConnectionWithEntry>()
    val other = mutableListOf<ConnectionWithEntry>()
    for (c in all) {
        when (c.relationType) {
            "prerequisite" -> if (c.isSource) next += c else before += c
            "extends" -> if (c.isSource) next += c else before += c
            "contrast", "contradicts" -> contrast += c
            "related" -> related += c
            else -> other += c
        }
    }
    val byStrength = compareByDescending<ConnectionWithEntry> { it.strength }.thenBy { it.otherEntryTitle }
    return listOf(
        ConnectionGroup("before", "⬅ 前提（先に）", before.sortedWith(byStrength)),
        ConnectionGroup("next", "➡ 次に進む", next.sortedWith(byStrength)),
        ConnectionGroup("contrast", "↔ 対比・混同注意", contrast.sortedWith(byStrength)),
        ConnectionGroup("related", "🔗 関連", related.sortedWith(byStrength)),
        ConnectionGroup("other", "📎 参照・例示・その他", other.sortedWith(byStrength)),
    )
}

private const val GROUP_COLLAPSED = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionGroupRow(
    group: ConnectionGroup,
    typeMap: Map<String, ConnectionTypeDefEntity>,
    onRemoveConnection: (String) -> Unit,
    onNavigateToEntry: (String) -> Unit
) {
    var expanded by rememberSaveable(group.key) { mutableStateOf(false) }
    val shown = if (expanded) group.items else group.items.take(GROUP_COLLAPSED)
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group.label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(6.dp))
            Text("${group.items.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { conn -> ConnectionChip(conn, typeMap, onRemoveConnection, onNavigateToEntry, showType = group.key == "other") }
            if (group.items.size > GROUP_COLLAPSED) {
                AssistChip(
                    onClick = { expanded = !expanded },
                    label = { Text(if (expanded) "閉じる" else "＋${group.items.size - GROUP_COLLAPSED}", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
private fun ConnectionChip(
    conn: ConnectionWithEntry,
    typeMap: Map<String, ConnectionTypeDefEntity>,
    onRemoveConnection: (String) -> Unit,
    onNavigateToEntry: (String) -> Unit,
    showType: Boolean
) {
    val typeDef = typeMap[conn.relationType]
    // 有向は向きでラベルを切り替える（自分が始点なら labelJa、終点なら inverseLabelJa）
    val typeLabel = when {
        !showType -> null
        !conn.isDirected -> typeDef?.labelJa ?: conn.relationType
        conn.isSource -> typeDef?.labelJa ?: conn.relationType
        else -> typeDef?.inverseLabelJa ?: typeDef?.labelJa ?: conn.relationType
    }
    val strong = conn.strength >= 0.9f
    val containerColor = if (strong) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    InputChip(
        selected = false,
        onClick = { onNavigateToEntry(conn.otherEntryId) },
        colors = InputChipDefaults.inputChipColors(containerColor = containerColor),
        label = {
            Column {
                Text(
                    "${entryTypeIcon(conn.otherEntryType)} ${conn.otherEntryTitle}" + (if (typeLabel != null) "（$typeLabel）" else ""),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (!conn.note.isNullOrBlank()) {
                    Text(conn.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingIcon = {
            Icon(
                Icons.Default.Close, contentDescription = "削除",
                modifier = Modifier.size(14.dp).clickable { onRemoveConnection(conn.connectionId) }
            )
        }
    )
}

package com.thuvstu.personalencyclopedia.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeColor
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.viewmodel.EntryPreviewViewModel

/**
 * §12.5 定義プレビュー（v14.0で新設）。
 *
 * 自動リンク/[[wiki-link]]タップ時に、別画面へ遷移せず軽量プレビューカードをポップアップ表示する。
 * タイトル・型アイコン・`entry.summary`または`entry_definition.definition`の冒頭2〜3行を表示し、
 * 「開く」を選んだ場合のみ詳細画面へ遷移する（Wikipediaのホバープレビューに類似）。
 */
@Composable
fun EntryPreviewPopup(
    entryId: String,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: EntryPreviewViewModel = hiltViewModel()
) {
    val entry by viewModel.observeEntry(entryId).collectAsState(initial = null)
    val definition by viewModel.observeDefinition(entryId).collectAsState(initial = null)

    Popup(
        onDismissRequest = onDismiss,
        alignment = Alignment.Center
    ) {
        val e = entry
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (e == null) {
                    Text("読み込み中…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    // タイトル + 型アイコン
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(entryTypeColor(e.type).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(entryTypeIcon(e.type), style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            e.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()

                    // プレビュー本文: summary(AI生成) ?? definition の冒頭2〜3行
                    val preview = e.summary?.takeIf { it.isNotBlank() }
                        ?: definition?.definition?.takeIf { it.isNotBlank() }
                    if (!preview.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            preview.trim().lines().take(3).joinToString("\n").take(150),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (e.type != "definition") {
                        e.content?.takeIf { it.isNotBlank() }?.let { body ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                body.trim().lines().take(3).joinToString("\n").take(150),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("閉じる") }
                        TextButton(onClick = { onOpen(entryId) }) { Text("開く") }
                    }
                }
            }
        }
    }
}

package com.thuvstu.personalencyclopedia.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thuvstu.personalencyclopedia.db.entity.EntryAttachmentEntity
import java.io.File

@Composable
fun AttachmentSection(
    attachments: List<EntryAttachmentEntity>,
    onPickImage: () -> Unit,
    onRemove: (EntryAttachmentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewerTarget by remember { mutableStateOf<EntryAttachmentEntity?>(null) }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🖼️ 添付画像", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(110.dp)
            ) {
                // 追加ボタン
                item {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onPickImage),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, contentDescription = "画像を追加")
                            Text("追加", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                items(attachments, key = { it.id }) { att ->
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewerTarget = att }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(att.blobPath))
                                .crossfade(true)
                                .build(),
                            contentDescription = att.caption,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onRemove(att) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(26.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    RoundedCornerShape(13.dp)
                                )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "削除",
                                modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }

    // 全画面ビューア
    viewerTarget?.let { att ->
        Dialog(onDismissRequest = { viewerTarget = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(att.blobPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = att.caption,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit
                    )
                    att.caption?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = { viewerTarget = null },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("閉じる") }
                }
            }
        }
    }
}
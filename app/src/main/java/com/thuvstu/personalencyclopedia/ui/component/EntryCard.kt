package com.thuvstu.personalencyclopedia.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeColor
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeLabelJa
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EntryCard(
    entry: EntryEntity,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Type badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(entryTypeColor(entry.type).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entryTypeIcon(entry.type),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.content.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                entryTypeLabelJa(entry.type),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDate(entry.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onFavoriteClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "お気に入り",
                    tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private val dateFormat = ThreadLocal.withInitial {
    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
}

private fun formatDate(millis: Long): String {
    return dateFormat.get()!!.format(Date(millis))
}
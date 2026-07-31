package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeColor
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeLabelJa
import com.thuvstu.personalencyclopedia.viewmodel.EntryEditViewModel
import com.thuvstu.personalencyclopedia.viewmodel.EntryFormState
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: EntryEditViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val type = viewModel.entryType

    LaunchedEffect(Unit) { viewModel.saved.collectLatest { onSaved(it) } }

    val canSave = when (type) {
        "definition" -> form.term.isNotBlank() && form.definition.isNotBlank()
        else -> form.title.isNotBlank()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${entryTypeLabelJa(type)}${if (viewModel.isNew) "を作成" else "を編集"}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }, enabled = canSave) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 型バッジヘッダー
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                        .background(entryTypeColor(type).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Text(entryTypeIcon(type), style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.width(12.dp))
                Text(entryTypeLabelJa(type), style = MaterialTheme.typography.titleMedium,
                    color = entryTypeColor(type))
            }

            when (type) {
                "thought" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "タイトル *")
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "内容")
                    Field(form.mood, { viewModel.update { f -> f.copy(mood = it) } }, "気分(任意)")
                }
                "definition" -> {
                    Field(form.term, { viewModel.update { f -> f.copy(term = it) } }, "用語 *")
                    Field(form.reading, { viewModel.update { f -> f.copy(reading = it) } }, "読み(任意)")
                    Area(form.definition, { viewModel.update { f -> f.copy(definition = it) } }, "定義 *")
                    Field(form.field, { viewModel.update { f -> f.copy(field = it) } }, "分野(任意)", hint = "例: 数学, CS, 歴史")
                }
                "webpage" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "タイトル *")
                    Field(form.url, { viewModel.update { f -> f.copy(url = it) } }, "URL", keyboard = KeyboardType.Uri)
                    Field(form.author, { viewModel.update { f -> f.copy(author = it) } }, "著者(任意)")
                    Area(form.fullText, { viewModel.update { f -> f.copy(fullText = it) } }, "本文")
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "自分のメモ", minLines = 2)
                }
                "book" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "書名 *")
                    Field(form.authorsText, { viewModel.update { f -> f.copy(authorsText = it) } }, "著者(カンマ区切り)")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(form.isbn, { viewModel.update { f -> f.copy(isbn = it) } }, "ISBN", Modifier.weight(1f))
                        Field(form.publisher, { viewModel.update { f -> f.copy(publisher = it) } }, "出版社", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField(form.publishedYear, { viewModel.update { f -> f.copy(publishedYear = it) } }, "出版年", Modifier.weight(1f))
                        NumField(form.totalPages, { viewModel.update { f -> f.copy(totalPages = it) } }, "ページ数", Modifier.weight(1f))
                    }
                    SectionLabel("読書ステータス")
                    ChipGroup(
                        listOf("unread" to "未読", "reading" to "読書中", "done" to "読了", "dropped" to "中断"),
                        form.readStatus
                    ) { viewModel.update { f -> f.copy(readStatus = it) } }
                    SectionLabel("評価")
                    RatingRow(form.rating) { viewModel.update { f -> f.copy(rating = it) } }
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "読書メモ", minLines = 2)
                }
                "video" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "タイトル *")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(form.platform, { viewModel.update { f -> f.copy(platform = it) } }, "プラットフォーム", Modifier.weight(1f), hint = "youtube")
                        NumField(form.durationS, { viewModel.update { f -> f.copy(durationS = it) } }, "長さ(秒)", Modifier.weight(1f))
                    }
                    Field(form.channelName, { viewModel.update { f -> f.copy(channelName = it) } }, "チャンネル名")
                    Area(form.transcript, { viewModel.update { f -> f.copy(transcript = it) } }, "文字起こし")
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "メモ", minLines = 2)
                }
                "document" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "タイトル *")
                    SectionLabel("ドキュメント種別")
                    ChipGroup(
                        listOf("pdf" to "PDF", "docx" to "Word", "xlsx" to "Excel", "pptx" to "PPT",
                            "gdoc" to "GDoc", "txt" to "TXT", "md" to "MD", "other" to "他"),
                        form.docType
                    ) { viewModel.update { f -> f.copy(docType = it) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField(form.fileSizeBytes, { viewModel.update { f -> f.copy(fileSizeBytes = it) } }, "サイズ(bytes)", Modifier.weight(1f))
                        NumField(form.pageCount, { viewModel.update { f -> f.copy(pageCount = it) } }, "ページ数", Modifier.weight(1f))
                    }
                    Area(form.extractedText, { viewModel.update { f -> f.copy(extractedText = it) } }, "抽出テキスト")
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "メモ", minLines = 2)
                }
                "media" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "タイトル *")
                    SectionLabel("メディア種別")
                    ChipGroup(
                        listOf("image" to "画像", "audio" to "音声", "video_file" to "動画", "other" to "他"),
                        form.mediaType
                    ) { viewModel.update { f -> f.copy(mediaType = it) } }
                    Field(form.caption, { viewModel.update { f -> f.copy(caption = it) } }, "キャプション")
                    Area(form.ocrText, { viewModel.update { f -> f.copy(ocrText = it) } }, "OCRテキスト")
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "メモ", minLines = 2)
                }
                "person" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "表示名 *")
                    Field(form.fullName, { viewModel.update { f -> f.copy(fullName = it) } }, "正式名", hint = "空欄なら表示名を使用")
                    Field(form.aliasesText, { viewModel.update { f -> f.copy(aliasesText = it) } }, "別名(カンマ区切り)")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField(form.birthYear, { viewModel.update { f -> f.copy(birthYear = it) } }, "生年", Modifier.weight(1f))
                        NumField(form.deathYear, { viewModel.update { f -> f.copy(deathYear = it) } }, "没年", Modifier.weight(1f))
                    }
                    Field(form.nationality, { viewModel.update { f -> f.copy(nationality = it) } }, "国籍")
                    Field(form.occupationsText, { viewModel.update { f -> f.copy(occupationsText = it) } }, "職業(カンマ区切り)")
                    Area(form.biography, { viewModel.update { f -> f.copy(biography = it) } }, "略歴")
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "メモ", minLines = 2)
                }
                "org" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "表示名 *")
                    Field(form.officialName, { viewModel.update { f -> f.copy(officialName = it) } }, "正式名称")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(form.orgType, { viewModel.update { f -> f.copy(orgType = it) } }, "種別", Modifier.weight(1f), hint = "企業/官庁/NPO")
                        NumField(form.foundedYear, { viewModel.update { f -> f.copy(foundedYear = it) } }, "設立年", Modifier.weight(1f))
                    }
                    Field(form.country, { viewModel.update { f -> f.copy(country = it) } }, "国")
                    Field(form.websiteUrl, { viewModel.update { f -> f.copy(websiteUrl = it) } }, "Webサイト", keyboard = KeyboardType.Uri)
                    Area(form.description, { viewModel.update { f -> f.copy(description = it) } }, "説明")
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "メモ", minLines = 2)
                }
                "place" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "表示名 *")
                    Field(form.placeName, { viewModel.update { f -> f.copy(placeName = it) } }, "場所名")
                    Field(form.placeType, { viewModel.update { f -> f.copy(placeType = it) } }, "タイプ", hint = "都市/建物/自然")
                    Field(form.address, { viewModel.update { f -> f.copy(address = it) } }, "住所")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField(form.latitude, { viewModel.update { f -> f.copy(latitude = it) } }, "緯度", Modifier.weight(1f), decimal = true)
                        NumField(form.longitude, { viewModel.update { f -> f.copy(longitude = it) } }, "経度", Modifier.weight(1f), decimal = true)
                    }
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "メモ", minLines = 2)
                }
                "event" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "表示名 *")
                    Field(form.eventName, { viewModel.update { f -> f.copy(eventName = it) } }, "イベント名")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateField("開始日", form.startedAt,
                            { viewModel.update { f -> f.copy(startedAt = it) } }, Modifier.weight(1f))
                        DateField("終了日", form.endedAt,
                            { viewModel.update { f -> f.copy(endedAt = it) } }, Modifier.weight(1f))
                    }
                    Field(form.locationText, { viewModel.update { f -> f.copy(locationText = it) } }, "開催地")
                    Field(form.participantsText, { viewModel.update { f -> f.copy(participantsText = it) } }, "参加者(カンマ区切り)")
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "メモ", minLines = 2)
                }
                "liked" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "タイトル *")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(form.likedPlatform, { viewModel.update { f -> f.copy(likedPlatform = it) } }, "プラットフォーム", Modifier.weight(1f), hint = "x/youtube/...")
                        Field(form.likedContentType, { viewModel.update { f -> f.copy(likedContentType = it) } }, "種類", Modifier.weight(1f), hint = "post/video")
                    }
                    Field(form.likedAuthorName, { viewModel.update { f -> f.copy(likedAuthorName = it) } }, "作者")
                    Field(form.originalId, { viewModel.update { f -> f.copy(originalId = it) } }, "元投稿ID(任意)")
                    Area(form.likedFullText, { viewModel.update { f -> f.copy(likedFullText = it) } }, "本文")
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "メモ", minLines = 2)
                }
                "ai_conv" -> {
                    Field(form.title, { viewModel.update { f -> f.copy(title = it) } }, "タイトル *")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(form.aiModel, { viewModel.update { f -> f.copy(aiModel = it) } }, "モデル", Modifier.weight(1f), hint = "gemini-2.5-flash")
                        Field(form.aiTopic, { viewModel.update { f -> f.copy(aiTopic = it) } }, "トピック", Modifier.weight(1f))
                    }
                    SectionLabel("プロバイダ")
                    ChipGroup(
                        listOf("google" to "Google", "anthropic" to "Anthropic", "local" to "ローカル"),
                        form.aiProvider
                    ) { viewModel.update { f -> f.copy(aiProvider = it) } }
                    Area(form.aiMessagesText, { viewModel.update { f -> f.copy(aiMessagesText = it) } },
                        "会話内容(空行でメッセージ区切り)", minLines = 5)
                    Area(form.content, { viewModel.update { f -> f.copy(content = it) } }, "メモ", minLines = 2)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── フォーム部品 ──────────────────────────────────────────────

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true,
        placeholder = hint?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard)
    )
}

@Composable
private fun NumField(
    value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, decimal: Boolean = false
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
    )
}

@Composable
private fun Area(value: String, onChange: (String) -> Unit, label: String, minLines: Int = 3) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, minLines = minLines
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) })
        }
    }
}

@Composable
private fun RatingRow(rating: Int, onRating: (Int) -> Unit) {
    Row {
        (1..5).forEach { star ->
            IconButton(onClick = { onRating(if (star == rating) 0 else star) },
                modifier = Modifier.size(40.dp)) {
                Icon(
                    if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "$star",
                    tint = if (star <= rating) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(if (rating > 0) "$rating / 5" else "未評価",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterVertically))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    val dateText = value?.let {
        remember(it) { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(it)) }
    } ?: ""
    OutlinedTextField(
        value = dateText, onValueChange = {}, readOnly = true,
        label = { Text(label) }, modifier = modifier.fillMaxWidth().clickable { showDialog = true },
        trailingIcon = { Icon(Icons.Default.DateRange, null) }
    )
    if (showDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(onSelect); showDialog = false
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("キャンセル") } }
        ) { DatePicker(state = state) }
    }
}
package com.thuvstu.personalencyclopedia.ui.screen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.brain.task.TaskEngine
import com.thuvstu.personalencyclopedia.db.entity.TaskEntity
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.viewmodel.TaskViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToDoScreen(
    onBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val runningTask by viewModel.runningTask.collectAsState()
    val runningLog by viewModel.runningLog.collectAsState()
    val estimationBias by viewModel.estimationBias.collectAsState()
    val forcedChoiceTask by viewModel.forcedChoiceTask.collectAsState()
    val timeboxExpiredTask by viewModel.timeboxExpiredTask.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var postponeTask by remember { mutableStateOf<TaskEntity?>(null) }
    var remainingSeconds by remember { mutableStateOf(0) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshEstimationBias()
        viewModel.message.collectLatest { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // §8.10.3: タイムボックスカウントダウン。0になったら強制対峙モーダルを表示する
    LaunchedEffect(runningTask?.id, runningLog?.startedAt) {
        val task = runningTask
        val log = runningLog
        if (task == null || log == null) return@LaunchedEffect
        while (true) {
            val elapsedSec = (System.currentTimeMillis() - log.startedAt) / 1000
            val totalSec = task.estimatedMinutes * 60L
            val remaining = totalSec - elapsedSec
            if (remaining <= 0) {
                remainingSeconds = 0
                viewModel.notifyTimeboxExpired(task.id)
                break
            }
            remainingSeconds = remaining.toInt()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("タスク") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "タスク追加")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 実行中タスク（タイムボックス）──
            runningTask?.let { task ->
                item(key = "running-${task.id}") {
                    RunningTaskCard(
                        task = task,
                        remainingSeconds = remainingSeconds,
                        onComplete = { viewModel.completeTask(task.id) },
                        onAbandon = { viewModel.abandonTask(task.id) },
                        onPostpone = { postponeTask = task }
                    )
                }
            }

            // ── 見積もり精度レポート（§8.10.1 / §11.11）──
            if (estimationBias.sampleSize > 0) {
                item(key = "bias") {
                    EstimationBiasCard(estimationBias)
                }
            }

            val now = System.currentTimeMillis()
            val overdue = tasks.filter { it.status == TaskEngine.STATUS_PENDING && it.deadlineAt < now }
            val dueSoon = tasks.filter {
                it.status == TaskEngine.STATUS_PENDING &&
                    it.deadlineAt in now..(now + 24 * 60 * 60 * 1000L)
            }
            val restPending = tasks.filter {
                it.status == TaskEngine.STATUS_PENDING && it.deadlineAt >= now + 24 * 60 * 60 * 1000L
            }
            val done = tasks.filter { it.status == TaskEngine.STATUS_DONE }
            val abandoned = tasks.filter {
                it.status == TaskEngine.STATUS_ABANDONED || it.status == TaskEngine.STATUS_FAILED
            }

            if (overdue.isNotEmpty()) {
                item(key = "h-overdue") {
                    SectionHeader("🔴 期限超過", overdue.size)
                }
                items(overdue, key = { it.id }) { TaskRow(it, onStart = { viewModel.startTask(it.id) }, onPostpone = { postponeTask = it }) }
            }
            if (dueSoon.isNotEmpty()) {
                item(key = "h-duesoon") {
                    SectionHeader("🟠 期限間近（24時間以内）", dueSoon.size)
                }
                items(dueSoon, key = { it.id }) { TaskRow(it, onStart = { viewModel.startTask(it.id) }, onPostpone = { postponeTask = it }) }
            }
            if (restPending.isNotEmpty()) {
                item(key = "h-pending") {
                    SectionHeader("📋 未着手", restPending.size)
                }
                items(restPending, key = { it.id }) { TaskRow(it, onStart = { viewModel.startTask(it.id) }, onPostpone = { postponeTask = it }) }
            }
            if (done.isNotEmpty()) {
                item(key = "h-done") {
                    SectionHeader("✅ 完了", done.size)
                }
                items(done.take(20), key = { it.id }) { TaskRow(it, onStart = null, onPostpone = null) }
            }
            if (abandoned.isNotEmpty()) {
                item(key = "h-abandoned") {
                    SectionHeader("🗑️ 破棄・失敗", abandoned.size)
                }
                items(abandoned.take(10), key = { it.id }) { TaskRow(it, onStart = null, onPostpone = null) }
            }
            if (tasks.isEmpty()) {
                item {
                    Text(
                        "タスクはまだありません。＋ボタンから作成しましょう。\n" +
                            "見積もり時間と締切を入力すると、パーキンソンの法則に対抗するタイムボックスが始まります。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTaskDialog(
            topics = viewModel.topics.collectAsState().value,
            entries = viewModel.entries.collectAsState().value,
            onDismiss = { showCreateDialog = false },
            onSave = { title, desc, minutes, deadline, entryId, topicId ->
                viewModel.createTask(title, desc, minutes, deadline, entryId, topicId)
                showCreateDialog = false
            }
        )
    }

    postponeTask?.let { task ->
        PostponeDialog(
            task = task,
            onDismiss = { postponeTask = null },
            onConfirm = { newDeadline ->
                viewModel.postponeTask(task.id, newDeadline)
                postponeTask = null
            }
        )
    }

    // §8.10.2 強制選択モーダル（閉じる・バックで回避不可）
    forcedChoiceTask?.let { task ->
        ForcedChoiceModal(
            task = task,
            reason = "先延ばしが上限（${TaskEngine.MAX_SILENT_POSTPONES}回）に達しました",
            onFinishToday = { viewModel.forceFinishToday(task.id) },
            onAbandon = { viewModel.abandonTask(task.id) }
        )
    }

    // §8.10.3 タイムボックス終了時の強制対峙（閉じる・バックで回避不可）
    timeboxExpiredTask?.let { task ->
        ForcedChoiceModal(
            task = task,
            reason = "見積もり時間（${task.estimatedMinutes}分）を超えました",
            onFinishToday = { viewModel.forceFinishToday(task.id) },
            onAbandon = { viewModel.abandonTask(task.id) }
        )
    }
}

@Composable
private fun SectionHeader(text: String, count: Int) {
    Text(
        "$text ($count)",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun RunningTaskCard(
    task: TaskEntity,
    remainingSeconds: Int,
    onComplete: () -> Unit,
    onAbandon: () -> Unit,
    onPostpone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("▶️ 実行中: ${task.title}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "見積もり ${task.estimatedMinutes}分 / 残り ${formatDuration(remainingSeconds)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    val total = task.estimatedMinutes * 60
                    if (total <= 0) 0f else (remainingSeconds.toFloat() / total).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onComplete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("完了")
                }
                OutlinedButton(onClick = onPostpone, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("延期")
                }
                TextButton(onClick = onAbandon) { Text("破棄") }
            }
        }
    }
}

@Composable
private fun EstimationBiasCard(bias: com.thuvstu.personalencyclopedia.brain.task.EstimationBias) {
    val ratio = bias.averageRatio ?: return
    val label = when {
        ratio <= 0.8 -> "あなたは見積もりを平均 ${"%.1f".format(ratio)}倍 過大評価しています（余裕を見すぎ）"
        ratio <= 1.2 -> "見積もり精度は良好です（平均 ${"%.1f".format(ratio)}倍）"
        else -> "あなたは見積もりを平均 ${"%.1f".format(ratio)}倍 過小評価しています（パーキンソンの法則の危険信号）"
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📏 見積もり精度レポート（直近${bias.sampleSize}件の実績）",
                style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    onStart: (() -> Unit)?,
    onPostpone: (() -> Unit)?
) {
    val now = System.currentTimeMillis()
    val sdf = remember { SimpleDateFormat("M/d HH:mm", Locale.getDefault()) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusChip(task.status)
            }
            task.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.height(4.dp))
            val deadlineText = "⏰ ${sdf.format(Date(task.deadlineAt))}"
            val color = when {
                task.status == TaskEngine.STATUS_DONE -> MaterialTheme.colorScheme.onSurfaceVariant
                task.deadlineAt < now -> MaterialTheme.colorScheme.error
                task.deadlineAt < now + 24 * 60 * 60 * 1000L -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                deadlineText + if (task.status == TaskEngine.STATUS_DONE && task.completedAt != null)
                    " | 完了 ${sdf.format(Date(task.completedAt!!))}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                "⏱️ 見積もり ${task.estimatedMinutes}分" +
                    if (task.postponeCount > 0) " | 延期×${task.postponeCount}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onStart != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("開始")
                    }
                    if (onPostpone != null) {
                        OutlinedButton(onClick = onPostpone, modifier = Modifier.weight(1f)) {
                            Text("延期")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        TaskEngine.STATUS_IN_PROGRESS -> "実行中" to MaterialTheme.colorScheme.primary
        TaskEngine.STATUS_DONE -> "完了" to MaterialTheme.colorScheme.primary
        TaskEngine.STATUS_ABANDONED -> "破棄" to MaterialTheme.colorScheme.error
        TaskEngine.STATUS_FAILED -> "失敗" to MaterialTheme.colorScheme.error
        else -> "未着手" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun ForcedChoiceModal(
    task: TaskEntity,
    reason: String,
    onFinishToday: () -> Unit,
    onAbandon: () -> Unit
) {
    // onDismissRequest = {} により、外側タップ・バックボタンでの回避を許さない（§8.10.2）
    AlertDialog(
        onDismissRequest = {},
        title = { Text("⛔ 強制選択") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(reason, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "「${task.title}」はこれ以上先延ばしできません。どちらかを選択してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onFinishToday, modifier = Modifier.fillMaxWidth()) {
                Text("📌 今日中に終わらせる（締切を今日の23:59に確定）")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onAbandon, modifier = Modifier.fillMaxWidth()) {
                Text("🗑️ タスクを破棄する（status=abandoned）")
            }
        }
    )
}

@Composable
private fun PostponeDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var newDeadline by remember { mutableStateOf(System.currentTimeMillis() + 24 * 60 * 60 * 1000L) }
    val context = LocalContext.current

    fun pickDateTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = newDeadline }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val t = Calendar.getInstance().apply {
                    set(y, m, d, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                }
                TimePickerDialog(
                    context,
                    { _, h, min ->
                        t.set(Calendar.HOUR_OF_DAY, h)
                        t.set(Calendar.MINUTE, min)
                        newDeadline = t.timeInMillis
                    },
                    t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE), true
                ).show()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("期限を延期（先延ばし ${task.postponeCount + 1}回目）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "※ ${TaskEngine.MAX_SILENT_POSTPONES}回を超えると強制選択になります",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = ::pickDateTime, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(newDeadline)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newDeadline) }) { Text("延期する") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun CreateTaskDialog(
    topics: List<com.thuvstu.personalencyclopedia.db.entity.TopicEntity>,
    entries: List<com.thuvstu.personalencyclopedia.db.entity.EntryEntity>,
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String?, minutes: Int, deadline: Long, entryId: String?, topicId: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf(System.currentTimeMillis() + 24 * 60 * 60 * 1000L) }
    var linkedEntryId by remember { mutableStateOf<String?>(null) }
    var linkedTopicId by remember { mutableStateOf<String?>(null) }
    var showEntryPicker by remember { mutableStateOf(false) }
    var showTopicPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val minutes = minutesText.toIntOrNull() ?: 0

    fun pickDateTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = deadline }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val t = Calendar.getInstance().apply {
                    set(y, m, d, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                }
                TimePickerDialog(
                    context,
                    { _, h, min ->
                        t.set(Calendar.HOUR_OF_DAY, h)
                        t.set(Calendar.MINUTE, min)
                        deadline = t.timeInMillis
                    },
                    t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE), true
                ).show()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タスクを作成") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タスク名（必須）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("説明（任意）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { c -> c.isDigit() } },
                    label = { Text("見積もり時間（分・必須）※タイムボックスの元になる") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = ::pickDateTime, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("締切: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(deadline))}")
                }
                OutlinedButton(
                    onClick = { showEntryPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        linkedEntryId?.let { id -> entries.firstOrNull { it.id == id }?.let { "${entryTypeIcon(it.type)} ${it.title}" } ?: "関連エントリー: 未選択" }
                            ?: "関連エントリー: 未選択（任意）",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = { showTopicPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        linkedTopicId?.let { id -> topics.firstOrNull { it.id == id }?.name ?: "科目: 未選択" }
                            ?: "科目: 未選択（任意・StudyPlusコメント欄に使用）",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title.trim(), description,
                        minutes, deadline, linkedEntryId, linkedTopicId
                    )
                },
                // §8.10.1: estimatedMinutes の入力を必須とし、未入力では保存不可（§11.11）
                enabled = title.isNotBlank() && minutes > 0
            ) { Text("作成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )

    if (showEntryPicker) {
        EntryPickerDialog(
            entries = entries,
            onDismiss = { showEntryPicker = false },
            onSelect = {
                linkedEntryId = it
                showEntryPicker = false
            }
        )
    }
    if (showTopicPicker) {
        TopicPickerDialog(
            topics = topics,
            onDismiss = { showTopicPicker = false },
            onSelect = {
                linkedTopicId = it
                showTopicPicker = false
            }
        )
    }
}

@Composable
private fun EntryPickerDialog(
    entries: List<com.thuvstu.personalencyclopedia.db.entity.EntryEntity>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("関連エントリーを選択") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(entries) { e ->
                    TextButton(onClick = { onSelect(e.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${entryTypeIcon(e.type)} ${e.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

@Composable
private fun TopicPickerDialog(
    topics: List<com.thuvstu.personalencyclopedia.db.entity.TopicEntity>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("科目を選択") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(topics) { t ->
                    TextButton(onClick = { onSelect(t.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(t.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

private fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

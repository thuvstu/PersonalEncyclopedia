package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.viewmodel.QuizEditViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuizEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: QuizEditViewModel = hiltViewModel()
) {
    val quizType by viewModel.quizType.collectAsState()
    val question by viewModel.question.collectAsState()
    val answer by viewModel.answer.collectAsState()
    val choices by viewModel.choices.collectAsState()
    val hints by viewModel.hints.collectAsState()
    val explanation by viewModel.explanation.collectAsState()
    val difficulty by viewModel.difficulty.collectAsState()
    val canSave by viewModel.canSave.collectAsState()

    LaunchedEffect(Unit) { viewModel.saved.collectLatest { onSaved(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "クイズを作成" else "クイズを編集") },
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
            // 出題形式
            Text("出題形式", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("qa" to "記述", "mcq" to "4択", "fill_blank" to "穴埋め").forEach { (v, label) ->
                    FilterChip(
                        selected = quizType == v,
                        onClick = { viewModel.setQuizType(v) },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = question, onValueChange = viewModel::setQuestion,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("問題文 *") }, minLines = 2
            )

            OutlinedTextField(
                value = answer, onValueChange = viewModel::setAnswer,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("正解 *") }
            )

            // MCQ選択肢
            if (quizType == "mcq") {
                Text("選択肢（正解を含む・2〜6個）", style = MaterialTheme.typography.labelLarge)
                choices.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = c,
                            onValueChange = { viewModel.updateChoice(i, it) },
                            modifier = Modifier.weight(1f),
                            label = { Text("選択肢 ${i + 1}") },
                            singleLine = true
                        )
                        if (choices.size > 2) {
                            IconButton(onClick = { viewModel.removeChoice(i) }) {
                                Icon(Icons.Default.Close, contentDescription = "削除")
                            }
                        }
                    }
                }
                if (choices.size < 6) {
                    TextButton(onClick = { viewModel.addChoice() }) {
                        Icon(Icons.Default.Add, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("選択肢を追加")
                    }
                }
            }

            // ヒント（§8.5 段階開示・最大3件・開示ごとに-0.3）
            Text("ヒント（任意・最大3件・開示ごとに得点-0.3）",
                style = MaterialTheme.typography.labelLarge)
            hints.forEachIndexed { i, h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = h,
                        onValueChange = { viewModel.updateHint(i, it) },
                        modifier = Modifier.weight(1f),
                        label = { Text("ヒント ${i + 1}") },
                        singleLine = true
                    )
                    IconButton(onClick = { viewModel.removeHint(i) }) {
                        Icon(Icons.Default.Close, contentDescription = "削除")
                    }
                }
            }
            if (hints.size < 3) {
                TextButton(onClick = { viewModel.addHint() }) {
                    Icon(Icons.Default.Add, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("ヒントを追加")
                }
            }

            OutlinedTextField(
                value = explanation, onValueChange = viewModel::setExplanation,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("解説（任意）") }, minLines = 2
            )

            // 難易度
            Text("難易度: $difficulty", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = difficulty.toFloat(),
                onValueChange = { viewModel.setDifficulty(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3
            )
        }
    }
}
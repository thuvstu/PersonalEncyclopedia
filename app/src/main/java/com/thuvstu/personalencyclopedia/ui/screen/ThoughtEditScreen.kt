package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.viewmodel.ThoughtEditViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThoughtEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ThoughtEditViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val mood by viewModel.mood.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.saved.collectLatest { id -> onSaved(id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "新しいメモ" else "メモを編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = title.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("タイトル *") },
                singleLine = true
            )
            OutlinedTextField(
                value = content,
                onValueChange = viewModel::onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("内容") },
                minLines = 5
            )
            OutlinedTextField(
                value = mood,
                onValueChange = viewModel::onMoodChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("気分（任意）") },
                singleLine = true,
                supportingText = { Text("例: 😊 やる気あり、🤔 考え中") }
            )
        }
    }
}
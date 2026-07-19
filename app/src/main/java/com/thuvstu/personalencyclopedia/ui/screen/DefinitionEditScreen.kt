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
import com.thuvstu.personalencyclopedia.viewmodel.DefinitionEditViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefinitionEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: DefinitionEditViewModel = hiltViewModel()
) {
    val term by viewModel.term.collectAsState()
    val reading by viewModel.reading.collectAsState()
    val definition by viewModel.definition.collectAsState()
    val field by viewModel.field.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.saved.collectLatest { id -> onSaved(id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "単語帳に追加" else "単語を編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = term.isNotBlank() && definition.isNotBlank()
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
                value = term,
                onValueChange = viewModel::onTermChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("用語 *") },
                singleLine = true
            )
            OutlinedTextField(
                value = reading,
                onValueChange = viewModel::onReadingChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("読み（任意）") },
                singleLine = true
            )
            OutlinedTextField(
                value = definition,
                onValueChange = viewModel::onDefinitionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("定義 *") },
                minLines = 3
            )
            OutlinedTextField(
                value = field,
                onValueChange = viewModel::onFieldChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分野（任意）") },
                singleLine = true,
                supportingText = { Text("例: 数学, CS, 歴史") }
            )
        }
    }
}
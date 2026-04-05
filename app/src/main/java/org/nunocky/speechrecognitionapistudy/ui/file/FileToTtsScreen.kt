package org.nunocky.speechrecognitionapistudy.ui.file

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nunocky.speechrecognitionapistudy.ui.component.ChatBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileToTtsScreen(
    onBack: () -> Unit,
    viewModel: FileToTtsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 一度限りのイベント（エラー表示など）を収集
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FileToTtsUiEvent.ShowError ->
                    Toast.makeText(context, "エラー: ${event.message}", Toast.LENGTH_SHORT).show()

                FileToTtsUiEvent.UnsupportedVersion ->
                    Toast.makeText(context, "この機能はAndroid 12以上で利用できます", Toast.LENGTH_SHORT).show()

                FileToTtsUiEvent.NoFileSelected ->
                    Toast.makeText(context, "ファイルを選択してください", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.onFileSelected(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ファイルからTTS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (uiState.selectedFileName.isNotBlank()) uiState.selectedFileName
                else "ファイルが選択されていません",
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { fileLauncher.launch("audio/*") },
                enabled = !uiState.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ファイルを選択")
            }

            Button(
                onClick = { viewModel.startRecognition() },
                enabled = !uiState.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("認識開始")
            }

            if (uiState.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("progress_indicator")
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.messages.isEmpty() && uiState.partialText.isBlank()) {
                    item { Text("認識結果がここに表示されます") }
                }
                items(uiState.messages) { message ->
                    ChatBubble(message.text)
                }
                if (uiState.partialText.isNotBlank()) {
                    item { ChatBubble(uiState.partialText) }
                }
            }
        }
    }
}

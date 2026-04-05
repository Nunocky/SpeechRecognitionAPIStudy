package org.nunocky.speechrecognitionapistudy.ui.mic

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nunocky.speechrecognitionapistudy.ui.component.ChatBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechRecognitionScreen(
    onBack: (() -> Unit)? = null,
    viewModel: SpeechRecognitionViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 一度限りのイベント（エラー表示など）を収集
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SpeechRecognitionUiEvent.ShowError -> {
                    Toast.makeText(context, "エラー: ${event.message}", Toast.LENGTH_SHORT).show()
                }
                is SpeechRecognitionUiEvent.UnsupportedVersion -> {
                    Toast.makeText(context, "この機能はAndroid 12以上で利用できます", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            Toast.makeText(context, "マイクの権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    fun onMicButtonClick() {
        if (uiState.isListening) {
            viewModel.stopListening()
        } else {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                viewModel.startListening()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("マイクからTTS") },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                FloatingActionButton(
                    onClick = { onMicButtonClick() },
                    shape = CircleShape,
                    containerColor = if (uiState.isListening) Color.Red else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = if (uiState.isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Microphone"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages) { message ->
                    ChatBubble(message.text)
                }
                if (uiState.partialText.isNotBlank()) {
                    item {
                        ChatBubble(uiState.partialText)
                    }
                }
            }
        }
    }
}
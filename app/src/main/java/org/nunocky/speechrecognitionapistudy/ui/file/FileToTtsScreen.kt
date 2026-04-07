package org.nunocky.speechrecognitionapistudy.ui.file

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nunocky.speechrecognitionapistudy.R
import org.nunocky.speechrecognitionapistudy.locale.SupportedSpeechLocales
import org.nunocky.speechrecognitionapistudy.ui.component.ChatBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileToTtsScreen(
    localeTag: String = SupportedSpeechLocales.DefaultLocaleTag,
    onBack: () -> Unit,
    viewModel: FileToTtsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(localeTag) {
        viewModel.setLocaleTag(localeTag)
    }

    // 一度限りのイベント（エラー表示など）を収集
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FileToTtsUiEvent.ShowError -> {
                    val cause = event.message.ifBlank { context.getString(R.string.error_unknown) }
                    Toast.makeText(context, context.getString(R.string.toast_error_prefix, cause), Toast.LENGTH_SHORT).show()
                }

                FileToTtsUiEvent.UnsupportedVersion ->
                    Toast.makeText(context, context.getString(R.string.toast_unsupported_version), Toast.LENGTH_SHORT).show()

                FileToTtsUiEvent.NoFileSelected ->
                    Toast.makeText(context, context.getString(R.string.toast_no_file_selected), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.onFileSelected(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecognition()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    fun onStartRecognition() {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    FileToTtsScreenContent(
        uiState = uiState,
        onBack = onBack,
        onSelectFile = { fileLauncher.launch("audio/*") },
        onStartRecognition = ::onStartRecognition,
        onTogglePlayback = { viewModel.togglePlayback() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileToTtsScreenContent(
    uiState: FileToTtsUiState,
    onBack: () -> Unit,
    onSelectFile: () -> Unit,
    onStartRecognition: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    val visibleMessages = if (uiState.isRecognizing) emptyList() else uiState.messages
    val visiblePartialText = if (uiState.isRecognizing) uiState.partialText else ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title_file_to_tts)) },
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
                else stringResource(R.string.label_no_file_selected),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onSelectFile,
                enabled = !uiState.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.button_select_file))
            }

            Button(
                onClick = onStartRecognition,
                enabled = !uiState.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.button_start_recognition))
            }

            Button(
                onClick = onTogglePlayback,
                enabled = !uiState.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (uiState.isPlaying) stringResource(R.string.button_stop_playback)
                    else stringResource(R.string.button_play_file)
                )
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
                if (visibleMessages.isEmpty() && visiblePartialText.isBlank()) {
                    item { Text(stringResource(R.string.label_recognition_placeholder)) }
                }
                items(visibleMessages) { message ->
                    ChatBubble(message.text)
                }
                if (visiblePartialText.isNotBlank()) {
                    item { ChatBubble(visiblePartialText) }
                }
            }
        }
    }
}

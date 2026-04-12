package org.nunocky.speechrecognitionapistudy.ui.file

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var pendingBatchUri by remember { mutableStateOf<Uri?>(null) }

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

                is FileToTtsUiEvent.BatchCompleted ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_batch_completed, event.jsonPath, event.csvPath),
                        Toast.LENGTH_LONG
                    ).show()

                FileToTtsUiEvent.NoBatchAudioFiles ->
                    Toast.makeText(context, context.getString(R.string.toast_batch_no_audio_files), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (pendingBatchUri != null) {
                viewModel.startBatchRecognition(pendingBatchUri!!)
                pendingBatchUri = null
            } else {
                viewModel.startRecognition()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show()
            pendingBatchUri = null
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.onFileSelected(uri)
    }

    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingBatchUri = uri
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                viewModel.startBatchRecognition(uri)
            }
        }
    }

    fun onStartRecognitionClick() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.startRecognition()
        }
    }

    FileToTtsScreenContent(
        uiState = uiState,
        onBack = onBack,
        onSelectFile = { fileLauncher.launch("audio/*") },
        onStartRecognition = ::onStartRecognitionClick,
        onTogglePlayback = { viewModel.togglePlayback() },
        onSeek = { viewModel.seekTo(it) },
        onSelectDirectory = { dirLauncher.launch(null) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileToTtsScreenContent(
    uiState: FileToTtsUiState,
    onBack: () -> Unit,
    onSelectFile: () -> Unit,
    onStartRecognition: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Int) -> Unit,
    onSelectDirectory: () -> Unit
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

            // ファイル選択と認識開始ボタンを横並び
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSelectFile,
                    enabled = !uiState.isProcessing && !uiState.isBatchProcessing,
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(stringResource(R.string.button_select_file))
                }

                Button(
                    onClick = onStartRecognition,
                    enabled = !uiState.isProcessing && !uiState.isBatchProcessing,
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(stringResource(R.string.button_start_recognition))
                }
            }

            // 再生と一括処理ボタンを別々に配置
            Button(
                onClick = onTogglePlayback,
                enabled = !uiState.isProcessing && !uiState.isBatchProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (uiState.isPlaying) stringResource(R.string.button_stop_playback)
                    else stringResource(R.string.button_play_file)
                )
            }

            if (uiState.playbackDurationMs > 0) {
                val sliderValue = if (uiState.playbackDurationMs > 0) {
                    uiState.playbackPositionMs.toFloat() / uiState.playbackDurationMs.toFloat()
                } else 0f
                Slider(
                    value = sliderValue,
                    onValueChange = { fraction ->
                        onSeek((fraction * uiState.playbackDurationMs).toInt())
                    },
                    enabled = !uiState.isProcessing && !uiState.isBatchProcessing,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = onSelectDirectory,
                enabled = !uiState.isProcessing && !uiState.isBatchProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.button_batch_directory))
            }

            if (uiState.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("progress_indicator")
                )
            }

            if (uiState.isBatchProcessing) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text(stringResource(R.string.dialog_title_batch_processing)) },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                stringResource(
                                    R.string.label_batch_progress,
                                    uiState.batchProcessedCount,
                                    uiState.batchTotalFiles
                                )
                            )
                            if (uiState.batchCurrentFileName.isNotBlank()) {
                                Text(uiState.batchCurrentFileName)
                            }
                            if (uiState.batchCurrentText.isNotBlank()) {
                                Text(uiState.batchCurrentText)
                            }
                        }
                    }
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

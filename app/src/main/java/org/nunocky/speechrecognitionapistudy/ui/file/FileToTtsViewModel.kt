package org.nunocky.speechrecognitionapistudy.ui.file

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.nunocky.speechrecognitionapistudy.locale.SupportedSpeechLocales
import org.nunocky.speechrecognitionapistudy.ui.component.UIChatMessage
import java.io.File
import java.util.Locale

private const val TAG = "FileToTtsViewModel"

// ── UI State / Events ─────────────────────────────────────────────────────────

data class FileToTtsUiState(
    val selectedFileName: String = "",
    val isProcessing: Boolean = false,
    val isRecognizing: Boolean = false,
    val isPlaying: Boolean = false,
    val partialText: String = "",
    val messages: List<UIChatMessage> = emptyList()
)

sealed class FileToTtsUiEvent {
    data class ShowError(val message: String) : FileToTtsUiEvent()
    object UnsupportedVersion : FileToTtsUiEvent()
    object NoFileSelected : FileToTtsUiEvent()
}

internal fun canStartRecognition(
    currentState: FileToTtsUiState,
    hasRecognitionJob: Boolean
): Boolean = !hasRecognitionJob && !currentState.isRecognizing

internal fun markRecognitionStarted(currentState: FileToTtsUiState): FileToTtsUiState =
    currentState.copy(
        isProcessing = true,
        isRecognizing = true,
        partialText = "",
        messages = emptyList()
    )

internal fun applyFinalTextResponseAggregation(
    currentState: FileToTtsUiState,
    finalText: String,
    resultBuffer: ResultBuffer
): FileToTtsUiState {
    resultBuffer.add(finalText)
    return currentState.copy(partialText = "")
}

internal fun applyCompletedResponseAggregation(
    currentState: FileToTtsUiState,
    resultBuffer: ResultBuffer
): FileToTtsUiState {
    val aggregatedText = resultBuffer.getAggregated()
    val updatedMessages = if (aggregatedText.isNotBlank()) {
        currentState.messages + UIChatMessage(aggregatedText)
    } else {
        currentState.messages
    }
    resultBuffer.clear()
    return currentState.copy(
        isProcessing = false,
        isRecognizing = false,
        partialText = "",
        messages = updatedMessages
    )
}

internal fun applyErrorOrCancelReset(
    currentState: FileToTtsUiState,
    resultBuffer: ResultBuffer
): FileToTtsUiState {
    resultBuffer.clear()
    return currentState.copy(
        isProcessing = false,
        isRecognizing = false,
        partialText = ""
    )
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class FileToTtsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FileToTtsUiState())
    val uiState: StateFlow<FileToTtsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FileToTtsUiEvent>()
    val events = _events.asSharedFlow()

    private var selectedFileUri: Uri? = null
    private var mediaPlayer: MediaPlayer? = null
    private val resultBuffer = ResultBuffer()

    private var currentLocaleTag: String = SupportedSpeechLocales.DefaultLocaleTag

    private var recognitionJob: Job? = null
    private var runCounter: Long = 0

    fun setLocaleTag(localeTag: String) {
        val sanitizedTag = SupportedSpeechLocales.sanitize(localeTag)
        if (sanitizedTag == currentLocaleTag || recognitionJob != null) return

        currentLocaleTag = sanitizedTag
    }

    private fun createSpeechRecognizer(localeTag: String) = run {
        val options = SpeechRecognizerOptions.Builder().apply {
            locale = Locale.forLanguageTag(localeTag)
        }.build()
        SpeechRecognition.getClient(options)
    }

    private suspend fun ensureRecognizerReady(
        recognizer: SpeechRecognizer,
        runId: Long
    ): Boolean {
        val status = recognizer.checkStatus()
        Log.d(TAG, "startRecognition[$runId]: checkStatus=$status")

        if (status == FeatureStatus.AVAILABLE) return true

        if (status == FeatureStatus.DOWNLOADABLE) {
            Log.d(TAG, "startRecognition[$runId]: feature downloadable, start download")
            var totalToDownload: Long = 0
            recognizer.download().collect { downloadStatus ->
                when (downloadStatus) {
                    is DownloadStatus.DownloadStarted -> {
                        totalToDownload = downloadStatus.bytesToDownload
                        Log.d(
                            TAG,
                            "startRecognition[$runId]: download started bytes=$totalToDownload"
                        )
                    }

                    is DownloadStatus.DownloadProgress -> {
                        Log.d(
                            TAG,
                            "startRecognition[$runId]: downloading ${downloadStatus.totalBytesDownloaded}/$totalToDownload"
                        )
                    }

                    is DownloadStatus.DownloadCompleted -> {
                        Log.d(TAG, "startRecognition[$runId]: download completed")
                    }

                    is DownloadStatus.DownloadFailed -> {
                        throw IllegalStateException(
                            "Model download failed: ${downloadStatus.e.message}"
                        )
                    }
                }
            }

            val recheck = recognizer.checkStatus()
            Log.d(TAG, "startRecognition[$runId]: checkStatus(after download)=$recheck")
            return recheck == FeatureStatus.AVAILABLE
        }

        return false
    }


    fun onFileSelected(uri: Uri) {
        stopPlaybackInternal()
        selectedFileUri = uri

        // Task 3.1: Clear result buffer from previous file selection
        resultBuffer.clear()

        val app = getApplication<Application>()
        val fileName = try {
            val cursor = app.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) c.getString(nameIdx) else null
                } else null
            } ?: uri.lastPathSegment ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve display name: ${e.message}")
            uri.lastPathSegment ?: ""
        }

        // Task 3.1: Update state with new file name and clear previous results
        // Ensures multiple file selections don't mix results (Requirements: 1, 3)
        _uiState.update { it.copy(
            selectedFileName = fileName,
            isPlaying = false,
            partialText = "",      // Clear partial text from previous recognition
            messages = emptyList()  // Clear messages to prevent result mixing
        ) }
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) {
            stopPlaybackInternal()
            return
        }

        val uri = selectedFileUri
        if (uri == null) {
            viewModelScope.launch { _events.emit(FileToTtsUiEvent.NoFileSelected) }
            return
        }

        startPlayback(uri)
    }

    private fun startPlayback(uri: Uri) {
        val app = getApplication<Application>()

        runCatching {
            stopPlaybackInternal()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(app, uri)
                setOnCompletionListener {
                    stopPlaybackInternal()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    stopPlaybackInternal()
                    viewModelScope.launch {
                        _events.emit(FileToTtsUiEvent.ShowError("Audio playback failed"))
                    }
                    true
                }
                prepare()
                start()
            }
            _uiState.update { it.copy(isPlaying = true) }
        }.onFailure { e ->
            Log.e(TAG, "Failed to start playback: ${e.message}", e)
            stopPlaybackInternal()
            viewModelScope.launch {
                _events.emit(FileToTtsUiEvent.ShowError(e.message ?: ""))
            }
        }
    }

    private fun stopPlaybackInternal() {
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun startRecognition() {
        if (!canStartRecognition(_uiState.value, recognitionJob != null)) return

        if (_uiState.value.isPlaying) {
            stopPlaybackInternal()
        }

        if (selectedFileUri == null) {
            viewModelScope.launch { _events.emit(FileToTtsUiEvent.NoFileSelected) }
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            viewModelScope.launch { _events.emit(FileToTtsUiEvent.UnsupportedVersion) }
            return
        }

        val uri = selectedFileUri!!
        val app = getApplication<Application>()

        val runId = ++runCounter

        _uiState.update { state -> markRecognitionStarted(state) }

        recognitionJob = viewModelScope.launch {
            var tempWavFile: File? = null
            var readFd: ParcelFileDescriptor? = null
            var writeFd: ParcelFileDescriptor? = null
            var streamJob: Job? = null
            val speechRecognizer = createSpeechRecognizer(currentLocaleTag)
            Log.d(TAG, "startRecognition[$runId]: recognizer created locale=$currentLocaleTag")

            try {
                val ready = ensureRecognizerReady(speechRecognizer, runId)
                if (!ready) {
                    _events.emit(
                        FileToTtsUiEvent.ShowError(
                            "Speech feature is not available yet. Please retry in a few moments."
                        )
                    )
                    _uiState.update { state -> applyErrorOrCancelReset(state, resultBuffer) }
                    return@launch
                }

                tempWavFile = withContext(Dispatchers.IO) {
                    createTempPcmFile(app, uri)
                }
                Log.d(TAG, "startRecognition[$runId]: temp pcm bytes=${tempWavFile.length()}")
                val pipe = ParcelFileDescriptor.createPipe()
                readFd = pipe[0]
                writeFd = pipe[1]

                streamJob = viewModelScope.launch(Dispatchers.IO) {
                    streamPcmFileToPipe(tempWavFile, writeFd)
                }

                val request = SpeechRecognizerRequest.Builder().apply {
                    audioSource = AudioSource.fromPfd(readFd)
                }.build()

                speechRecognizer.startRecognition(request).collect { response ->
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse -> {
                            _uiState.update { it.copy(partialText = response.text) }
                        }

                        is SpeechRecognizerResponse.FinalTextResponse -> {
                            _uiState.update { state ->
                                applyFinalTextResponseAggregation(
                                    currentState = state,
                                    finalText = response.text,
                                    resultBuffer = resultBuffer
                                )
                            }
                        }

                        is SpeechRecognizerResponse.ErrorResponse -> {
                            Log.e(TAG, "Recognition ErrorResponse[$runId]: ${response.e.message}", response.e)
                            _events.emit(FileToTtsUiEvent.ShowError(response.e.message ?: ""))
                            _uiState.update { state -> applyErrorOrCancelReset(state, resultBuffer) }
                        }

                        is SpeechRecognizerResponse.CompletedResponse -> {
                            _uiState.update { state ->
                                applyCompletedResponseAggregation(
                                    currentState = state,
                                    resultBuffer = resultBuffer
                                )
                            }
                            recognitionJob = null
                        }
                    }
                }
            } catch (_: CancellationException) {
                Log.d(TAG, "Recognition cancelled[$runId] (expected control flow)")
                _uiState.update { state -> applyErrorOrCancelReset(state, resultBuffer) }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception during recognition[$runId]: ${e.message}", e)
                _events.emit(FileToTtsUiEvent.ShowError(e.message ?: ""))
                _uiState.update { state -> applyErrorOrCancelReset(state, resultBuffer) }
            } finally {
                streamJob?.cancel()
                runCatching { readFd?.close() }
                runCatching { writeFd?.close() }
                runCatching { speechRecognizer.stopRecognition() }
                runCatching { speechRecognizer.close() }
                runCatching {
                    val deleted = tempWavFile?.delete() ?: true
                    Log.d(TAG, "startRecognition[$runId]: temp pcm deleted=$deleted")
                }
                Log.d(TAG, "startRecognition[$runId]: recognizer closed")
                _uiState.update { it.copy(isProcessing = false, isRecognizing = false) }
                recognitionJob = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recognitionJob?.cancel()
        stopPlaybackInternal()
    }
}

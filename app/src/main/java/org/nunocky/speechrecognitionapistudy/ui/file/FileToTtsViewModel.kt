package org.nunocky.speechrecognitionapistudy.ui.file

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.nunocky.speechrecognitionapistudy.locale.SupportedSpeechLocales
import org.nunocky.speechrecognitionapistudy.ui.component.UIChatMessage

private const val TAG = "FileToTtsViewModel"

data class FileToTtsUiState(
    val selectedFileName: String = "",
    val isProcessing: Boolean = false,
    val isRecognizing: Boolean = false,
    val isPlaying: Boolean = false,
    val partialText: String = "",
    val messages: List<UIChatMessage> = emptyList(),
    val isBatchProcessing: Boolean = false,
    val batchCurrentFileName: String = "",
    val batchCurrentText: String = "",
    val batchTotalFiles: Int = 0,
    val batchProcessedCount: Int = 0,
    val playbackPositionMs: Int = 0,
    val playbackDurationMs: Int = 0
)

sealed class FileToTtsUiEvent {
    data class ShowError(val message: String) : FileToTtsUiEvent()
    object UnsupportedVersion : FileToTtsUiEvent()
    object NoFileSelected : FileToTtsUiEvent()
    data class BatchCompleted(val jsonPath: String, val csvPath: String) : FileToTtsUiEvent()
    object NoBatchAudioFiles : FileToTtsUiEvent()
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
    private var batchJob: Job? = null
    private var playbackPollingJob: Job? = null
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
                        Log.d(TAG, "startRecognition[$runId]: download started bytes=$totalToDownload")
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
                        throw IllegalStateException("Model download failed: ${downloadStatus.e.message}")
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
                } else {
                    null
                }
            } ?: uri.lastPathSegment ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve display name: ${e.message}")
            uri.lastPathSegment ?: ""
        }

        _uiState.update {
            it.copy(
                selectedFileName = fileName,
                isPlaying = false,
                partialText = "",
                messages = emptyList()
            )
        }
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
            val duration = mediaPlayer?.duration ?: 0
            _uiState.update { it.copy(isPlaying = true, playbackDurationMs = duration, playbackPositionMs = 0) }
            playbackPollingJob = viewModelScope.launch {
                while (_uiState.value.isPlaying) {
                    val pos = mediaPlayer?.currentPosition ?: 0
                    _uiState.update { it.copy(playbackPositionMs = pos) }
                    delay(250)
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to start playback: ${e.message}", e)
            stopPlaybackInternal()
            viewModelScope.launch {
                _events.emit(FileToTtsUiEvent.ShowError(e.message ?: ""))
            }
        }
    }

    private fun stopPlaybackInternal() {
        playbackPollingJob?.cancel()
        playbackPollingJob = null
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _uiState.update { it.copy(isPlaying = false, playbackPositionMs = 0) }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _uiState.update { it.copy(playbackPositionMs = positionMs) }
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
                runCatching { tempWavFile?.delete() }
                _uiState.update { it.copy(isProcessing = false, isRecognizing = false) }
                recognitionJob = null
            }
        }
    }

    private suspend fun recognizeFileToText(
        uri: Uri,
        onPartial: (String) -> Unit = {},
        onFinalText: (String) -> Unit = {}
    ): String {
        val app = getApplication<Application>()
        var tempWavFile: File? = null
        var readFd: ParcelFileDescriptor? = null
        var writeFd: ParcelFileDescriptor? = null
        var streamJob: Job? = null
        val speechRecognizer = createSpeechRecognizer(currentLocaleTag)
        val resultBuilder = StringBuilder()

        try {
            val ready = ensureRecognizerReady(speechRecognizer, 0)
            if (!ready) {
                throw IllegalStateException("Speech feature is not available yet. Please retry in a few moments.")
            }

            tempWavFile = withContext(Dispatchers.IO) {
                createTempPcmFile(app, uri)
            }
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
                    is SpeechRecognizerResponse.PartialTextResponse -> onPartial(response.text)
                    is SpeechRecognizerResponse.FinalTextResponse -> {
                        resultBuilder.append(response.text)
                        onFinalText(response.text)
                        onPartial("")
                    }

                    is SpeechRecognizerResponse.ErrorResponse -> {
                        Log.e(TAG, "Recognition ErrorResponse: ${response.e.message}", response.e)
                        throw response.e
                    }

                    is SpeechRecognizerResponse.CompletedResponse -> Unit
                }
            }
        } finally {
            streamJob?.cancel()
            runCatching { readFd?.close() }
            runCatching { writeFd?.close() }
            runCatching { speechRecognizer.stopRecognition() }
            runCatching { speechRecognizer.close() }
            runCatching { tempWavFile?.delete() }
        }

        return resultBuilder.toString()
    }

    fun startBatchRecognition(directoryUri: Uri) {
        if (batchJob != null || recognitionJob != null) return

        if (_uiState.value.isPlaying) {
            stopPlaybackInternal()
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            viewModelScope.launch { _events.emit(FileToTtsUiEvent.UnsupportedVersion) }
            return
        }

        val app = getApplication<Application>()
        val dir = DocumentFile.fromTreeUri(app, directoryUri)
        if (dir == null || !dir.isDirectory) {
            viewModelScope.launch { _events.emit(FileToTtsUiEvent.NoBatchAudioFiles) }
            return
        }

        val audioExtensions = setOf("mp3", "m4a", "wav", "ogg", "flac", "aac")
        val audioFiles = dir.listFiles().filter { file ->
            val mimeOk = file.type?.startsWith("audio/") == true
            val extOk = file.name?.substringAfterLast('.', "")?.lowercase() in audioExtensions
            mimeOk || extOk
        }

        if (audioFiles.isEmpty()) {
            viewModelScope.launch { _events.emit(FileToTtsUiEvent.NoBatchAudioFiles) }
            return
        }

        _uiState.update {
            it.copy(
                isBatchProcessing = true,
                batchTotalFiles = audioFiles.size,
                batchProcessedCount = 0,
                batchCurrentFileName = "",
                batchCurrentText = ""
            )
        }

        batchJob = viewModelScope.launch {
            val results = mutableListOf<Pair<String, String>>()
            try {
                audioFiles.forEachIndexed { index, file ->
                    val fileName = file.name ?: "file_${index + 1}"
                    _uiState.update {
                        it.copy(
                            batchCurrentFileName = fileName,
                            batchCurrentText = "",
                            batchProcessedCount = index + 1
                        )
                    }

                    val text = try {
                        recognizeFileToText(
                            file.uri,
                            onPartial = { partial ->
                                _uiState.update { state -> state.copy(batchCurrentText = partial) }
                            }
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error recognizing $fileName: ${e.message}", e)
                        "[Failed: ${e.message}]"
                    }
                    results.add(fileName to text)
                }

                val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
                val outputDir = app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: app.filesDir
                val jsonPath = saveAsJson(outputDir, timestamp, results)
                val csvPath = saveAsCsv(outputDir, timestamp, results)
                _events.emit(FileToTtsUiEvent.BatchCompleted(jsonPath, csvPath))
            } catch (_: CancellationException) {
                Log.d(TAG, "Batch recognition cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Batch processing error: ${e.message}", e)
                _events.emit(FileToTtsUiEvent.ShowError(e.message ?: ""))
            } finally {
                _uiState.update {
                    it.copy(
                        isBatchProcessing = false,
                        batchCurrentFileName = "",
                        batchCurrentText = ""
                    )
                }
                batchJob = null
            }
        }
    }

    private fun saveAsJson(
        outputDir: File,
        timestamp: String,
        results: List<Pair<String, String>>
    ): String {
        val sortedResults = sortResultsByFileName(results)
        val array = JSONArray()
        sortedResults.forEach { (fileName, text) ->
            array.put(
                JSONObject().apply {
                    put("fileName", fileName)
                    put("text", text)
                }
            )
        }
        val file = File(outputDir, "transcription_$timestamp.json")
        file.writeText(array.toString(2), Charsets.UTF_8)
        return file.absolutePath
    }

    private fun saveAsCsv(
        outputDir: File,
        timestamp: String,
        results: List<Pair<String, String>>
    ): String {
        val sortedResults = sortResultsByFileName(results)
        val file = File(outputDir, "transcription_$timestamp.csv")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("fileName,text")
            writer.newLine()
            sortedResults.forEach { (fileName, text) ->
                writer.write("${csvEscape(fileName)},${csvEscape(text)}")
                writer.newLine()
            }
        }
        return file.absolutePath
    }

    private fun sortResultsByFileName(results: List<Pair<String, String>>): List<Pair<String, String>> {
        return results.sortedWith(
            compareBy<Pair<String, String>> { it.first.lowercase(Locale.ROOT) }
                .thenBy { it.first }
        )
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    override fun onCleared() {
        super.onCleared()
        recognitionJob?.cancel()
        batchJob?.cancel()
        stopPlaybackInternal()
    }
}

package org.nunocky.speechrecognitionapistudy.ui.file

import android.app.Application
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nunocky.speechrecognitionapistudy.ui.component.UIChatMessage
import java.io.FileOutputStream
import java.util.Locale

private const val TAG = "FileToTtsViewModel"

/** Target PCM format expected by ML Kit GenAI Speech Recognition */
private const val TARGET_SAMPLE_RATE = 16000

private fun ByteArray.getShortLE(offset: Int): Int =
    ((this[offset + 1].toInt() shl 8) or (this[offset].toInt() and 0xFF)).toShort().toInt()

private fun convertToTargetPcm(
    input: ByteArray,
    inputSampleRate: Int,
    channelCount: Int
): ByteArray {
    val inputFrames = input.size / (channelCount * 2)

    val mono = ShortArray(inputFrames) { frame ->
        if (channelCount == 1) {
            input.getShortLE(frame * 2).toShort()
        } else {
            var sum = 0
            for (ch in 0 until channelCount) {
                sum += input.getShortLE((frame * channelCount + ch) * 2)
            }
            (sum / channelCount).coerceIn(-32768, 32767).toShort()
        }
    }

    if (inputSampleRate == TARGET_SAMPLE_RATE) {
        val out = ByteArray(mono.size * 2)
        mono.forEachIndexed { i, s ->
            out[i * 2] = (s.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
        }
        return out
    }

    val ratio = inputSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
    val outputFrames = (inputFrames / ratio).toInt()
    val out = ByteArray(outputFrames * 2)
    for (i in 0 until outputFrames) {
        val srcPos = i * ratio
        val srcIdx = srcPos.toInt().coerceAtMost(mono.size - 1)
        val frac = srcPos - srcIdx
        val s0 = mono[srcIdx].toInt()
        val s1 = if (srcIdx + 1 < mono.size) mono[srcIdx + 1].toInt() else s0
        val s = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767)
        out[i * 2] = (s and 0xFF).toByte()
        out[i * 2 + 1] = (s shr 8 and 0xFF).toByte()
    }
    return out
}

private fun decodeAudioToPcm(
    app: Application,
    uri: Uri,
    writeFd: ParcelFileDescriptor
) {
    try {
        val extractor = MediaExtractor()
        extractor.setDataSource(app, uri, null)

        var audioTrackIdx = -1
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrackIdx = i
                break
            }
        }
        if (audioTrackIdx < 0) {
            Log.e(TAG, "decodeAudioToPcm: no audio track found in $uri")
            return
        }

        extractor.selectTrack(audioTrackIdx)
        val format = extractor.getTrackFormat(audioTrackIdx)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        var outSampleRate =
            runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(44100)
        var outChannelCount =
            runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(1)
        Log.d(
            TAG,
            "decodeAudioToPcm: MIME=$mime sampleRate=$outSampleRate channels=$outChannelCount → converting to $TARGET_SAMPLE_RATE Hz mono"
        )

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        FileOutputStream(writeFd.fileDescriptor).buffered(65536).use { out ->
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIdx = codec.dequeueInputBuffer(10_000L)
                    if (inputIdx >= 0) {
                        val inputBuf = codec.getInputBuffer(inputIdx)!!
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outputIdx >= 0 -> {
                        val outputBuf = codec.getOutputBuffer(outputIdx)!!
                        if (bufferInfo.size > 0) {
                            val rawPcm = ByteArray(bufferInfo.size)
                            outputBuf.get(rawPcm)
                            val converted = convertToTargetPcm(
                                rawPcm, outSampleRate, outChannelCount
                            )
                            out.write(converted)
                        }
                        codec.releaseOutputBuffer(outputIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }

                    outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        outSampleRate =
                            runCatching { newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) }
                                .getOrDefault(outSampleRate)
                        outChannelCount =
                            runCatching { newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                                .getOrDefault(outChannelCount)
                        Log.d(
                            TAG,
                            "decodeAudioToPcm: output format updated → sampleRate=$outSampleRate channels=$outChannelCount"
                        )
                    }
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()
        Log.d(TAG, "decodeAudioToPcm: decode + conversion complete")
    } catch (e: Exception) {
        if (e.message?.contains("EPIPE") == true || e.message?.contains("Broken pipe") == true) {
            Log.d(TAG, "decodeAudioToPcm: pipe closed by recognition side (expected on cancel)")
        } else {
            Log.e(TAG, "decodeAudioToPcm: error — ${e.message}", e)
        }
    } finally {
        runCatching { writeFd.close() }
    }
}

// ── UI State / Events ─────────────────────────────────────────────────────────

data class FileToTtsUiState(
    val selectedFileName: String = "",
    val isProcessing: Boolean = false,
    val partialText: String = "",
    val messages: List<UIChatMessage> = emptyList()
)

sealed class FileToTtsUiEvent {
    data class ShowError(val message: String) : FileToTtsUiEvent()
    object UnsupportedVersion : FileToTtsUiEvent()
    object NoFileSelected : FileToTtsUiEvent()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class FileToTtsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FileToTtsUiState())
    val uiState: StateFlow<FileToTtsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FileToTtsUiEvent>()
    val events = _events.asSharedFlow()

    private var selectedFileUri: Uri? = null

    private val speechRecognizer = run {
        val options = SpeechRecognizerOptions.Builder().apply {
            locale = Locale.JAPAN
        }.build()
        SpeechRecognition.getClient(options)
    }

    private var recognitionJob: Job? = null

    fun onFileSelected(uri: Uri) {
        selectedFileUri = uri
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
        _uiState.update { it.copy(selectedFileName = fileName) }
    }

    fun startRecognition() {
        if (recognitionJob != null) return

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

        val pipe = try {
            ParcelFileDescriptor.createPipe()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create pipe: ${e.message}", e)
            viewModelScope.launch {
                _events.emit(FileToTtsUiEvent.ShowError("ファイルを読み込めませんでした: ${e.message}"))
            }
            return
        }
        val readFd = pipe[0]
        val writeFd = pipe[1]

        _uiState.update { it.copy(isProcessing = true, partialText = "") }

        val request = SpeechRecognizerRequest.Builder().apply {
            audioSource = AudioSource.fromPfd(readFd)
        }.build()

        val decoderJob = viewModelScope.launch(Dispatchers.IO) {
            decodeAudioToPcm(app, uri, writeFd)
        }

        recognitionJob = viewModelScope.launch {
            try {
                speechRecognizer.startRecognition(request).collect { response ->
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse -> {
                            _uiState.update { it.copy(partialText = response.text) }
                        }

                        is SpeechRecognizerResponse.FinalTextResponse -> {
                            _uiState.update { state ->
                                state.copy(
                                    messages = state.messages + UIChatMessage(response.text),
                                    partialText = ""
                                )
                            }
                        }

                        is SpeechRecognizerResponse.ErrorResponse -> {
                            Log.e(TAG, "Recognition ErrorResponse: ${response.e.message}", response.e)
                            _events.emit(FileToTtsUiEvent.ShowError(response.e.message ?: "Unknown error"))
                        }

                        is SpeechRecognizerResponse.CompletedResponse -> {
                            _uiState.update { it.copy(isProcessing = false) }
                            recognitionJob = null
                        }
                    }
                }
            } catch (_: CancellationException) {
                Log.d(TAG, "Recognition cancelled (expected control flow)")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception during recognition: ${e.message}", e)
                _events.emit(FileToTtsUiEvent.ShowError(e.message ?: "Unknown error"))
            } finally {
                decoderJob.cancel()
                runCatching { readFd.close() }
                _uiState.update { it.copy(isProcessing = false) }
                recognitionJob = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recognitionJob?.cancel()
        speechRecognizer.close()
    }
}







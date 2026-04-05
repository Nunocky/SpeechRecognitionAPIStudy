package org.nunocky.speechrecognitionapistudy

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.util.Locale

private const val TAG = "FileToTtsScreen"

/** Target PCM format expected by ML Kit GenAI Speech Recognition */
private const val TARGET_SAMPLE_RATE = 16000
private const val TARGET_CHANNELS = 1  // mono

/**
 * Reads a signed 16-bit little-endian sample from [this] ByteArray at [offset].
 * Returns the value as a sign-extended Int (-32768..32767).
 */
private fun ByteArray.getShortLE(offset: Int): Int =
    ((this[offset + 1].toInt() shl 8) or (this[offset].toInt() and 0xFF)).toShort().toInt()

/**
 * Converts raw PCM chunks from MediaCodec to 16-bit mono PCM at [targetSampleRate].
 *
 * Steps:
 *  1. Multi-channel → mono (average all channels)
 *  2. Linear-interpolation resample from [inputSampleRate] to [targetSampleRate]
 *
 * Input / output encoding: signed 16-bit little-endian.
 */
private fun convertToTargetPcm(
    input: ByteArray,
    inputSampleRate: Int,
    channelCount: Int,
    targetSampleRate: Int
): ByteArray {
    val inputFrames = input.size / (channelCount * 2)  // 2 bytes per sample

    // ── Step 1: mix down to mono ──────────────────────────────────────────────
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

    // ── Step 2: resample ──────────────────────────────────────────────────────
    if (inputSampleRate == targetSampleRate) {
        val out = ByteArray(mono.size * 2)
        mono.forEachIndexed { i, s ->
            out[i * 2]     = (s.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
        }
        return out
    }

    val ratio = inputSampleRate.toDouble() / targetSampleRate.toDouble()
    val outputFrames = (inputFrames / ratio).toInt()
    val out = ByteArray(outputFrames * 2)
    for (i in 0 until outputFrames) {
        val srcPos = i * ratio
        val srcIdx = srcPos.toInt().coerceAtMost(mono.size - 1)
        val frac   = srcPos - srcIdx
        val s0 = mono[srcIdx].toInt()
        val s1 = if (srcIdx + 1 < mono.size) mono[srcIdx + 1].toInt() else s0
        val s  = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767)
        out[i * 2]     = (s and 0xFF).toByte()
        out[i * 2 + 1] = (s shr 8 and 0xFF).toByte()
    }
    return out
}

/**
 * Decodes an audio file (MP3, AAC, OGG, WAV, etc.) to raw PCM using MediaExtractor + MediaCodec,
 * converts the result to [TARGET_SAMPLE_RATE] Hz mono 16-bit PCM, and writes it to [writeFd].
 *
 * AudioSource.fromPfd() expects raw signed 16-bit PCM at 16 kHz mono.
 * [writeFd] is always closed in the finally block, signalling EOF to the read end.
 */
private fun decodeAudioToPcm(context: Context, uri: Uri, writeFd: ParcelFileDescriptor) {
    try {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        // Find the first audio track
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

        // Read initial sample rate / channel count (API-28-safe: use try-catch instead of 2-arg getInteger)
        var outSampleRate   = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(44100)
        var outChannelCount = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(1)
        Log.d(TAG, "decodeAudioToPcm: MIME=$mime sampleRate=$outSampleRate channels=$outChannelCount → converting to ${TARGET_SAMPLE_RATE} Hz mono")

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        FileOutputStream(writeFd.fileDescriptor).buffered(65536).use { out ->
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                // Feed compressed data to decoder
                if (!inputDone) {
                    val inputIdx = codec.dequeueInputBuffer(10_000L)
                    if (inputIdx >= 0) {
                        val inputBuf = codec.getInputBuffer(inputIdx)!!
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Drain decoded PCM from decoder
                val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outputIdx >= 0 -> {
                        val outputBuf = codec.getOutputBuffer(outputIdx)!!
                        if (bufferInfo.size > 0) {
                            val rawPcm = ByteArray(bufferInfo.size)
                            outputBuf.get(rawPcm)
                            // Convert to TARGET_SAMPLE_RATE Hz mono before writing to pipe
                            val converted = convertToTargetPcm(rawPcm, outSampleRate, outChannelCount, TARGET_SAMPLE_RATE)
                            out.write(converted)
                        }
                        codec.releaseOutputBuffer(outputIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                    outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        outSampleRate   = runCatching { newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(outSampleRate)
                        outChannelCount = runCatching { newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(outChannelCount)
                        Log.d(TAG, "decodeAudioToPcm: output format updated → sampleRate=$outSampleRate channels=$outChannelCount")
                    }
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()
        Log.d(TAG, "decodeAudioToPcm: decode + conversion complete")
    } catch (e: Exception) {
        // Broken pipe (EPIPE) is expected when the recognition job is cancelled before decoding finishes
        if (e.message?.contains("EPIPE") == true || e.message?.contains("Broken pipe") == true) {
            Log.d(TAG, "decodeAudioToPcm: pipe closed by recognition side (expected on cancel)")
        } else {
            Log.e(TAG, "decodeAudioToPcm: error — ${e.message}", e)
        }
    } finally {
        runCatching { writeFd.close() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileToTtsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- State ---
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<UIChatMessage>() }
    val recognitionJobState = remember { mutableStateOf<Job?>(null) }
    var recognitionJob by recognitionJobState

    // --- SpeechRecognizer lifecycle (Requirement 5.1) ---
    val speechRecognizer = remember {
        val options = SpeechRecognizerOptions.Builder().apply {
            locale = Locale.JAPAN
        }.build()
        SpeechRecognition.getClient(options)
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            // Requirement 5.2: cancel active job and close client on navigation away
            recognitionJobState.value?.cancel()
            speechRecognizer.close()
        }
    }

    // --- File picker launcher (Requirements 1.1, 1.3) ---
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult  // Requirement 1.3: retain previous state

        selectedFileUri = uri

        // Resolve display name (Requirements 1.2, 1.4)
        try {
            val cursor = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )
            selectedFileName = cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) c.getString(nameIdx) else null
                } else null
            } ?: uri.lastPathSegment ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve display name for URI, falling back to path segment: ${e.message}")
            selectedFileName = uri.lastPathSegment ?: ""
        }
    }

    // --- Recognition start with guards and error handling ---
    fun startRecognition() {
        // Requirement 5.3: Idempotency guard
        if (recognitionJob != null) return

        // Requirement 2.2: No file selected guard
        if (selectedFileUri == null) {
            Toast.makeText(context, "ファイルを選択してください", Toast.LENGTH_SHORT).show()
            return
        }

        // Requirement 2.3: SDK version guard
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(context, "この機能はAndroid 12以上で利用できます", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = selectedFileUri!!

        // Requirement 4.3: Create pipe — validates URI is accessible before starting
        // AudioSource.fromPfd() expects raw PCM; decodeAudioToPcm() decodes any format (MP3, AAC, …)
        val pipe = try {
            ParcelFileDescriptor.createPipe()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create pipe: ${e.message}", e)
            Toast.makeText(context, "ファイルを読み込めませんでした: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }
        val readFd = pipe[0]   // passed to AudioSource.fromPfd()
        val writeFd = pipe[1]  // decoder writes PCM here

        isProcessing = true
        partialText = ""

        val request = SpeechRecognizerRequest.Builder().apply {
            audioSource = AudioSource.fromPfd(readFd)
        }.build()

        // Decoder job: runs on IO thread, decodes audio → writes PCM to writeFd
        val decoderJob = scope.launch(Dispatchers.IO) {
            decodeAudioToPcm(context, uri, writeFd)
        }

        recognitionJob = scope.launch {
            try {
                // Requirements 3.1–3.3: Handle streaming responses
                speechRecognizer.startRecognition(request).collect { response ->
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse -> {
                            partialText = response.text
                        }
                        is SpeechRecognizerResponse.FinalTextResponse -> {
                            messages.add(UIChatMessage(response.text))
                            partialText = ""
                        }
                        is SpeechRecognizerResponse.ErrorResponse -> {
                            // Requirement 4.1
                            Log.e(TAG, "Recognition ErrorResponse: ${response.e.message}", response.e)
                            Toast.makeText(
                                context, "エラー: ${response.e.message}", Toast.LENGTH_SHORT
                            ).show()
                        }
                        is SpeechRecognizerResponse.CompletedResponse -> {
                            // Requirement 3.3
                            isProcessing = false
                            recognitionJob = null
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Requirement 4.4: CancellationException is silent
                Log.d(TAG, "Recognition cancelled (expected control flow): ${e.message}")
            } catch (e: Exception) {
                // Requirement 4.2
                Log.e(TAG, "Unexpected exception during recognition: ${e.message}", e)
                Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // Cancel decoder and close pipe read end on every exit path
                decoderJob.cancel()
                runCatching { readFd.close() }
                isProcessing = false
                recognitionJob = null
            }
        }
    }

    // --- UI ---
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
            // Requirement 1.4: Selected file label
            Text(
                text = if (selectedFileName.isNotBlank()) selectedFileName
                       else "ファイルが選択されていません",
                modifier = Modifier.fillMaxWidth()
            )

            // Requirement 1.1: File select button, disabled during processing (2.4)
            Button(
                onClick = { fileLauncher.launch("audio/*") },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ファイルを選択")
            }

            // Requirement 2.1: Start recognition button, disabled during processing
            Button(
                onClick = { startRecognition() },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("認識開始")
            }

            // Requirement 2.5: Processing indicator
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("progress_indicator")
                )
            }

            // Requirements 3.4–3.5: Results list with placeholder
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty() && partialText.isBlank()) {
                    item {
                        Text("認識結果がここに表示されます")
                    }
                }
                items(messages) { message ->
                    ChatBubble(message.text)
                }
                if (partialText.isNotBlank()) {
                    item {
                        ChatBubble(partialText)
                    }
                }
            }
        }
    }
}

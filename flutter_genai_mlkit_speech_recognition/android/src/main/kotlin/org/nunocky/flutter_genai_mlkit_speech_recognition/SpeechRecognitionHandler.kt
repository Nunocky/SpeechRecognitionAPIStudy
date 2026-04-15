package org.nunocky.flutter_genai_mlkit_speech_recognition

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

private const val TAG = "SpeechRecognitionHandler"

class SpeechRecognitionHandler(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private var eventSink: EventChannel.EventSink? = null
    private var recognitionJob: Job? = null
    private var currentLocaleTag: String = SupportedSpeechLocales.DefaultLocaleTag
    private val resultBuffer = ResultBuffer()
    private var runCounter: Long = 0

    fun setEventSink(sink: EventChannel.EventSink?) {
        eventSink = sink
    }

    fun setLocale(localeTag: String) {
        val sanitizedTag = SupportedSpeechLocales.sanitize(localeTag)
        if (sanitizedTag != currentLocaleTag && recognitionJob == null) {
            currentLocaleTag = sanitizedTag
        }
    }

    private fun createSpeechRecognizer(localeTag: String): SpeechRecognizer {
        val options = SpeechRecognizerOptions.Builder().apply {
            locale = Locale.forLanguageTag(localeTag)
        }.build()
        return SpeechRecognition.getClient(options)
    }

    private suspend fun ensureRecognizerReady(
        recognizer: SpeechRecognizer,
        runId: Long
    ): Boolean {
        val status = recognizer.checkStatus()
        Log.d(TAG, "ensureRecognizerReady[$runId]: checkStatus=$status")

        if (status == FeatureStatus.AVAILABLE) return true

        if (status == FeatureStatus.DOWNLOADABLE) {
            Log.d(TAG, "ensureRecognizerReady[$runId]: feature downloadable, start download")
            var totalToDownload: Long = 0
            recognizer.download().collect { downloadStatus ->
                when (downloadStatus) {
                    is DownloadStatus.DownloadStarted -> {
                        totalToDownload = downloadStatus.bytesToDownload
                        Log.d(TAG, "download started bytes=$totalToDownload")
                    }
                    is DownloadStatus.DownloadProgress -> {
                        Log.d(TAG, "downloading ${downloadStatus.totalBytesDownloaded}/$totalToDownload")
                    }
                    is DownloadStatus.DownloadCompleted -> {
                        Log.d(TAG, "download completed")
                    }
                    is DownloadStatus.DownloadFailed -> {
                        throw IllegalStateException("Model download failed: ${downloadStatus.e.message}")
                    }
                }
            }

            val recheck = recognizer.checkStatus()
            Log.d(TAG, "ensureRecognizerReady[$runId]: checkStatus(after download)=$recheck")
            return recheck == FeatureStatus.AVAILABLE
        }

        return false
    }

    fun startMicRecognition() {
        if (recognitionJob != null) {
            Log.w(TAG, "Recognition already in progress")
            return
        }

        val runId = ++runCounter
        Log.d(TAG, "startMicRecognition[$runId]: locale=$currentLocaleTag")

        recognitionJob = coroutineScope.launch {
            val speechRecognizer = createSpeechRecognizer(currentLocaleTag)
            try {
                val ready = ensureRecognizerReady(speechRecognizer, runId)
                if (!ready) {
                    sendEvent(mapOf(
                        "type" to "error",
                        "message" to "Speech feature is not available yet. Please retry in a few moments."
                    ))
                    return@launch
                }

                val request = SpeechRecognizerRequest.Builder().apply {
                    audioSource = AudioSource.fromMic()
                }.build()

                speechRecognizer.startRecognition(request).collect { response ->
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse -> {
                            sendEvent(mapOf(
                                "type" to "partial",
                                "text" to response.text
                            ))
                        }
                        is SpeechRecognizerResponse.FinalTextResponse -> {
                            sendEvent(mapOf(
                                "type" to "final",
                                "text" to response.text
                            ))
                        }
                        is SpeechRecognizerResponse.ErrorResponse -> {
                            sendEvent(mapOf(
                                "type" to "error",
                                "message" to (response.e.message ?: "Unknown error")
                            ))
                        }
                        is SpeechRecognizerResponse.CompletedResponse -> {
                            sendEvent(mapOf("type" to "completed"))
                            recognitionJob = null
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Recognition cancelled[$runId]")
            } catch (e: Exception) {
                Log.e(TAG, "Recognition error[$runId]: ${e.message}", e)
                sendEvent(mapOf(
                    "type" to "error",
                    "message" to (e.message ?: "Unknown error")
                ))
            } finally {
                runCatching { speechRecognizer.stopRecognition() }
                runCatching { speechRecognizer.close() }
                recognitionJob = null
                Log.d(TAG, "startMicRecognition[$runId]: recognizer closed")
            }
        }
    }

    fun startFileRecognition(filePath: String) {
        if (recognitionJob != null) {
            Log.w(TAG, "Recognition already in progress")
            return
        }

        val runId = ++runCounter
        Log.d(TAG, "startFileRecognition[$runId]: file=$filePath locale=$currentLocaleTag")

        resultBuffer.clear()

        recognitionJob = coroutineScope.launch {
            var tempPcmFile: File? = null
            var readFd: ParcelFileDescriptor? = null
            var writeFd: ParcelFileDescriptor? = null
            var streamJob: Job? = null
            val speechRecognizer = createSpeechRecognizer(currentLocaleTag)

            try {
                val ready = ensureRecognizerReady(speechRecognizer, runId)
                if (!ready) {
                    sendEvent(mapOf(
                        "type" to "error",
                        "message" to "Speech feature is not available yet. Please retry in a few moments."
                    ))
                    return@launch
                }

                val uri = Uri.parse(filePath)
                tempPcmFile = withContext(Dispatchers.IO) {
                    createTempPcmFile(context, uri)
                }
                Log.d(TAG, "startFileRecognition[$runId]: temp pcm bytes=${tempPcmFile.length()}")

                val pipe = ParcelFileDescriptor.createPipe()
                readFd = pipe[0]
                writeFd = pipe[1]

                streamJob = coroutineScope.launch(Dispatchers.IO) {
                    streamPcmFileToPipe(tempPcmFile, writeFd)
                }

                val request = SpeechRecognizerRequest.Builder().apply {
                    audioSource = AudioSource.fromPfd(readFd)
                }.build()

                speechRecognizer.startRecognition(request).collect { response ->
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse -> {
                            sendEvent(mapOf(
                                "type" to "partial",
                                "text" to response.text
                            ))
                        }
                        is SpeechRecognizerResponse.FinalTextResponse -> {
                            resultBuffer.add(response.text)
                            sendEvent(mapOf(
                                "type" to "final",
                                "text" to response.text
                            ))
                        }
                        is SpeechRecognizerResponse.ErrorResponse -> {
                            Log.e(TAG, "Recognition ErrorResponse[$runId]: ${response.e.message}", response.e)
                            sendEvent(mapOf(
                                "type" to "error",
                                "message" to (response.e.message ?: "Unknown error")
                            ))
                            resultBuffer.clear()
                        }
                        is SpeechRecognizerResponse.CompletedResponse -> {
                            val aggregatedText = resultBuffer.getAggregated()
                            sendEvent(mapOf(
                                "type" to "completed",
                                "aggregatedText" to aggregatedText
                            ))
                            resultBuffer.clear()
                            recognitionJob = null
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "File recognition cancelled[$runId]")
                resultBuffer.clear()
            } catch (e: Exception) {
                Log.e(TAG, "File recognition error[$runId]: ${e.message}", e)
                sendEvent(mapOf(
                    "type" to "error",
                    "message" to (e.message ?: "Unknown error")
                ))
                resultBuffer.clear()
            } finally {
                streamJob?.cancel()
                runCatching { readFd?.close() }
                runCatching { writeFd?.close() }
                runCatching { speechRecognizer.stopRecognition() }
                runCatching { speechRecognizer.close() }
                runCatching {
                    val deleted = tempPcmFile?.delete() ?: true
                    Log.d(TAG, "startFileRecognition[$runId]: temp pcm deleted=$deleted")
                }
                recognitionJob = null
                Log.d(TAG, "startFileRecognition[$runId]: recognizer closed")
            }
        }
    }

    fun stopRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        resultBuffer.clear()
        Log.d(TAG, "stopRecognition: job cancelled")
    }

    fun cleanup() {
        stopRecognition()
        eventSink = null
    }

    private fun sendEvent(event: Map<String, String>) {
        eventSink?.success(event)
    }
}

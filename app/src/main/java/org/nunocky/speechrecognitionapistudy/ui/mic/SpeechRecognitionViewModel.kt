package org.nunocky.speechrecognitionapistudy.ui.mic

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
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
import kotlinx.coroutines.launch
import org.nunocky.speechrecognitionapistudy.ui.component.UIChatMessage
import java.util.Locale

data class SpeechRecognitionUiState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val messages: List<UIChatMessage> = emptyList()
)

sealed class SpeechRecognitionUiEvent {
    data class ShowError(val message: String) : SpeechRecognitionUiEvent()
    object UnsupportedVersion : SpeechRecognitionUiEvent()
}

class SpeechRecognitionViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SpeechRecognitionUiState())
    val uiState: StateFlow<SpeechRecognitionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SpeechRecognitionUiEvent>()
    val events = _events.asSharedFlow()

    private val speechRecognizer = run {
        val options = SpeechRecognizerOptions.Builder().apply {
            locale = Locale.JAPAN
        }.build()
        SpeechRecognition.getClient(options)
    }

    private var recognitionJob: Job? = null

    fun startListening() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            viewModelScope.launch {
                _events.emit(SpeechRecognitionUiEvent.UnsupportedVersion)
            }
            return
        }

        if (recognitionJob != null) return

        _uiState.update { it.copy(isListening = true, partialText = "") }

        val request = SpeechRecognizerRequest.Builder().apply {
            audioSource = AudioSource.fromMic()
        }.build()

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
                            _events.emit(
                                SpeechRecognitionUiEvent.ShowError(
                                    response.e.message ?: "Unknown error"
                                )
                            )
                        }

                        is SpeechRecognizerResponse.CompletedResponse -> {
                            _uiState.update { it.copy(isListening = false) }
                            recognitionJob = null
                        }
                    }
                }
            } catch (_: CancellationException) {
                // stopRecognition によるキャンセル時は何もしない
            } catch (e: Exception) {
                _events.emit(SpeechRecognitionUiEvent.ShowError(e.message ?: "Unknown error"))
            } finally {
                _uiState.update { it.copy(isListening = false) }
                recognitionJob = null
            }
        }
    }

    fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        _uiState.update { it.copy(isListening = false, partialText = "") }
        viewModelScope.launch {
            runCatching { speechRecognizer.stopRecognition() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.close()
    }
}



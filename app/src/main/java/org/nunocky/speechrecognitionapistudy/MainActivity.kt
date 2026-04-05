package org.nunocky.speechrecognitionapistudy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.nunocky.speechrecognitionapistudy.ui.theme.SpeechRecognitionAPIStudyTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeechRecognitionAPIStudyTheme {
                SpeechRecognitionScreen()
            }
        }
    }
}

data class UIChatMessage(val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechRecognitionScreen() {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<UIChatMessage>() }
    val scope = rememberCoroutineScope()
    var recognitionJob by remember { mutableStateOf<Job?>(null) }

    val speechRecognizer = remember {
        val options = SpeechRecognizerOptions.Builder().apply {
            locale = Locale.JAPAN
        }.build()
        SpeechRecognition.getClient(options)
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            speechRecognizer.close()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted
        } else {
            Toast.makeText(context, "マイクの権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (recognitionJob != null) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(context, "この機能はAndroid 12以上で利用できます", Toast.LENGTH_SHORT)
                .show()
            return
        }

        isListening = true
        partialText = ""

        val request = SpeechRecognizerRequest.Builder().apply {
            audioSource = AudioSource.fromMic()
        }.build()

        recognitionJob = scope.launch {
            try {
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
                            Toast.makeText(
                                context,
                                "エラー: ${response.e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        is SpeechRecognizerResponse.CompletedResponse -> {
                            isListening = false
                            recognitionJob = null
                        }
                    }
                }
            } catch (e: CancellationException) {
                // stopRecognitionによるキャンセル時は何もしない
            } catch (e: Exception) {
                Toast.makeText(context, "エラー: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isListening = false
                recognitionJob = null
            }
        }
    }

    fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        partialText = ""
        scope.launch {
            runCatching { speechRecognizer.stopRecognition() }
        }
        isListening = false
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Speech Recognition") })
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                FloatingActionButton(
                    onClick = {
                        if (isListening) {
                            stopListening()
                        } else {
                            startListening()
                        }
                    },
                    shape = CircleShape,
                    containerColor = if (isListening) Color.Red else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
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

@Composable
fun ChatBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            color = Color(0xFFE0E0E0),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(8.dp),
                color = Color.Black
            )
        }
    }
}

package org.nunocky.speechrecognitionapistudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.nunocky.speechrecognitionapistudy.ui.theme.SpeechRecognitionAPIStudyTheme

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

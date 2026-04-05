package org.nunocky.speechrecognitionapistudy

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.nunocky.speechrecognitionapistudy.ui.file.FileToTtsScreen
import org.nunocky.speechrecognitionapistudy.ui.main.StartupMenuScreen
import org.nunocky.speechrecognitionapistudy.ui.mic.SpeechRecognitionScreen

private object AppRoute {
    const val Menu = "menu"
    const val MicToTts = "mic_to_tts"
    const val FileToTts = "file_to_tts"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.Menu
    ) {
        composable(AppRoute.Menu) {
            StartupMenuScreen(
                onMicToTtsClick = { navController.navigate(AppRoute.MicToTts) },
                onFileToTtsClick = { navController.navigate(AppRoute.FileToTts) }
            )
        }
        composable(AppRoute.MicToTts) {
            SpeechRecognitionScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.FileToTts) {
            FileToTtsScreen(onBack = { navController.popBackStack() })
        }
    }
}


package org.nunocky.speechrecognitionapistudy

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.nunocky.speechrecognitionapistudy.locale.SupportedSpeechLocales
import org.nunocky.speechrecognitionapistudy.ui.file.FileToTtsScreen
import org.nunocky.speechrecognitionapistudy.ui.main.StartupMenuScreen
import org.nunocky.speechrecognitionapistudy.ui.mic.SpeechRecognitionScreen

private object AppRoute {
    const val LocaleArg = "localeTag"
    const val Menu = "menu"
    const val MicToTts = "mic_to_tts?$LocaleArg={$LocaleArg}"
    const val FileToTts = "file_to_tts?$LocaleArg={$LocaleArg}"

    fun micToTts(localeTag: String): String = "mic_to_tts?$LocaleArg=${Uri.encode(localeTag)}"
    fun fileToTts(localeTag: String): String = "file_to_tts?$LocaleArg=${Uri.encode(localeTag)}"
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
                onMicToTtsClick = { localeTag -> navController.navigate(AppRoute.micToTts(localeTag)) },
                onFileToTtsClick = { localeTag -> navController.navigate(AppRoute.fileToTts(localeTag)) }
            )
        }
        composable(
            route = AppRoute.MicToTts,
            arguments = listOf(
                navArgument(AppRoute.LocaleArg) {
                    type = NavType.StringType
                    defaultValue = SupportedSpeechLocales.DefaultLocaleTag
                }
            )
        ) { backStackEntry ->
            val localeTag = SupportedSpeechLocales.sanitize(
                backStackEntry.arguments?.getString(AppRoute.LocaleArg).orEmpty()
            )
            SpeechRecognitionScreen(
                localeTag = localeTag,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = AppRoute.FileToTts,
            arguments = listOf(
                navArgument(AppRoute.LocaleArg) {
                    type = NavType.StringType
                    defaultValue = SupportedSpeechLocales.DefaultLocaleTag
                }
            )
        ) { backStackEntry ->
            val localeTag = SupportedSpeechLocales.sanitize(
                backStackEntry.arguments?.getString(AppRoute.LocaleArg).orEmpty()
            )
            FileToTtsScreen(
                localeTag = localeTag,
                onBack = { navController.popBackStack() }
            )
        }
    }
}


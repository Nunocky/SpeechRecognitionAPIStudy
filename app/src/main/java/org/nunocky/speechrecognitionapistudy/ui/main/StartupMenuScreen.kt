package org.nunocky.speechrecognitionapistudy.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.nunocky.speechrecognitionapistudy.locale.SupportedSpeechLocales
import org.nunocky.speechrecognitionapistudy.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartupMenuScreen(
    onMicToTtsClick: (localeTag: String) -> Unit,
    onFileToTtsClick: (localeTag: String) -> Unit
) {
    var localeMenuExpanded by remember { mutableStateOf(false) }
    var selectedLocaleTag by rememberSaveable { mutableStateOf(SupportedSpeechLocales.DefaultLocaleTag) }
    val selectedLocaleOption = SupportedSpeechLocales.options.firstOrNull { it.tag == selectedLocaleTag }
        ?: SupportedSpeechLocales.options.first { it.tag == SupportedSpeechLocales.DefaultLocaleTag }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.screen_title_tts_mode_select)) })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.label_locale_selector),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
            OutlinedButton(
                onClick = { localeMenuExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedLocaleOption.displayName)
            }
            DropdownMenu(
                expanded = localeMenuExpanded,
                onDismissRequest = { localeMenuExpanded = false }
            ) {
                SupportedSpeechLocales.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            selectedLocaleTag = option.tag
                            localeMenuExpanded = false
                        }
                    )
                }
            }
            Button(
                onClick = { onMicToTtsClick(selectedLocaleTag) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.button_mic_to_tts))
            }
            Button(
                onClick = { onFileToTtsClick(selectedLocaleTag) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.button_file_to_tts))
            }
        }
    }
}


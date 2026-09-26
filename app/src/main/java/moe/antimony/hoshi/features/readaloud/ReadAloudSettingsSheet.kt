package moe.antimony.hoshi.features.readaloud

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.reader.ReaderBottomPanel
import moe.antimony.hoshi.features.reader.readerSheetStyle
import moe.antimony.hoshi.ui.theme.hoshiContainerBorder
import moe.antimony.hoshi.ui.theme.hoshiSurfaces
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAloudSettingsSheet(
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ReadAloudViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var systemEngines by remember { mutableStateOf(emptyList<TextToSpeech.EngineInfo>()) }
    LaunchedEffect(viewModel) {
        systemEngines = viewModel.availableSystemEngines()
    }

    ReaderBottomPanel(
        sheetStyle = readerSheetStyle(),
        onDismiss = onDismiss,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.read_aloud_settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SettingsCard {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.read_aloud_engine)) },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReadAloudSystemEngineChip(
                        label = stringResource(R.string.read_aloud_engine_system_default),
                        selected = settings.selectedSystemEngineName == null,
                        onClick = { viewModel.setSystemEngine(null) },
                    )
                    systemEngines.forEach { engine ->
                        val label = engine.label?.ifBlank { engine.name } ?: engine.name
                        ReadAloudSystemEngineChip(
                            label = label,
                            selected = settings.selectedSystemEngineName == engine.name,
                            onClick = { viewModel.setSystemEngine(engine.name) },
                        )
                    }
                }
            }
            SettingsCard {
                val context = LocalContext.current
                val phonePermission = Manifest.permission.READ_PHONE_STATE
                val phonePermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    viewModel.updateSettings { it.copy(pauseWhilePhoneCalls = granted) }
                }
                ReadAloudToggleRow(
                    title = stringResource(R.string.read_aloud_ignore_audio_focus),
                    subtitle = stringResource(R.string.read_aloud_ignore_audio_focus_desc),
                    checked = settings.ignoreAudioFocus,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(ignoreAudioFocus = enabled) }
                    },
                )
                ReadAloudToggleRow(
                    title = stringResource(R.string.read_aloud_pause_phone_calls),
                    subtitle = stringResource(R.string.read_aloud_pause_phone_calls_desc),
                    checked = settings.pauseWhilePhoneCalls,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            viewModel.updateSettings { it.copy(pauseWhilePhoneCalls = false) }
                        } else {
                            val granted = ContextCompat.checkSelfPermission(context, phonePermission) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.updateSettings { it.copy(pauseWhilePhoneCalls = true) }
                            } else {
                                phonePermissionLauncher.launch(phonePermission)
                            }
                        }
                    },
                )
                ReadAloudToggleRow(
                    title = stringResource(R.string.read_aloud_wake_lock),
                    subtitle = stringResource(R.string.read_aloud_wake_lock_desc),
                    checked = settings.wakeLock,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(wakeLock = enabled) }
                    },
                )
                ReadAloudToggleRow(
                    title = stringResource(R.string.read_aloud_media_button),
                    subtitle = stringResource(R.string.read_aloud_media_button_desc),
                    checked = settings.mediaButtonPerNext,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(mediaButtonPerNext = enabled) }
                    },
                )
                ReadAloudToggleRow(
                    title = stringResource(R.string.read_aloud_by_page),
                    subtitle = stringResource(R.string.read_aloud_by_page_desc),
                    checked = settings.readAloudByPage,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(readAloudByPage = enabled) }
                    },
                )
                ReadAloudToggleRow(
                    title = stringResource(R.string.read_aloud_highlight_while_playing),
                    subtitle = stringResource(R.string.read_aloud_highlight_while_playing_desc),
                    checked = settings.highlightWhilePlaying,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(highlightWhilePlaying = enabled) }
                    },
                )
                ReadAloudToggleRow(
                    title = stringResource(R.string.read_aloud_translate_current_sentence),
                    subtitle = stringResource(R.string.read_aloud_translate_current_sentence_desc),
                    checked = settings.translateCurrentSentence,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(translateCurrentSentence = enabled) }
                    },
                )
                ReadAloudToggleRow(
                    title = stringResource(R.string.read_aloud_auto_pause_on_lookup),
                    subtitle = stringResource(R.string.read_aloud_auto_pause_on_lookup_desc),
                    checked = settings.pauseForLookup,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(pauseForLookup = enabled) }
                    },
                )
                ReadAloudToggleRow(
                    title = stringResource(R.string.read_aloud_auto_pause_on_page_translation),
                    subtitle = stringResource(R.string.read_aloud_auto_pause_on_page_translation_desc),
                    checked = settings.pauseForPageTranslation,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings { it.copy(pauseForPageTranslation = enabled) }
                    },
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.read_aloud_system_tts_settings)) },
                    supportingContent = { Text(stringResource(R.string.read_aloud_system_tts_settings_desc)) },
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                        }
                    },
                )
            }
            SettingsCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.read_aloud_speech_rate),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = settings.speechRate,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        valueRange = ReadAloudSettings.MinimumSpeechRate..ReadAloudSettings.MaximumSpeechRate,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "%.1fx", settings.speechRate),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = stringResource(R.string.read_aloud_sentence_repeat_count),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Slider(
                        value = settings.sentenceRepeatCount.toFloat(),
                        onValueChange = { viewModel.setSentenceRepeatCount(it.roundToInt()) },
                        valueRange = ReadAloudSettings.MinimumSentenceRepeatCount.toFloat()..
                            ReadAloudSettings.MaximumSentenceRepeatCount.toFloat(),
                        steps = ReadAloudSettings.MaximumSentenceRepeatCount -
                            ReadAloudSettings.MinimumSentenceRepeatCount - 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.read_aloud_sentence_repeat_count_value,
                            settings.sentenceRepeatCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    }
                    Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    ) {
                    Text(stringResource(R.string.read_aloud_start))
                    }
                    }
                    }
                    }
}

@Composable
private fun ReadAloudToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}

@Composable
private fun ReadAloudSystemEngineChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = hoshiSurfaces.group,
        border = hoshiContainerBorder(),
        tonalElevation = 0.dp,
    ) {
        Column(content = { content() })
    }
}

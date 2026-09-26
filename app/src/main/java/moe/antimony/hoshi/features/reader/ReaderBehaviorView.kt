package moe.antimony.hoshi.features.reader

import moe.antimony.hoshi.ui.theme.hoshiSurfaces
import moe.antimony.hoshi.ui.theme.hoshiContainerBorder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.settings.SettingsDetailScaffold
import moe.antimony.hoshi.features.settings.collectAsLoadedSettings

@Composable
fun ReaderBehaviorScreen(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContainer = LocalHoshiUiDependencies.current
    val updateSettings = appContainer.updateSettingsRepository.settings.collectAsLoadedSettings()
    val scope = rememberCoroutineScope()
    SettingsDetailScaffold(
        title = stringResource(R.string.settings_behavior),
        onClose = onClose,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            item {
                val loadedUpdateSettings = updateSettings ?: return@item
                BehaviorSettingsCard {
                    BehaviorSwitchRow(
                        label = stringResource(ReaderBehaviorRow.VolumeKeysTurnPages.labelRes),
                        checked = settings.volumeKeysTurnPages,
                        onCheckedChange = {
                            onSettingsChange { current -> current.copy(volumeKeysTurnPages = it) }
                        },
                    )
                    BehaviorDivider()
                    BehaviorSwitchRow(
                        label = stringResource(ReaderBehaviorRow.VolumeKeysNavigatePopupTerms.labelRes),
                        checked = settings.volumeKeysNavigatePopupTerms,
                        onCheckedChange = {
                            onSettingsChange { current -> current.copy(volumeKeysNavigatePopupTerms = it) }
                        },
                    )
                    readerBehaviorSasayakiRows().forEach { labelRes ->
                        BehaviorDivider()
                        BehaviorSwitchRow(
                            label = stringResource(labelRes),
                            checked = settings.volumeKeysSeekSasayaki,
                            onCheckedChange = {
                                onSettingsChange { current -> current.copy(volumeKeysSeekSasayaki = it) }
                            },
                        )
                    }
                    BehaviorDivider()
                    BehaviorSwitchRow(
                        label = stringResource(ReaderBehaviorRow.VolumeKeysControlReadAloud.labelRes),
                        checked = settings.volumeKeysControlReadAloud,
                        onCheckedChange = {
                            onSettingsChange { current -> current.copy(volumeKeysControlReadAloud = it) }
                        },
                    )
                    BehaviorDivider()
                    BehaviorSwitchRow(
                        label = stringResource(ReaderBehaviorRow.ReverseVolumeKeyDirection.labelRes),
                        checked = settings.reverseVolumeKeyDirection,
                        onCheckedChange = {
                            onSettingsChange { current -> current.copy(reverseVolumeKeyDirection = it) }
                        },
                    )
                    BehaviorDivider()
                    BehaviorSwitchRow(
                        label = stringResource(ReaderBehaviorRow.KeepScreenOn.labelRes),
                        checked = settings.keepScreenOnWhileReading,
                        onCheckedChange = {
                            onSettingsChange { current -> current.copy(keepScreenOnWhileReading = it) }
                        },
                    )
                    BehaviorDivider()
                    BehaviorSwitchRow(
                        label = stringResource(ReaderBehaviorRow.LockCurrentOrientation.labelRes),
                        checked = settings.lockCurrentOrientation,
                        onCheckedChange = {
                            onSettingsChange { current -> current.copy(lockCurrentOrientation = it) }
                        },
                    )
                    BehaviorDivider()
                    BehaviorSwitchRow(
                        label = stringResource(ReaderBehaviorRow.OpenLastReadBookOnLaunch.labelRes),
                        checked = settings.openLastReadBookOnLaunch,
                        onCheckedChange = {
                            onSettingsChange { current -> current.copy(openLastReadBookOnLaunch = it) }
                        },
                    )
                    BehaviorDivider()
                    BehaviorSwitchRow(
                        label = stringResource(ReaderBehaviorRow.AutomaticallyCheckForUpdates.labelRes),
                        checked = loadedUpdateSettings.autoCheckUpdates,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                appContainer.updateSettingsRepository.update {
                                    it.copy(autoCheckUpdates = enabled)
                                }
                                if (enabled) {
                                    appContainer.updateScheduler.schedule()
                                    appContainer.updateScheduler.scheduleImmediateCheck()
                                } else {
                                    appContainer.updateScheduler.cancel()
                                }
                            }
                        },
                    )
                }
            }
            item {
                BehaviorSettingsCard {
                    BehaviorSwitchRow(
                        label = stringResource(R.string.reader_auto_play),
                        checked = settings.autoPlayEnabled,
                        onCheckedChange = {
                            onSettingsChange { current -> current.copy(autoPlayEnabled = it) }
                        },
                    )
                    if (settings.autoPlayEnabled) {
                        BehaviorDivider()
                        AutoPlaySpeedRow(
                            selected = settings.autoPlaySpeed,
                            onSelected = {
                                onSettingsChange { current -> current.copy(autoPlaySpeed = it) }
                            },
                        )
                    }
                }
            }
            item {
                BehaviorSettingsCard {
                    Text(
                        text = stringResource(R.string.reader_behavior_gestures),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                    )
                    GestureActionRow(
                        label = stringResource(R.string.reader_behavior_double_tap_action),
                        options = ReaderGestureAction.doubleTapOptions(),
                        selected = settings.readerDoubleTapAction,
                        onSelected = {
                            onSettingsChange { current -> current.copy(readerDoubleTapAction = it) }
                        },
                    )
                    BehaviorDivider()
                    GestureActionRow(
                        label = stringResource(R.string.reader_behavior_long_press_action),
                        options = ReaderGestureAction.longPressOptions(),
                        selected = settings.readerLongPressAction,
                        onSelected = {
                            onSettingsChange { current -> current.copy(readerLongPressAction = it) }
                        },
                    )
                }
            }
        }
    }
}

private fun ReaderGestureAction.gestureLabelRes(): Int = when (this) {
    ReaderGestureAction.None -> R.string.reader_gesture_action_none
    ReaderGestureAction.SentenceReadAloud -> R.string.reader_gesture_action_sentence
    ReaderGestureAction.SentenceReadAloudAndTranslate -> R.string.reader_gesture_action_sentence_read_aloud_translate
    ReaderGestureAction.SentenceTranslate -> R.string.reader_gesture_action_sentence_translate
    ReaderGestureAction.WordSelection -> R.string.reader_gesture_action_word_selection
}

@Composable
private fun GestureActionRow(
    label: String,
    options: List<ReaderGestureAction>,
    selected: ReaderGestureAction,
    onSelected: (ReaderGestureAction) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = hoshiSurfaces.group),
        headlineContent = {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        },
        trailingContent = {
            // 句子手势最多四项，横排放不下时换行，避免挤出列表项。
            FlowRow(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { action ->
                    val isSelected = action == selected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onSelected(action) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(action.gestureLabelRes()),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AutoPlaySpeedRow(
    selected: AutoPlaySpeed,
    onSelected: (AutoPlaySpeed) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = hoshiSurfaces.group),
        headlineContent = {
            Text(
                text = stringResource(R.string.reader_auto_play_speed),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        trailingContent = {
            FlowRow(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AutoPlaySpeed.entries.forEach { speed ->
                    val isSelected = speed == selected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onSelected(speed) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(speed.labelResId),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
    )
}

internal fun readerBehaviorSasayakiRows(): List<Int> =
    listOf(ReaderBehaviorRow.VolumeKeysSeekSasayaki.labelRes)

internal fun readerBehaviorRows(): List<Int> = ReaderBehaviorRow.entries.map { it.labelRes }

private enum class ReaderBehaviorRow(val labelRes: Int) {
    VolumeKeysTurnPages(R.string.reader_behavior_volume_keys_turn_pages),
    VolumeKeysNavigatePopupTerms(R.string.reader_behavior_volume_keys_navigate_popup_terms),
    VolumeKeysSeekSasayaki(R.string.reader_behavior_volume_keys_seek_sasayaki),
    VolumeKeysControlReadAloud(R.string.reader_behavior_volume_keys_control_read_aloud),
    ReverseVolumeKeyDirection(R.string.reader_behavior_reverse_volume_key_direction),
    KeepScreenOn(R.string.reader_behavior_keep_screen_on),
    LockCurrentOrientation(R.string.reader_behavior_lock_current_orientation),
    OpenLastReadBookOnLaunch(R.string.reader_behavior_open_last_read_book_on_launch),
    AutomaticallyCheckForUpdates(R.string.reader_behavior_auto_check_updates),
}

@Composable
private fun BehaviorSettingsCard(content: @Composable () -> Unit) {
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

@Composable
private fun BehaviorSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = hoshiSurfaces.group),
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun BehaviorDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

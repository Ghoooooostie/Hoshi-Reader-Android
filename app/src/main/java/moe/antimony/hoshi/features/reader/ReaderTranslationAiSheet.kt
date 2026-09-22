package moe.antimony.hoshi.features.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.antimony.hoshi.R
import moe.antimony.hoshi.ui.theme.LocalHoshiEInkMode
import kotlin.math.roundToInt

@Composable
internal fun ReaderTranslationAiSheet(
    settings: ReaderSettings,
    fullPageTranslationSupported: Boolean,
    availabilityHint: String?,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = translationAiPalette()
    val metrics = readerSheetDensityMetrics()
    val sheetStyle = readerSheetStyle().copy(
        containerColor = palette.background,
        contentColor = palette.onBackground,
    )
    ReaderBottomPanel(
        sheetStyle = sheetStyle,
        onDismiss = onDismiss,
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides palette.onBackground) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)),
                verticalArrangement = Arrangement.spacedBy(metrics.appearanceSectionSpacingDp.dp),
            ) {
                TranslationAiSection(
                    title = stringResource(R.string.reader_translation_ai_page_section),
                    palette = palette,
                ) {
                    TranslationAiSwitchRow(
                        label = stringResource(R.string.reader_translation_ai_enable),
                        supporting = if (fullPageTranslationSupported) {
                            stringResource(R.string.reader_translation_ai_page_supporting)
                        } else {
                            stringResource(R.string.reader_translation_ai_unsupported_vn)
                        },
                        checked = settings.readerAiFullPageTranslationEnabled,
                        enabled = fullPageTranslationSupported,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(readerAiFullPageTranslationEnabled = it))
                        },
                    )
                    TranslationAiDivider(palette)
                    TranslationAiSegmentedRow(
                        label = stringResource(R.string.reader_translation_ai_display_mode),
                        options = listOf(
                            stringResource(R.string.reader_translation_ai_display_persistent),
                            stringResource(R.string.reader_translation_ai_display_long_press),
                        ),
                        selectedIndex = ReaderAiFullPageTranslationDisplayMode.entries
                            .indexOf(settings.readerAiFullPageTranslationDisplayMode)
                            .coerceAtLeast(0),
                        enabled = settings.readerAiFullPageTranslationEnabled && fullPageTranslationSupported,
                        onSelected = { index ->
                            ReaderAiFullPageTranslationDisplayMode.entries.getOrNull(index)?.let { mode ->
                                onSettingsChange(settings.copy(readerAiFullPageTranslationDisplayMode = mode))
                            }
                        },
                        palette = palette,
                    )
                    TranslationAiSupportingText(
                        text = stringResource(R.string.reader_translation_ai_display_mode_supporting),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = metrics.appearanceRowVerticalPaddingDp.dp),
                    )
                    TranslationAiDivider(palette)
                    TranslationAiSwitchRow(
                        label = stringResource(R.string.reader_translation_ai_fallback_enable),
                        supporting = stringResource(R.string.reader_translation_ai_fallback_supporting),
                        checked = settings.readerAiTranslationFallbackEnabled,
                        enabled = settings.readerAiFullPageTranslationEnabled && fullPageTranslationSupported,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(readerAiTranslationFallbackEnabled = it))
                        },
                    )
                    availabilityHint?.takeIf { it.isNotBlank() }?.let { hint ->
                        TranslationAiDivider(palette)
                        TranslationAiSupportingText(
                            text = hint,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = metrics.appearanceRowVerticalPaddingDp.dp),
                        )
                    }
                }
                TranslationAiSection(
                    title = stringResource(R.string.reader_translation_ai_style_section),
                    palette = palette,
                ) {
                    var colorPickerOpen by remember { mutableStateOf(false) }
                    ReaderColorSettingRow(
                        label = stringResource(R.string.reader_translation_ai_style_color),
                        color = settings.translationColor,
                        onClick = { colorPickerOpen = true },
                    )
                    TranslationAiSupportingText(
                        text = stringResource(R.string.reader_translation_ai_style_color_supporting),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = metrics.appearanceRowVerticalPaddingDp.dp),
                    )
                    TranslationAiDivider(palette)
                    val opacity = settings.translationOpacity.coerceReaderTranslationOpacity()
                    TranslationAiSliderRow(
                        label = stringResource(R.string.reader_translation_ai_style_opacity),
                        value = "${(opacity * 100).roundToInt()}%",
                        sliderValue = opacity,
                        onValueChange = { value ->
                            onSettingsChange(settings.copy(translationOpacity = value.coerceReaderTranslationOpacity()))
                        },
                    )
                    if (colorPickerOpen) {
                        ReaderColorPickerDialog(
                            title = stringResource(R.string.reader_translation_ai_style_color),
                            initialColor = settings.translationColor,
                            defaultColor = ReaderTranslationColorFollowText,
                            onColorChange = { color ->
                                onSettingsChange(settings.copy(translationColor = color))
                                colorPickerOpen = false
                            },
                            onDismiss = { colorPickerOpen = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationAiSection(
    title: String,
    palette: TranslationAiPalette,
    content: @Composable ColumnScope.() -> Unit,
) {
    val metrics = readerSheetDensityMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.onMuted,
            modifier = Modifier.padding(start = 10.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(metrics.appearanceSectionCornerRadiusDp.dp),
            color = palette.group,
            contentColor = palette.onGroup,
            border = BorderStroke(1.dp, palette.divider),
            tonalElevation = 0.dp,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun TranslationAiSwitchRow(
    label: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val metrics = readerSheetDensityMetrics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = metrics.appearanceRowVerticalPaddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
        TranslationAiSupportingText(text = supporting)
    }
}

@Composable
private fun TranslationAiSegmentedRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    palette: TranslationAiPalette,
    enabled: Boolean = true,
) {
    val metrics = readerSheetDensityMetrics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 14.dp, vertical = metrics.appearanceWideRowVerticalPaddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = if (translationAiSegmentedControlUsesFullWidth(options)) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.width(segmentedControlWidthDp(options).dp)
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = palette.segmentContainer,
            contentColor = palette.onGroup,
            border = BorderStroke(1.dp, palette.segmentBorder),
            tonalElevation = 0.dp,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    TranslationAiSegmentButton(
                        text = option,
                        selected = index == selectedIndex,
                        palette = palette,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationAiSegmentButton(
    text: String,
    selected: Boolean,
    palette: TranslationAiPalette,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = if (selected) palette.segmentSelected else Color.Transparent,
        contentColor = if (selected) palette.segmentSelectedContent else palette.segmentUnselectedContent,
        tonalElevation = 0.dp,
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun translationAiSegmentedControlUsesFullWidth(options: List<String>): Boolean =
    options.size <= 2

@Composable
private fun TranslationAiSupportingText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TranslationAiSliderRow(
    label: String,
    value: String,
    sliderValue: Float,
    onValueChange: (Float) -> Unit,
) {
    val metrics = readerSheetDensityMetrics()
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = metrics.appearanceSliderVerticalPaddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = sliderValue,
            onValueChange = onValueChange,
            valueRange = ReaderTranslationOpacityMin..ReaderTranslationOpacityMax,
            steps = readerTranslationOpacitySliderSteps(),
        )
    }
}

@Composable
private fun TranslationAiDivider(palette: TranslationAiPalette) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = palette.divider,
    )
}

internal data class TranslationAiPalette(
    val background: Color,
    val group: Color,
    val onBackground: Color,
    val onGroup: Color,
    val onMuted: Color,
    val divider: Color,
    val segmentContainer: Color,
    val segmentSelected: Color,
    val segmentSelectedContent: Color,
    val segmentUnselectedContent: Color,
    val segmentBorder: Color,
)

@Composable
internal fun translationAiPalette(): TranslationAiPalette {
    val colorScheme = MaterialTheme.colorScheme
    val segmentedControlColors = readerSegmentedControlColors(
        eInkMode = LocalHoshiEInkMode.current,
        background = colorScheme.background,
        content = colorScheme.onBackground,
        surfaceVariant = colorScheme.surfaceVariant,
        primaryContainer = colorScheme.primaryContainer,
        onPrimaryContainer = colorScheme.onPrimaryContainer,
    )
    return TranslationAiPalette(
        background = colorScheme.background,
        group = colorScheme.surface,
        onBackground = colorScheme.onBackground,
        onGroup = colorScheme.onSurface,
        onMuted = colorScheme.onSurfaceVariant,
        divider = colorScheme.outlineVariant,
        segmentContainer = segmentedControlColors.container,
        segmentSelected = segmentedControlColors.selected,
        segmentSelectedContent = segmentedControlColors.selectedContent,
        segmentUnselectedContent = segmentedControlColors.unselectedContent,
        segmentBorder = colorScheme.outlineVariant,
    )
}

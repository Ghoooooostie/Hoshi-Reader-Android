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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.antimony.hoshi.R
import moe.antimony.hoshi.ui.theme.LocalHoshiEInkMode

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
                    availabilityHint?.takeIf { it.isNotBlank() }?.let { hint ->
                        TranslationAiDivider(palette)
                        TranslationAiSupportingText(
                            text = hint,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = metrics.appearanceRowVerticalPaddingDp.dp),
                        )
                    }
                }
                TranslationAiSection(
                    title = stringResource(R.string.reader_translation_ai_long_press_section),
                    palette = palette,
                ) {
                    TranslationAiSegmentedRow(
                        label = stringResource(R.string.reader_translation_ai_default_mode),
                        selected = settings.readerAiLongPressMode,
                        onSelected = {
                            onSettingsChange(settings.copy(readerAiLongPressMode = it))
                        },
                        palette = palette,
                    )
                }
                TranslationAiSection(
                    title = stringResource(R.string.reader_translation_ai_style_section),
                    palette = palette,
                ) {
                    TranslationAiSupportingText(
                        text = stringResource(R.string.reader_translation_ai_style_follow_text),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = metrics.appearanceRowVerticalPaddingDp.dp),
                    )
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
    selected: ReaderAiLongPressMode,
    onSelected: (ReaderAiLongPressMode) -> Unit,
    palette: TranslationAiPalette,
) {
    val metrics = readerSheetDensityMetrics()
    val options = listOf(
        stringResource(R.string.reader_translation_ai_mode_translation),
        stringResource(R.string.reader_translation_ai_mode_analysis),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                TranslationAiSegmentButton(
                    text = options[0],
                    selected = selected == ReaderAiLongPressMode.Translation,
                    palette = palette,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(ReaderAiLongPressMode.Translation) },
                )
                TranslationAiSegmentButton(
                    text = options[1],
                    selected = selected == ReaderAiLongPressMode.Analysis,
                    palette = palette,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(ReaderAiLongPressMode.Analysis) },
                )
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
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = if (selected) palette.segmentSelected else Color.Transparent,
        contentColor = if (selected) palette.segmentSelectedContent else palette.segmentUnselectedContent,
        tonalElevation = 0.dp,
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
        outlineVariant = colorScheme.outlineVariant,
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
        segmentBorder = segmentedControlColors.border,
    )
}

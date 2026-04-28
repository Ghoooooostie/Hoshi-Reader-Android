package moe.antimony.hoshi.features.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import de.manhhao.hoshi.LookupResult
import moe.antimony.hoshi.features.reader.ReaderSelectionData

data class LookupPopupState(
    val selection: ReaderSelectionData,
    val results: List<LookupResult>,
)

@Composable
fun LookupPopupView(
    state: LookupPopupState,
    onSwipeDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.results.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val frame = LookupPopupLayout(
            selectionRect = state.selection.rect,
            screenWidth = maxWidth.value.toDouble(),
            screenHeight = maxHeight.value.toDouble(),
            maxWidth = 320.0,
            maxHeight = 250.0,
            isVertical = true,
        ).calculate()
        Surface(
            modifier = Modifier
                .absoluteOffset(
                    x = (frame.centerX - frame.width / 2).dp,
                    y = (frame.centerY - frame.height / 2).dp,
                )
                .width(frame.width.dp)
                .height(frame.height.dp)
                .popupSwipeDismiss(onSwipeDismiss),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                state.results.take(3).forEach { result ->
                    Text(
                        text = result.term.expression,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (result.term.reading.isNotBlank() && result.term.reading != result.term.expression) {
                        Text(
                            text = result.term.reading,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    result.term.glossaries.take(4).forEach { glossary ->
                        Text(
                            text = glossary.glossary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Box(Modifier.height(10.dp))
                }
            }
        }
    }
}

private fun Modifier.popupSwipeDismiss(onSwipeDismiss: () -> Unit): Modifier =
    pointerInput(onSwipeDismiss) {
        var totalX = 0f
        var totalY = 0f
        detectHorizontalDragGestures(
            onDragStart = {
                totalX = 0f
                totalY = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
                totalX += dragAmount
                totalY += change.positionChange().y
            },
            onDragEnd = {
                if (kotlin.math.abs(totalX) > POPUP_SWIPE_DISMISS_THRESHOLD_PX &&
                    kotlin.math.abs(totalY) < POPUP_SWIPE_VERTICAL_SLOP_PX
                ) {
                    onSwipeDismiss()
                }
            },
        )
    }

private const val POPUP_SWIPE_DISMISS_THRESHOLD_PX = 80f
private const val POPUP_SWIPE_VERTICAL_SLOP_PX = 40f

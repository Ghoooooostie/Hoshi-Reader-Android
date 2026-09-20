package moe.antimony.hoshi.features.readaloud

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import moe.antimony.hoshi.R
import moe.antimony.hoshi.ui.asString

@Composable
fun ReadAloudPanel(
    state: ReadAloudState,
    speechRate: Float,
    onSkipPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipNext: () -> Unit,
    onStop: () -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSkipPrevious) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.read_aloud_previous_sentence),
                    )
                }
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.read_aloud_pause else R.string.read_aloud_play,
                        ),
                    )
                }
                IconButton(onClick = onSkipNext) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.read_aloud_next_sentence),
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = stringResource(R.string.read_aloud_stop),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentSentence.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${state.currentIndex + 1} / ${state.sentenceCount}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.read_aloud_speech_rate),
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = speechRate,
                    onValueChange = onSpeechRateChange,
                    valueRange = ReadAloudSettings.MinimumSpeechRate..ReadAloudSettings.MaximumSpeechRate,
                    steps = 5,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = String.format(Locale.getDefault(), "%.1fx", speechRate),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            val error = state.error
            if (error != null) {
                Text(
                    text = error.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

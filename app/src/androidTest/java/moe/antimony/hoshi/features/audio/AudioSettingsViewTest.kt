package moe.antimony.hoshi.features.audio

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioSettingsViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backgroundAudioSegmentsUseEqualSize() {
        composeRule.setContent {
            MaterialTheme {
                AudioSettingsView(onClose = {})
            }
        }

        val interruptBounds = composeRule.onNode(hasText("Interrupt") and isSelectable())
            .getUnclippedBoundsInRoot()
        val lowerVolumeBounds = composeRule.onNode(hasText("Lower Volume") and isSelectable())
            .getUnclippedBoundsInRoot()
        val keepVolumeBounds = composeRule.onNode(hasText("Keep Volume") and isSelectable())
            .getUnclippedBoundsInRoot()

        assertEquals(lowerVolumeBounds.width, interruptBounds.width, 0.5f)
        assertEquals(lowerVolumeBounds.width, keepVolumeBounds.width, 0.5f)
        assertEquals(lowerVolumeBounds.height, interruptBounds.height, 0.5f)
        assertEquals(lowerVolumeBounds.height, keepVolumeBounds.height, 0.5f)
    }

    private val androidx.compose.ui.unit.DpRect.width: Float
        get() = (right - left).value

    private val androidx.compose.ui.unit.DpRect.height: Float
        get() = (bottom - top).value
}

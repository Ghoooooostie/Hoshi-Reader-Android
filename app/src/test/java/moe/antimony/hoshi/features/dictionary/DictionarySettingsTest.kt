package moe.antimony.hoshi.features.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionarySettingsTest {
    @Test
    fun defaultsMatchIosUserConfig() {
        val settings = DictionarySettings()

        assertFalse(settings.dictionaryTabDefault)
        assertEquals(16, settings.maxResults)
        assertEquals(16, settings.scanLength)
        assertFalse(settings.collapseDictionaries)
        assertTrue(settings.compactGlossaries)
        assertFalse(settings.showExpressionTags)
        assertFalse(settings.harmonicFrequency)
        assertFalse(settings.deduplicatePitchAccents)
        assertEquals("", settings.customCSS)
    }

    @Test
    fun lookupSettingsAreClampedToIosStepperRanges() {
        val settings = DictionarySettings(maxResults = 200, scanLength = 0).normalized()

        assertEquals(50, settings.maxResults)
        assertEquals(1, settings.scanLength)
    }
}

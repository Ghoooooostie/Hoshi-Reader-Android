package moe.antimony.hoshi.features.dictionary

import moe.antimony.hoshi.features.advancedai.AdvancedAiClient
import moe.antimony.hoshi.features.advancedai.AdvancedAiSettingsRepository
import moe.antimony.hoshi.features.audio.LocalAudioRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessTextLookupDependenciesTest {
    @Test
    fun dependenciesInjectLocalAudioRepository() {
        val constructor = ProcessTextLookupDependencies::class.java.declaredConstructors.single()

        assertTrue(
            "Process Text lookup should use the Hilt-provided LocalAudioRepository.",
            constructor.parameterTypes.contains(LocalAudioRepository::class.java),
        )
        assertTrue(
            "Process Text lookup should inject AdvancedAiSettingsRepository for sentence analysis.",
            constructor.parameterTypes.contains(AdvancedAiSettingsRepository::class.java),
        )
        assertTrue(
            "Process Text lookup should inject AdvancedAiClient for sentence analysis.",
            constructor.parameterTypes.contains(AdvancedAiClient::class.java),
        )
    }
}

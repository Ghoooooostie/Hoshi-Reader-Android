package moe.antimony.hoshi.features.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCloudOAuthConfigurationTest {
    @Test
    fun configurationExplainsDeviceCodeProjectSetup() {
        val configuration = GoogleCloudOAuthConfiguration
        val steps = configuration.instructions

        assertEquals(
            "https://github.com/ttu-ttu/ebook-reader?tab=readme-ov-file#storage-sources",
            configuration.ttuSetupUrl,
        )
        assertTrue(configuration.introduction.contains("Device Code flow"))
        assertTrue(configuration.introduction.contains("same user-owned Google Cloud project"))
        assertEquals(5, steps.size)
        assertTrue(steps.any { it.contains("same Google Cloud project") && it.contains("Google Drive API") })
        assertTrue(steps.any { it.contains("TVs and Limited Input devices") })
        assertTrue(steps.any { it.contains("client ID") && it.contains("client secret") })
        assertTrue(steps.any { it.contains("Do not create an Android OAuth client") })
        assertTrue(steps.any { it.contains("Connect Google Drive") })
    }
}

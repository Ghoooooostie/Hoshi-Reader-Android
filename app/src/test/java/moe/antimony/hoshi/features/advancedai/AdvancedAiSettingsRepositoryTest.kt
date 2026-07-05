package moe.antimony.hoshi.features.advancedai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdvancedAiSettingsRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun emitsDisabledDefaultsWithSeparatePromptFields() = runBlocking {
        repository().use { handle ->
            val settings = handle.repository.settings.first()

            assertTrue(!settings.enabled)
            assertEquals("word-default", settings.wordPrompt)
            assertEquals("translation-default", settings.sentenceTranslationPrompt)
            assertEquals("sentence-default", settings.sentencePrompt)
            assertTrue(settings.wordAvailability() is AdvancedAiAvailability.Disabled)
            assertTrue(settings.sentenceTranslationAvailability() is AdvancedAiAvailability.Disabled)
            assertTrue(settings.sentenceAvailability() is AdvancedAiAvailability.Disabled)
        }
    }

    @Test
    fun persistsApiFieldsAndReportsReadyAvailability() = runBlocking {
        repository().use { handle ->
            handle.repository.update {
                it.copy(
                    enabled = true,
                    baseUrl = "https://example.invalid/v1/",
                    apiKey = "sk-test",
                    model = "gpt-test",
                )
            }

            val saved = handle.repository.settings.first()
            val wordAvailability = saved.wordAvailability()
            val sentenceTranslationAvailability = saved.sentenceTranslationAvailability()
            val sentenceAvailability = saved.sentenceAvailability()

            assertEquals("https://example.invalid/v1/", saved.baseUrl)
            assertTrue(wordAvailability is AdvancedAiAvailability.Ready)
            assertTrue(sentenceTranslationAvailability is AdvancedAiAvailability.Ready)
            assertTrue(sentenceAvailability is AdvancedAiAvailability.Ready)
            assertEquals("https://example.invalid/v1", (wordAvailability as AdvancedAiAvailability.Ready).settings.baseUrl)
        }
    }

    @Test
    fun missingWordPromptReportsPromptSpecificAvailability() = runBlocking {
        repository().use { handle ->
            handle.repository.update {
                it.copy(
                    enabled = true,
                    baseUrl = "https://example.invalid/v1",
                    apiKey = "sk-test",
                    model = "gpt-test",
                    wordPrompt = "",
                )
            }

            val availability = handle.repository.settings.first().wordAvailability()

            assertTrue(availability is AdvancedAiAvailability.MissingConfiguration)
            assertEquals(
                AdvancedAiMissingField.WordPrompt,
                (availability as AdvancedAiAvailability.MissingConfiguration).field,
            )
        }
    }

    private fun repository(): RepositoryHandle {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tempFolder.root, "advanced-ai.preferences_pb") },
        )
        return RepositoryHandle(
            repository = AdvancedAiSettingsRepository(
                dataStore = dataStore,
                defaultWordPrompt = "word-default",
                defaultSentenceTranslationPrompt = "translation-default",
                defaultSentencePrompt = "sentence-default",
            ),
            scope = scope,
        )
    }

    private class RepositoryHandle(
        val repository: AdvancedAiSettingsRepository,
        private val scope: CoroutineScope,
    ) : AutoCloseable {
        override fun close() {
            scope.cancel()
        }
    }
}

package moe.antimony.hoshi.features.anki

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import moe.antimony.hoshi.R
import moe.antimony.hoshi.dictionary.DictionaryRepository
import moe.antimony.hoshi.dictionary.DictionaryType
import moe.antimony.hoshi.features.advancedai.AdvancedAiAvailability
import moe.antimony.hoshi.features.advancedai.AdvancedAiClient
import moe.antimony.hoshi.features.advancedai.AdvancedAiSettingsRepository
import moe.antimony.hoshi.features.advancedai.sentenceAvailability
import moe.antimony.hoshi.features.advancedai.sentenceTranslationAvailability
import moe.antimony.hoshi.features.advancedai.wordAvailability
import moe.antimony.hoshi.features.audio.LocalAudioFile
import moe.antimony.hoshi.features.audio.LocalAudioRepository
import moe.antimony.hoshi.features.audio.LocalAudioResolver
import moe.antimony.hoshi.features.reader.ReaderSelectionData
import moe.antimony.hoshi.features.reader.ReaderSelectionRect
import moe.antimony.hoshi.ui.UiText
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Simple 4-tuple for parallel media upload results */
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Singleton
internal class AnkiRepository(
    private val context: Context,
    private val backend: AnkiBackend,
    private val settingsRepository: AnkiSettingsRepository,
    private val localAudioRepository: LocalAudioRepository,
    private val ankiConnectBackendFactory: (String, String) -> AnkiBackend,
    private val loadDictionaryMedia: (DictionaryMedia) -> ByteArray?,
    private val advancedAiSettingsRepository: AdvancedAiSettingsRepository?,
    private val advancedAiClient: AdvancedAiClient?,
    private val loadTermDictionaries: () -> List<AnkiTermDictionary>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        backend: AnkiBackend,
        settingsRepository: AnkiSettingsRepository,
        localAudioRepository: LocalAudioRepository,
        dictionaryRepository: DictionaryRepository,
        advancedAiSettingsRepository: AdvancedAiSettingsRepository,
        advancedAiClient: AdvancedAiClient,
    ) : this(
        context = context,
        backend = backend,
        settingsRepository = settingsRepository,
        localAudioRepository = localAudioRepository,
        ankiConnectBackendFactory = { endpoint: String, apiKey: String ->
            AnkiConnectBackend(endpoint, apiKey = apiKey)
        },
        loadDictionaryMedia = { media -> dictionaryRepository.dictionaryMedia(media.dictionary, media.path) },
        advancedAiSettingsRepository = advancedAiSettingsRepository,
        advancedAiClient = advancedAiClient,
        loadTermDictionaries = {
            dictionaryRepository.loadDictionaries(DictionaryType.Term).map { dictionary ->
                AnkiTermDictionary(
                    name = dictionary.index.title,
                    category = dictionary.category,
                )
            }
        },
    )

    internal constructor(
        context: Context,
        backend: AnkiBackend,
        settingsRepository: AnkiSettingsRepository,
        localAudioRepository: LocalAudioRepository,
        ankiConnectBackendFactory: (String, String) -> AnkiBackend = { endpoint: String, apiKey: String ->
            AnkiConnectBackend(endpoint, apiKey = apiKey)
        },
        advancedAiSettingsRepository: AdvancedAiSettingsRepository? = null,
        advancedAiClient: AdvancedAiClient? = null,
        loadTermDictionaries: () -> List<AnkiTermDictionary> = { emptyList() },
    ) : this(
        context = context,
        backend = backend,
        settingsRepository = settingsRepository,
        localAudioRepository = localAudioRepository,
        ankiConnectBackendFactory = ankiConnectBackendFactory,
        loadDictionaryMedia = { null },
        advancedAiSettingsRepository = advancedAiSettingsRepository,
        advancedAiClient = advancedAiClient,
        loadTermDictionaries = loadTermDictionaries,
    )

    val settings: Flow<AnkiSettings> = settingsRepository.settings

    suspend fun updateSettings(transform: (AnkiSettings) -> AnkiSettings) {
        settingsRepository.update(transform)
    }

    fun isAnkiDroidAvailable(): Boolean = backend.isAvailable()

    suspend fun fetchConfiguration(): AnkiFetchResult = withContext(Dispatchers.IO) {
        val currentSettings = settings.first()
        val activeBackend = activeBackendOrError(currentSettings).getOrElse { error ->
            return@withContext AnkiFetchResult.Error(
                error.message?.let(UiText::Literal) ?: UiText.Resource(R.string.anki_fetch_configure_failed),
            )
        }
        val fetched = runCatching {
            if (!activeBackend.isAvailable()) {
                return@withContext AnkiFetchResult.Error(
                    message = if (currentSettings.backendKind == AnkiBackendKind.AnkiDroid) {
                        UiText.Resource(AnkiFetchFailure.ApiUnavailable.userMessageRes)
                    } else {
                        UiText.Resource(R.string.anki_fetch_connect_ankiconnect_failed)
                    },
                    failure = if (currentSettings.backendKind == AnkiBackendKind.AnkiDroid) {
                        AnkiFetchFailure.ApiUnavailable
                    } else {
                        null
                    },
                )
            }
            activeBackend.fetchDecks() to activeBackend.fetchNoteTypes()
        }.getOrElse { error ->
            if (error is AnkiFetchException) {
                logAnkiFetchFailure("Unable to fetch Anki configuration: ${error.failure}", error)
                return@withContext AnkiFetchResult.Error(
                    message = if (error.message != error.failure.userMessage) {
                        UiText.Literal(error.message)
                    } else {
                        UiText.Resource(error.failure.userMessageRes)
                    },
                    failure = error.failure,
                )
            }
            logAnkiFetchFailure("Unable to fetch Anki configuration.", error)
            return@withContext AnkiFetchResult.Error(
                if (currentSettings.backendKind == AnkiBackendKind.AnkiDroid) {
                    UiText.Resource(AnkiFetchFailure.ProviderFailure.userMessageRes)
                } else {
                    error.message?.let(UiText::Literal)
                        ?: UiText.Resource(R.string.anki_fetch_ankiconnect_provider_failure)
                },
            )
        }
        val (decks, noteTypes) = fetched
        if (decks.isEmpty()) {
            return@withContext AnkiFetchResult.Error(
                if (currentSettings.backendKind == AnkiBackendKind.AnkiDroid) {
                    UiText.Resource(R.string.anki_fetch_no_ankidroid_decks)
                } else {
                    UiText.Resource(R.string.anki_fetch_no_ankiconnect_decks)
                },
            )
        }
        if (noteTypes.isEmpty()) {
            return@withContext AnkiFetchResult.Error(
                if (currentSettings.backendKind == AnkiBackendKind.AnkiDroid) {
                    UiText.Resource(R.string.anki_fetch_no_ankidroid_note_types)
                } else {
                    UiText.Resource(R.string.anki_fetch_no_ankiconnect_note_types)
                },
            )
        }
        settingsRepository.update { current ->
            val selectedDeck = decks.firstOrNull { !it.name.equals("Default", ignoreCase = true) }
                ?: decks.first()
            val selectedNoteType = noteTypes.first()
            val formats = current.cardFormats
                .ifEmpty { listOf(defaultAnkiCardFormat(UUID.randomUUID().toString())) }
                .map { format ->
                    format.copy(
                        selectedDeckId = selectedDeck.id,
                        selectedDeckName = selectedDeck.name,
                        selectedNoteTypeId = selectedNoteType.id,
                        selectedNoteTypeName = selectedNoteType.name,
                        fieldMappings = AnkiFieldTemplates.defaultMappings(selectedNoteType),
                    )
                }
            current.copy(
                cardFormats = formats,
                selectedDeckId = selectedDeck.id,
                selectedDeckName = selectedDeck.name,
                selectedNoteTypeId = selectedNoteType.id,
                selectedNoteTypeName = selectedNoteType.name,
                availableDecks = decks,
                availableNoteTypes = noteTypes,
                fieldMappings = formats.first().fieldMappings,
            )
        }
        AnkiFetchResult.Success(decks, noteTypes)
    }

    suspend fun pingAnkiConnect(): AnkiConnectConnectionResult = withContext(Dispatchers.IO) {
        val currentSettings = settings.first()
        val endpoint = runCatching {
            AnkiConnectUrlValidator.requireValidEndpoint(currentSettings.ankiConnectUrl).toString()
        }.getOrElse { error ->
            return@withContext AnkiConnectConnectionResult.Error(
                error.message?.let(UiText::Literal) ?: UiText.Resource(R.string.anki_connect_invalid_url),
            )
        }
        if (ankiConnectBackendFactory(endpoint, currentSettings.ankiConnectApiKey).isAvailable()) {
            AnkiConnectConnectionResult.Connected
        } else {
            AnkiConnectConnectionResult.Error(UiText.Resource(R.string.anki_fetch_connect_ankiconnect_failed))
        }
    }

    suspend fun mineEntry(
        rawPayload: String,
        context: AnkiMiningContext,
        decks: List<AnkiDeck>,
        noteTypes: List<AnkiNoteType>,
        formatId: String? = null,
        resolveSasayakiAudioPath: (suspend () -> String?)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "mineEntry started for format=$formatId")
        
        val settings = settings.first()
        val termDictionaries = loadTermDictionaries()
        val format = settings.resolveCardFormat(formatId)
        if (format == null) {
            Log.w(TAG, "mineEntry failed: card format not found (formatId=$formatId)")
            return@withContext false
        }
        Log.d(TAG, "mineEntry: resolved format=${format.id}, deck=${format.selectedDeckId}, noteType=${format.selectedNoteTypeId}")
        
        val activeBackendResult = activeBackendOrError(settings)
        val activeBackend = activeBackendResult.getOrElse { error ->
            Log.w(TAG, "mineEntry failed: backend resolution error", error)
            return@withContext false
        }
        
        if (!activeBackend.isAvailable()) {
            Log.w(TAG, "mineEntry failed: backend is not available")
            return@withContext false
        }
        Log.d(TAG, "mineEntry: backend available (${settings.backendKind})")
        
        val availableDecks = decks.ifEmpty { activeBackend.fetchDecks() }
        val availableNoteTypes = noteTypes.ifEmpty { activeBackend.fetchNoteTypes() }
        
        val deck = availableDecks.firstOrNull { it.id == format.selectedDeckId }
            ?: format.selectedDeckName?.let { name -> availableDecks.firstOrNull { it.name == name } }
        if (deck == null) {
            Log.w(TAG, "mineEntry failed: deck not found (id=${format.selectedDeckId}, name=${format.selectedDeckName})")
            return@withContext false
        }
        Log.d(TAG, "mineEntry: resolved deck=${deck.name}")
        
        val noteType = availableNoteTypes.firstOrNull { it.id == format.selectedNoteTypeId }
            ?: format.selectedNoteTypeName?.let { name -> availableNoteTypes.firstOrNull { it.name == name } }
        if (noteType == null) {
            Log.w(TAG, "mineEntry failed: noteType not found (id=${format.selectedNoteTypeId}, name=${format.selectedNoteTypeName})")
            return@withContext false
        }
        Log.d(TAG, "mineEntry: resolved noteType=${noteType.name}")
        
        val fieldMappings = format.fieldMappings.activeAnkiFieldMappings(noteType)
        val payload = runCatching { AnkiMiningPayload.fromJson(rawPayload) }.onFailure { error ->
            Log.w(TAG, "mineEntry failed: payload JSON parse error", error)
        }.getOrNull()
            ?: return@withContext false
        Log.d(TAG, "mineEntry: payload parsed, expression='${payload.expression.take(20)}...'")
        val needsCover = fieldMappings.referencesAnkiHandlebar("{book-cover}")
        val needsSasayakiAudio = fieldMappings.referencesAnkiHandlebar("{sasayaki-audio}")
        val needsAudio = fieldMappings.referencesAnkiHandlebar("{audio}")
        val needsSentenceCn = fieldMappings.referencesAnkiHandlebar("{sentence-cn}")
        val needsSentenceAnalyze = fieldMappings.referencesAnkiHandlebar("{sentence-analyze}")
        val needsWordAnalyze = fieldMappings.referencesAnkiHandlebar("{word-analyze}") ||
            fieldMappings.referencesAnkiHandlebar("{advanced-ai-word}")
        Log.d(TAG, "mineEntry: template needs - cover=$needsCover, sasayaki=$needsSasayakiAudio, audio=$needsAudio, sentenceCn=$needsSentenceCn, sentenceAnalyze=$needsSentenceAnalyze, wordAnalyze=$needsWordAnalyze")
        
        // The three AI lookups are independent, so run them together: mining should wait for the
        // slowest lookup instead of the sum of all of them. Each lookup degrades to null on failure.
        val aiStartTime = System.currentTimeMillis()
        val (sentenceCn, sentenceAnalyze, wordAnalyze) = coroutineScope {
            val sentenceCnRequest = async {
                if (needsSentenceCn) {
                    context.sentenceCn?.takeIf { it.isNotBlank() } ?: requestSentenceCn(context.sentence)
                } else {
                    context.sentenceCn
                }
            }
            val sentenceAnalyzeRequest = async {
                if (needsSentenceAnalyze) {
                    context.sentenceAnalyze?.takeIf { it.isNotBlank() } ?: requestSentenceAnalyze(context.sentence)
                } else {
                    context.sentenceAnalyze
                }
            }
            val wordAnalyzeRequest = async {
                if (needsWordAnalyze) {
                    context.wordAnalyze?.takeIf { it.isNotBlank() } ?: requestWordAnalyze(payload, context)
                } else {
                    context.wordAnalyze
                }
            }
            Triple(sentenceCnRequest.await(), sentenceAnalyzeRequest.await(), wordAnalyzeRequest.await())
        }
        Log.d(TAG, "mineEntry: AI lookups completed in ${System.currentTimeMillis() - aiStartTime}ms")
        
        // Media uploads - run cover/sasayaki in parallel, but audio/dictionary sequentially
        // to avoid file write conflicts in cache directory
        val mediaStartTime = System.currentTimeMillis()
        var httpCount = 0
        
        // Cover and sasayaki can run in parallel (different files)
        val (coverPath, sasayakiAudioPath) = coroutineScope {
            val coverRequest = async {
                context.coverPath?.takeIf { needsCover }?.let {
                    val start = System.currentTimeMillis()
                    val result = addHashedMediaFile(it, "hoshi_cover", activeBackend, settings.backendKind)
                    Log.d(TAG, "mineEntry: cover upload took ${System.currentTimeMillis() - start}ms, result=${result != null}")
                    if (result != null) httpCount++
                    result
                }
            }
            
            val sasayakiRequest = async {
                if (!needsSasayakiAudio) return@async null
                val sourcePath = context.sasayakiAudioPath?.takeIf { it.isNotBlank() }
                    ?: resolveSasayakiAudioPath?.invoke()?.takeIf { it.isNotBlank() }
                sourcePath?.let {
                    val start = System.currentTimeMillis()
                    val result = addHashedMediaFile(it, "hoshi_sasayaki", activeBackend, settings.backendKind)
                    Log.d(TAG, "mineEntry: sasayaki upload took ${System.currentTimeMillis() - start}ms, result=${result != null}")
                    if (result != null) httpCount++
                    result
                }
            }
            
            Pair(coverRequest.await(), sasayakiRequest.await())
        }
        
        // Audio upload (sequential to avoid cache file conflicts)
        val audioResult = payload.audio.takeIf { needsAudio && it.isNotBlank() }
            ?.let { 
                val start = System.currentTimeMillis()
                val result = addRemoteAudio(it, activeBackend, settings.backendKind)
                Log.d(TAG, "mineEntry: remote audio upload took ${System.currentTimeMillis() - start}ms, result=${result != null}")
                if (result != null) httpCount++
                result
            }
        
        // Dictionary media uploads (sequential to avoid cache file conflicts)
        val dictionaryMediaTags = payload.dictionaryMedia.associate { media ->
            val start = System.currentTimeMillis()
            val result = addDictionaryMedia(media, activeBackend, settings.backendKind).orEmpty()
            Log.d(TAG, "mineEntry: dictionary media '${media.filename}' upload took ${System.currentTimeMillis() - start}ms, result=${result.isNotBlank()}")
            if (result.isNotBlank()) httpCount++
            media.filename to result
        }.filterValues { it.isNotBlank() }
        
        Log.d(TAG, "mineEntry: all media uploads completed in ${System.currentTimeMillis() - mediaStartTime}ms, HTTP requests=$httpCount")
        
        val mediaContext = AnkiMiningContext(
            sentence = context.sentence,
            documentTitle = context.documentTitle,
            coverPath = coverPath,
            sasayakiAudioPath = sasayakiAudioPath,
            sentenceOffset = context.sentenceOffset,
            sentenceCn = sentenceCn,
            sentenceAnalyze = sentenceAnalyze,
            wordAnalyze = wordAnalyze,
        )
        
        val mediaPayload = payload.copy(audio = audioResult.orEmpty())
        val fields = fieldMappings.mapValues { (_, template) ->
            dictionaryMediaTags.entries.fold(
                AnkiHandlebarRenderer.render(
                    template = template,
                    payload = mediaPayload,
                    context = mediaContext,
                    selectedGlossaryFallback = settings.selectedGlossaryFallback,
                    termDictionaries = termDictionaries,
                ),
            ) { value, (filename, tag) -> value.replace(filename, tag) }
                .let(::normalizeAnkiDictionaryHtml)
        }.filterValues { it.isNotBlank() }
        Log.d(TAG, "mineEntry: rendered ${fields.size} fields")

        val addNoteStart = System.currentTimeMillis()
        val added = activeBackend.addNote(
            deck = deck,
            noteType = noteType,
            fieldsByName = fields,
            tags = AnkiHandlebarRenderer.renderTags(
                template = format.tags,
                payload = mediaPayload,
                context = mediaContext,
                selectedGlossaryFallback = settings.selectedGlossaryFallback,
                termDictionaries = termDictionaries,
            ).split(Regex("\\s+")).filter { it.isNotBlank() }.toSet(),
            allowDupes = settings.allowDupes,
            duplicateScope = settings.duplicateScope,
            checkDuplicatesAcrossAllModels = settings.checkDuplicatesAcrossAllModels,
        )
        Log.d(TAG, "mineEntry: addNote took ${System.currentTimeMillis() - addNoteStart}ms, result=$added")
        
        if (added) {
            val syncStart = System.currentTimeMillis()
            when (settings.backendKind) {
                AnkiBackendKind.AnkiConnect -> if (settings.ankiConnectForceSync) {
                    activeBackend.sync()
                    Log.d(TAG, "mineEntry: force sync took ${System.currentTimeMillis() - syncStart}ms")
                }
                AnkiBackendKind.AnkiDroid -> if (settings.ankiDroidForceSync) {
                    activeBackend.sync()
                    Log.d(TAG, "mineEntry: force sync took ${System.currentTimeMillis() - syncStart}ms")
                }
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "mineEntry completed in ${totalTime}ms, success=$added, total HTTP requests=${httpCount + 2}") // +2 for isAvailable and addNote
        
        added
    }

    private suspend fun requestSentenceCn(sentence: String): String? {
        val settingsRepository = advancedAiSettingsRepository ?: return null
        val client = advancedAiClient ?: return null
        if (sentence.isBlank()) return null
        val ready = settingsRepository.settings.first().sentenceTranslationAvailability() as? AdvancedAiAvailability.Ready
            ?: return null
        return runCatching { client.translateSentence(ready.settings, sentence) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun requestSentenceAnalyze(sentence: String): String? {
        val settingsRepository = advancedAiSettingsRepository ?: return null
        val client = advancedAiClient ?: return null
        if (sentence.isBlank()) return null
        val ready = settingsRepository.settings.first().sentenceAvailability() as? AdvancedAiAvailability.Ready
            ?: return null
        return runCatching { client.analyzeSentence(ready.settings, sentence) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun requestWordAnalyze(
        payload: AnkiMiningPayload,
        context: AnkiMiningContext,
    ): String? {
        val settingsRepository = advancedAiSettingsRepository ?: return null
        val client = advancedAiClient ?: return null
        val sentence = context.sentence.takeIf { it.isNotBlank() } ?: return null
        val word = payload.matched.takeIf { it.isNotBlank() }
            ?: payload.expression.takeIf { it.isNotBlank() }
            ?: payload.popupSelectionText.takeIf { it.isNotBlank() }
            ?: return null
        val ready = settingsRepository.settings.first().wordAvailability() as? AdvancedAiAvailability.Ready
            ?: return null
        val selection = ReaderSelectionData(
            text = word,
            sentence = sentence,
            rect = ReaderSelectionRect(0.0, 0.0, 1.0, 1.0),
            normalizedOffset = context.sentenceOffset,
            sentenceOffset = context.sentenceOffset,
        )
        return runCatching { client.analyzeWordInSentence(ready.settings, selection) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun duplicateStates(
        valuesByHandlebar: Map<String, String>,
        decks: List<AnkiDeck>,
        noteTypes: List<AnkiNoteType>,
    ): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val settings = settings.first()
        val activeBackend = activeBackendOrError(settings).getOrElse {
            return@withContext emptyMap()
        }
        if (!activeBackend.isAvailable()) return@withContext emptyMap()
        val availableDecks = decks.ifEmpty { activeBackend.fetchDecks() }
        val availableNoteTypes = noteTypes.ifEmpty { activeBackend.fetchNoteTypes() }
        settings.effectiveCardFormats().associate { format ->
            val deck = availableDecks.firstOrNull { it.id == format.selectedDeckId }
                ?: format.selectedDeckName?.let { name -> availableDecks.firstOrNull { it.name == name } }
            val noteType = availableNoteTypes.firstOrNull { it.id == format.selectedNoteTypeId }
                ?: format.selectedNoteTypeName?.let { name -> availableNoteTypes.firstOrNull { it.name == name } }
            val firstFieldHandlebar = noteType?.fields?.firstOrNull()?.let(format.fieldMappings::get)
            val key = firstFieldHandlebar?.let(valuesByHandlebar::get).orEmpty()
            val duplicate = if (deck == null || noteType == null || key.isBlank()) {
                false
            } else {
                activeBackend.isDuplicate(
                    deck = deck,
                    noteType = noteType,
                    key = key,
                    duplicateScope = settings.duplicateScope,
                    checkDuplicatesAcrossAllModels = settings.checkDuplicatesAcrossAllModels,
                )
            }
            format.id to duplicate
        }
    }

    suspend fun showNotes(
        formatId: String,
        valuesByHandlebar: Map<String, String>,
        decks: List<AnkiDeck>,
        noteTypes: List<AnkiNoteType>,
    ): Boolean = withContext(Dispatchers.IO) {
        val settings = settings.first()
        val format = settings.resolveCardFormat(formatId) ?: return@withContext false
        val activeBackend = activeBackendOrError(settings).getOrElse { return@withContext false }
        if (!activeBackend.isAvailable()) return@withContext false
        val availableDecks = decks.ifEmpty { activeBackend.fetchDecks() }
        val availableNoteTypes = noteTypes.ifEmpty { activeBackend.fetchNoteTypes() }
        val deck = availableDecks.firstOrNull { it.id == format.selectedDeckId }
            ?: format.selectedDeckName?.let { name -> availableDecks.firstOrNull { it.name == name } }
            ?: return@withContext false
        val noteType = availableNoteTypes.firstOrNull { it.id == format.selectedNoteTypeId }
            ?: format.selectedNoteTypeName?.let { name -> availableNoteTypes.firstOrNull { it.name == name } }
            ?: return@withContext false
        val firstField = noteType.fields.firstOrNull() ?: return@withContext false
        val handlebar = format.fieldMappings[firstField] ?: return@withContext false
        val key = valuesByHandlebar[handlebar].orEmpty()
        if (key.isBlank()) return@withContext false
        activeBackend.openNotes(
            deck = deck,
            noteType = noteType,
            key = key,
            duplicateScope = settings.duplicateScope,
            checkDuplicatesAcrossAllModels = settings.checkDuplicatesAcrossAllModels,
        )
    }

    suspend fun isDuplicate(
        expression: String,
        decks: List<AnkiDeck>,
        noteTypes: List<AnkiNoteType>,
    ): Boolean = withContext(Dispatchers.IO) {
        val settings = settings.first()
        val activeBackend = activeBackendOrError(settings).getOrElse { return@withContext false }
        val availableNoteTypes = noteTypes.ifEmpty { activeBackend.fetchNoteTypes() }
        val availableDecks = decks.ifEmpty { activeBackend.fetchDecks() }
        val deck = availableDecks.firstOrNull { it.id == settings.selectedDeckId }
            ?: settings.selectedDeckName?.let { name -> availableDecks.firstOrNull { it.name == name } }
            ?: return@withContext false
        val noteTypeId = settings.selectedNoteTypeId
            ?: settings.selectedNoteTypeName?.let { name -> availableNoteTypes.firstOrNull { it.name == name }?.id }
            ?: return@withContext false
        val noteType = availableNoteTypes.firstOrNull { it.id == noteTypeId } ?: return@withContext false
        activeBackend.isDuplicate(
            deck = deck,
            noteType = noteType,
            key = expression,
            duplicateScope = settings.duplicateScope,
            checkDuplicatesAcrossAllModels = settings.checkDuplicatesAcrossAllModels,
        )
    }

    private fun addRemoteAudio(url: String, activeBackend: AnkiBackend, backendKind: AnkiBackendKind): String? =
        runCatching {
            val data = readAnkiAudioBytes(
                url = url,
                readLocalAudio = localAudioRepository::loadAudio,
                readRemoteAudio = ::readRemoteAudioBytes,
            )
                ?: return null
            val media = ankiAudioMediaFile(url, data)
            val file = mediaCacheFile(media.preferredName)
            file.writeBytes(data)
            addMediaFile(file.absolutePath, file.name, media.mimeType, activeBackend, backendKind)
        }.getOrNull()

    private fun addDictionaryMedia(media: DictionaryMedia, activeBackend: AnkiBackend, backendKind: AnkiBackendKind): String? =
        runCatching {
            val data = loadDictionaryMedia(media) ?: return null
            val file = mediaCacheFile("hoshi_dict_${sha1Hex(data)}.${media.path.substringAfterLast('.', "bin")}")
            file.writeBytes(data)
            addMediaFile(file.absolutePath, file.name, mimeTypeForPath(media.path), activeBackend, backendKind)
                ?.let(::ankiInlineMediaReference)
        }.onFailure { Log.w(TAG, "Failed to add dictionary media ${media.path}", it) }
            .getOrNull()

    private fun addHashedMediaFile(
        path: String,
        prefix: String,
        activeBackend: AnkiBackend,
        backendKind: AnkiBackendKind,
    ): String? {
        val file = File(path).takeIf { it.isFile } ?: return null
        val name = "${prefix}_${sha1Hex(file.readBytes())}.${file.extension}"
        return addMediaFile(path, name, mimeTypeForPath(path), activeBackend, backendKind)
    }

    private suspend fun addMediaFile(
        path: String,
        preferredName: String,
        mimeType: String,
        activeBackend: AnkiBackend,
        backendKind: AnkiBackendKind,
    ): String? {
        val file = File(path).takeIf { it.isFile } ?: return null
        
        // Retry up to 2 times for transient network failures
        var lastError: Throwable? = null
        for (attempt in 1..2) {
            val result = runCatching {
                if (backendKind == AnkiBackendKind.AnkiConnect) {
                    return@runCatching activeBackend.addMediaFromBytes(file.readBytes(), preferredName, mimeType)
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                context.grantUriPermission("com.ichi2.anki", uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                activeBackend.addMediaFromUri(uri.toString(), preferredName, mimeType)
            }
            
            when {
                result.isSuccess -> return result.getOrNull()
                attempt < 2 -> {
                    lastError = result.exceptionOrNull()
                    Log.w(TAG, "addMediaFile attempt $attempt failed for $preferredName, retrying...", lastError)
                    kotlinx.coroutines.delay(500L * attempt) // Exponential backoff: 500ms, then 1000ms
                }
                else -> {
                    lastError = result.exceptionOrNull()
                    Log.w(TAG, "Failed to add Anki media $preferredName after ${attempt} attempts", lastError)
                }
            }
        }
        return null
    }

    private fun mediaCacheFile(name: String): File {
        val dir = File(context.cacheDir, "anki-media").also { it.mkdirs() }
        return File(dir, name)
    }

    /**
     * `HttpURLConnection` has no timeout by default, so an unresponsive audio host would hang the
     * whole mine (and the popup) instead of failing it.
     */
    private fun readRemoteAudioBytes(url: String): ByteArray {
        val connection = URL(url).openConnection().apply {
            if (this is HttpURLConnection) {
                connectTimeout = RemoteAudioConnectTimeoutMillis
                readTimeout = RemoteAudioReadTimeoutMillis
            }
        }
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }

    private fun activeBackendOrError(settings: AnkiSettings): Result<AnkiBackend> =
        when (settings.backendKind) {
            AnkiBackendKind.AnkiDroid -> Result.success(backend)
            AnkiBackendKind.AnkiConnect -> runCatching {
                val endpoint = AnkiConnectUrlValidator.requireValidEndpoint(settings.ankiConnectUrl).toString()
                ankiConnectBackendFactory(endpoint, settings.ankiConnectApiKey)
            }
        }
}

internal fun readAnkiAudioBytes(
    url: String,
    readLocalAudio: (LocalAudioFile) -> ByteArray?,
    readRemoteAudio: (String) -> ByteArray?,
): ByteArray? {
    val localFile = LocalAudioResolver.parseAudioUrl(url)
    return if (localFile != null) {
        readLocalAudio(localFile)
    } else {
        readRemoteAudio(url)
    }
}

internal data class AnkiAudioMediaFile(
    val preferredName: String,
    val mimeType: String,
)

internal fun ankiAudioMediaFile(url: String, data: ByteArray): AnkiAudioMediaFile {
    val extension = ankiAudioExtension(url)
    val preferredName = "hoshi_audio_${sha1Hex(data)}.$extension"
    return AnkiAudioMediaFile(
        preferredName = preferredName,
        mimeType = mimeTypeForPath(preferredName),
    )
}

private fun ankiAudioExtension(url: String): String {
    LocalAudioResolver.parseAudioUrl(url)?.file?.let { localFile ->
        LocalAudioResolver.audioExtension(localFile)
            .takeIf(::isSupportedAnkiAudioExtension)
            ?.let { return it }
    }
    return runCatching { URL(url).path }
        .getOrDefault(url.substringBefore('?'))
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf(::isSupportedAnkiAudioExtension)
        ?: "mp3"
}

private fun isSupportedAnkiAudioExtension(extension: String): Boolean =
    extension in setOf("mp3", "opus", "ogg", "aac", "m4a", "wav")

private fun sha1Hex(data: ByteArray): String =
    MessageDigest.getInstance("SHA-1").digest(data).joinToString("") { "%02x".format(it) }

private const val TAG = "AnkiRepository"

private const val RemoteAudioConnectTimeoutMillis = 10_000
private const val RemoteAudioReadTimeoutMillis = 30_000

private fun logAnkiFetchFailure(message: String, error: Throwable) {
    runCatching { Log.w(TAG, message, error) }
}

internal fun selectDeckAfterFetch(
    decks: List<AnkiDeck>,
    current: AnkiSettings,
): AnkiDeck =
    decks.firstOrNull { it.id == current.selectedDeckId }
        ?: current.selectedDeckName?.let { name -> decks.firstOrNull { it.name == name } }
        ?: decks.firstOrNull { !it.name.equals("Default", ignoreCase = true) }
        ?: decks.first()

internal fun selectNoteTypeAfterFetch(
    noteTypes: List<AnkiNoteType>,
    current: AnkiSettings,
): AnkiNoteType =
    noteTypes.firstOrNull { it.id == current.selectedNoteTypeId }
        ?: current.selectedNoteTypeName?.let { name -> noteTypes.firstOrNull { it.name == name } }
        ?: noteTypes.firstOrNull { AnkiFieldTemplates.matches(it) }
        ?: noteTypes.first()

internal fun fieldMappingsAfterFetch(
    selectedNoteType: AnkiNoteType,
    current: AnkiSettings,
): Map<String, String> =
    AnkiFieldTemplates.applyDefaultsIfUnmapped(selectedNoteType, current.fieldMappings)

sealed interface AnkiFetchResult {
    data class Success(
        val decks: List<AnkiDeck>,
        val noteTypes: List<AnkiNoteType>,
    ) : AnkiFetchResult

    data class Error(
        val message: UiText,
        val failure: AnkiFetchFailure? = null,
    ) : AnkiFetchResult
}

sealed interface AnkiConnectConnectionResult {
    data object Connected : AnkiConnectConnectionResult
    data class Error(val message: UiText) : AnkiConnectConnectionResult
}

fun mimeTypeForPath(path: String): String =
    when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "opus" -> "audio/ogg"
        "aac" -> "audio/aac"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "heic" -> "image/heic"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

internal fun ankiInlineMediaReference(addMediaResult: String): String {
    val imageSrc = Regex("""<img\s+[^>]*src=["']([^"']+)["'][^>]*>""")
        .find(addMediaResult)
        ?.groupValues
        ?.getOrNull(1)
    if (!imageSrc.isNullOrBlank()) return imageSrc
    val soundFile = Regex("""\[sound:([^\]]+)]""")
        .find(addMediaResult)
        ?.groupValues
        ?.getOrNull(1)
    return soundFile ?: addMediaResult
}

internal fun normalizeAnkiDictionaryHtml(value: String): String {
    if (!value.contains("data-sc-img") || !value.contains("gloss-image")) return value
    return value + AnkiGaijiImageStyle
}

private const val AnkiGaijiImageStyle =
    """<style>.yomitan-glossary [data-sc-img][data-sc-class="gaiji"]{display:inline!important;white-space:nowrap!important;vertical-align:baseline!important}.yomitan-glossary [data-sc-img][data-sc-class="gaiji"] .gloss-image-link{display:inline-block!important;vertical-align:text-bottom!important;max-width:1.2em!important}.yomitan-glossary [data-sc-img][data-sc-class="gaiji"] .gloss-image-container{display:inline-block!important;width:1em!important;height:1em!important;max-width:1em!important;max-height:1em!important;vertical-align:text-bottom!important;font-size:1em!important}.yomitan-glossary [data-sc-img][data-sc-class="gaiji"] .gloss-image-sizer{display:none!important}.yomitan-glossary [data-sc-img][data-sc-class="gaiji"] .gloss-image{position:static!important;width:1em!important;height:1em!important;vertical-align:text-bottom!important}</style>"""

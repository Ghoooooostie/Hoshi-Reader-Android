package moe.antimony.hoshi.features.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.advancedai.AdvancedAiSettings
import moe.antimony.hoshi.ui.hoshiOutlinedTextFieldColors
import moe.antimony.hoshi.ui.hoshiSingleLineTextFieldLineLimits
import moe.antimony.hoshi.ui.rememberSyncedTextFieldState

private enum class AdvancedAiEditableField(
    val titleRes: Int,
    val placeholder: String = "",
    val isSecret: Boolean = false,
    val isMultiline: Boolean = false,
) {
    BaseUrl(
        titleRes = R.string.advanced_ai_base_url,
        placeholder = "https://api.openai.com/v1",
    ),
    ApiKey(
        titleRes = R.string.advanced_ai_api_key,
        placeholder = "sk-...",
        isSecret = true,
    ),
    Model(
        titleRes = R.string.advanced_ai_model,
        placeholder = "gpt-4.1-mini",
    ),
    WordPrompt(
        titleRes = R.string.advanced_ai_word_prompt,
        isMultiline = true,
    ),
    SentenceTranslationPrompt(
        titleRes = R.string.advanced_ai_sentence_translation_prompt,
        isMultiline = true,
    ),
    PageParagraphTranslationPrompt(
        titleRes = R.string.advanced_ai_page_paragraph_translation_prompt,
        isMultiline = true,
    ),
    SentencePrompt(
        titleRes = R.string.advanced_ai_sentence_prompt,
        isMultiline = true,
    ),
}

@Composable
fun AdvancedAiSettingsView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContainer = LocalHoshiUiDependencies.current
    val repository = appContainer.advancedAiSettingsRepository
    val client = appContainer.advancedAiClient
    val scope = rememberCoroutineScope()
    val settings = repository.settings.collectAsLoadedSettings() ?: return
    var editingField by remember { mutableStateOf<AdvancedAiEditableField?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var testResultResId by remember { mutableIntStateOf(0) }
    var isTesting by remember { mutableStateOf(false) }

    if (editingField != null) {
        AdvancedAiEditDialog(
            field = editingField!!,
            value = editingValue,
            onValueChange = { editingValue = it },
            onDismiss = { editingField = null },
            onConfirm = {
                val target = editingField ?: return@AdvancedAiEditDialog
                scope.launch {
                    repository.update { current ->
                        current.updateField(target, editingValue)
                    }
                }
                editingField = null
            },
        )
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.advanced_ai),
        onClose = onClose,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                GroupCard {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text(stringResource(R.string.advanced_ai_enable)) },
                        trailingContent = {
                            Switch(
                                checked = settings.enabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        repository.update { current -> current.copy(enabled = enabled) }
                                    }
                                },
                            )
                        },
                    )
                    GroupDivider()
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text(stringResource(R.string.advanced_ai_connection)) },
                        supportingContent = {
                            Text(
                                text = if (testResultResId == 0) {
                                    stringResource(R.string.advanced_ai_not_tested)
                                } else {
                                    stringResource(testResultResId)
                                },
                            )
                        },
                        trailingContent = {
                            TextButton(
                                enabled = !isTesting,
                                onClick = {
                                    scope.launch {
                                        isTesting = true
                                        testResultResId = client.testConnection(settings)
                                            .fold(
                                                onSuccess = { R.string.advanced_ai_test_success },
                                                onFailure = { R.string.advanced_ai_test_failed },
                                            )
                                        isTesting = false
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (isTesting) {
                                            R.string.advanced_ai_testing
                                        } else {
                                            R.string.advanced_ai_test_connection
                                        },
                                    ),
                                )
                            }
                        },
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.advanced_ai_section_api))
            }
            item {
                GroupCard {
                    AdvancedAiValueRow(
                        title = stringResource(R.string.advanced_ai_base_url),
                        value = settings.baseUrl.ifBlank { stringResource(R.string.none) },
                        onEdit = {
                            editingField = AdvancedAiEditableField.BaseUrl
                            editingValue = settings.baseUrl
                        },
                    )
                    GroupDivider()
                    AdvancedAiValueRow(
                        title = stringResource(R.string.advanced_ai_api_key),
                        value = if (settings.apiKey.isBlank()) {
                            stringResource(R.string.none)
                        } else {
                            stringResource(R.string.advanced_ai_api_key_configured)
                        },
                        onEdit = {
                            editingField = AdvancedAiEditableField.ApiKey
                            editingValue = settings.apiKey
                        },
                    )
                    GroupDivider()
                    AdvancedAiValueRow(
                        title = stringResource(R.string.advanced_ai_model),
                        value = settings.model.ifBlank { stringResource(R.string.none) },
                        onEdit = {
                            editingField = AdvancedAiEditableField.Model
                            editingValue = settings.model
                        },
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.advanced_ai_section_prompts))
            }
            item {
                GroupCard {
                    AdvancedAiValueRow(
                        title = stringResource(R.string.advanced_ai_word_prompt),
                        value = settings.wordPrompt,
                        onEdit = {
                            editingField = AdvancedAiEditableField.WordPrompt
                            editingValue = settings.wordPrompt
                        },
                    )
                    GroupDivider()
                    AdvancedAiValueRow(
                        title = stringResource(R.string.advanced_ai_sentence_translation_prompt),
                        value = settings.sentenceTranslationPrompt,
                        onEdit = {
                            editingField = AdvancedAiEditableField.SentenceTranslationPrompt
                            editingValue = settings.sentenceTranslationPrompt
                        },
                    )
                    GroupDivider()
                    AdvancedAiValueRow(
                        title = stringResource(R.string.advanced_ai_page_paragraph_translation_prompt),
                        value = settings.pageParagraphTranslationPrompt,
                        onEdit = {
                            editingField = AdvancedAiEditableField.PageParagraphTranslationPrompt
                            editingValue = settings.pageParagraphTranslationPrompt
                        },
                    )
                    GroupDivider()
                    AdvancedAiValueRow(
                        title = stringResource(R.string.advanced_ai_sentence_prompt),
                        value = settings.sentencePrompt,
                        onEdit = {
                            editingField = AdvancedAiEditableField.SentencePrompt
                            editingValue = settings.sentencePrompt
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedAiEditDialog(
    field: AdvancedAiEditableField,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val textState = rememberSyncedTextFieldState(
        value = value,
        onValueChange = onValueChange,
        scrollState = scrollState,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(field.titleRes)) },
        text = {
            if (field.isSecret) {
                OutlinedSecureTextField(
                    state = textState,
                    label = { Text(stringResource(field.titleRes)) },
                    textObfuscationMode = TextObfuscationMode.Hidden,
                    colors = hoshiOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    state = textState,
                    label = { Text(stringResource(field.titleRes)) },
                    placeholder = field.placeholder.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                    lineLimits = if (field.isMultiline) {
                        TextFieldLineLimits.MultiLine(maxHeightInLines = 8)
                    } else {
                        hoshiSingleLineTextFieldLineLimits()
                    },
                    scrollState = scrollState,
                    colors = hoshiOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AdvancedAiValueRow(
    title: String,
    value: String,
    onEdit: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.action_edit))
            }
        },
    )
}

/** 更新指定字段。 */
private fun AdvancedAiSettings.updateField(
    field: AdvancedAiEditableField,
    value: String,
): AdvancedAiSettings =
    when (field) {
        AdvancedAiEditableField.BaseUrl -> copy(baseUrl = value.trim())
        AdvancedAiEditableField.ApiKey -> copy(apiKey = value.trim())
        AdvancedAiEditableField.Model -> copy(model = value.trim())
        AdvancedAiEditableField.WordPrompt -> copy(wordPrompt = value.trim())
        AdvancedAiEditableField.SentenceTranslationPrompt -> copy(sentenceTranslationPrompt = value.trim())
        AdvancedAiEditableField.PageParagraphTranslationPrompt -> copy(pageParagraphTranslationPrompt = value.trim())
        AdvancedAiEditableField.SentencePrompt -> copy(sentencePrompt = value.trim())
    }

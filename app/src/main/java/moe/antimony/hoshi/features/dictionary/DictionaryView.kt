package moe.antimony.hoshi.features.dictionary

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.dictionary.DictionaryInfo
import moe.antimony.hoshi.dictionary.DictionaryRepository
import moe.antimony.hoshi.dictionary.DictionaryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DictionaryRepository(context.filesDir, context.cacheDir) }
    var selectedType by remember { mutableStateOf(DictionaryType.Term) }
    var importType by remember { mutableStateOf(DictionaryType.Term) }
    var importMenuExpanded by remember { mutableStateOf(false) }
    var dictionaries by remember { mutableStateOf<Map<DictionaryType, List<DictionaryInfo>>>(emptyMap()) }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun reload() {
        dictionaries = DictionaryType.entries.associateWith { repository.loadDictionaries(it) }
        runCatching { repository.rebuildLookupQuery() }
    }

    fun importDictionaries(uris: List<Uri>, type: DictionaryType) {
        scope.launch {
            isImporting = true
            errorMessage = null
            runCatching {
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        repository.importDictionary(context.contentResolver, uri, type)
                    }
                }
            }.onSuccess {
                reload()
            }.onFailure {
                errorMessage = it.localizedMessage ?: "Failed to import dictionary."
            }
            isImporting = false
        }
    }

    fun setDictionaryEnabled(dictionary: DictionaryInfo, enabled: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.setDictionaryEnabled(selectedType, dictionary.path.name, enabled)
            }
            reload()
        }
    }

    fun deleteDictionary(dictionary: DictionaryInfo) {
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteDictionary(selectedType, dictionary.path.name)
            }
            reload()
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        importDictionaries(uris, importType)
    }

    val currentDictionaries = dictionaries[selectedType].orEmpty()

    LaunchedEffect(Unit) {
        reload()
    }
    BackHandler(onBack = onClose)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Dictionaries") },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text("‹")
                    }
                },
                actions = {
                    Box {
                        TextButton(
                            onClick = { importMenuExpanded = true },
                            enabled = !isImporting,
                        ) {
                            Text("+")
                        }
                        DropdownMenu(
                            expanded = importMenuExpanded,
                            onDismissRequest = { importMenuExpanded = false },
                        ) {
                            DictionaryType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = {
                                        importMenuExpanded = false
                                        importType = type
                                        importer.launch(zipMimeTypes)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            DictionaryType.entries.forEachIndexed { index, type ->
                                SegmentedButton(
                                    selected = selectedType == type,
                                    onClick = { selectedType = type },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = DictionaryType.entries.size,
                                    ),
                                ) {
                                    Text(type.displayName)
                                }
                            }
                        }
                        Text(
                            text = "Yomitan term, frequency and pitch dictionaries (.zip) are supported",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                errorMessage?.let { item { Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) } }
                if (currentDictionaries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No ${selectedType.displayName} Dictionaries")
                        }
                    }
                } else {
                    items(
                        items = currentDictionaries,
                        key = { it.path.name },
                    ) { dictionary ->
                        DictionaryRow(
                            dictionary = dictionary,
                            onEnabledChange = { setDictionaryEnabled(dictionary, it) },
                            onDelete = { deleteDictionary(dictionary) },
                        )
                    }
                }
            }
            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

private val zipMimeTypes = arrayOf(
    "application/zip",
    "application/octet-stream",
    "application/x-zip-compressed",
)

private val DictionaryType.displayName: String
    get() = when (this) {
        DictionaryType.Term -> "Term"
        DictionaryType.Frequency -> "Frequency"
        DictionaryType.Pitch -> "Pitch"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictionaryRow(
    dictionary: DictionaryInfo,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(Color(0xFFB3261E))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Delete", color = Color.White)
            }
        },
    ) {
        ListItem(
            headlineContent = { Text(dictionary.index.title) },
            supportingContent = { Text(dictionary.index.revision) },
            trailingContent = {
                Switch(
                    checked = dictionary.isEnabled,
                    onCheckedChange = onEnabledChange,
                )
            },
        )
    }
}

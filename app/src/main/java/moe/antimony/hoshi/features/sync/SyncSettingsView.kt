package moe.antimony.hoshi.features.sync

import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.antimony.hoshi.LocalHoshiAppContainer
import moe.antimony.hoshi.features.settings.collectAsLoadedSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer = LocalHoshiAppContainer.current
    val repository = appContainer.syncSettingsRepository
    val authorizer = appContainer.deviceCodeDriveAuthorizer
    val scope = rememberCoroutineScope()
    val settings = repository.settings.collectAsLoadedSettings()
    var authStatus by remember { mutableStateOf<DriveAuthStatus?>(null) }
    var directionMenuExpanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var copyMessage by remember { mutableStateOf<String?>(null) }
    var isAuthorizing by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var devicePrompt by remember { mutableStateOf<DeviceCodePrompt?>(null) }
    var pollIntervalSeconds by remember { mutableStateOf(5L) }
    val screenState = SyncSettingsScreenState(settings = settings, authStatus = authStatus)
    val currentSettings = settings
    val currentAuthStatus = authStatus
    val connectionActions = currentAuthStatus?.let { syncConnectionActions(it, isAuthorizing) }

    fun save(next: SyncSettings) {
        scope.launch {
            repository.update { next }
        }
    }

    LaunchedEffect(authorizer) {
        authorizer.configuredClient()?.let { client ->
            clientId = client.clientId
            clientSecret = client.clientSecret
        }
        authStatus = authorizer.status()
    }

    LaunchedEffect(devicePrompt, isAuthorizing) {
        val prompt = devicePrompt ?: return@LaunchedEffect
        if (!isAuthorizing) return@LaunchedEffect
        val expiresAtMillis = System.currentTimeMillis() + prompt.expiresInSeconds * 1000L
        var nextIntervalSeconds = pollIntervalSeconds
        while (isAuthorizing && System.currentTimeMillis() < expiresAtMillis) {
            delay(nextIntervalSeconds * 1000L)
            val result = try {
                authorizer.pollAuthorization(prompt)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                DriveAuthorizationResult.Failed(error.message ?: "Google Drive authorization failed.")
            }
            when (result) {
                is DriveAuthorizationResult.Authorized -> {
                    isAuthorizing = false
                    devicePrompt = null
                    authStatus = DriveAuthStatus.Connected
                    message = null
                    break
                }
                DriveAuthorizationResult.Pending -> Unit
                DriveAuthorizationResult.SlowDown -> {
                    nextIntervalSeconds += DeviceCodeDriveAuthorizer.SlowDownIncrementSeconds
                    pollIntervalSeconds = nextIntervalSeconds
                }
                is DriveAuthorizationResult.Failed -> {
                    isAuthorizing = false
                    devicePrompt = null
                    authStatus = DriveAuthStatus.Failed(result.message)
                    message = result.message
                    break
                }
            }
        }
        if (isAuthorizing && devicePrompt == prompt) {
            isAuthorizing = false
            devicePrompt = null
            authStatus = DriveAuthStatus.NotConnected
            message = "Google Drive authorization code expired."
        }
    }

    fun connectGoogleDrive() {
        if (isAuthorizing) return
        isAuthorizing = true
        message = null
        copyMessage = null
        devicePrompt = null
        scope.launch {
            if (clientId.isBlank() || clientSecret.isBlank()) {
                isAuthorizing = false
                authStatus = DriveAuthStatus.MissingConfiguration
                message = DeviceCodeDriveAuthorizer.MissingConfigurationMessage
                return@launch
            }
            authorizer.saveClient(clientId, clientSecret)
            runCatching { authorizer.requestDeviceCode() }
                .onSuccess { prompt ->
                    pollIntervalSeconds = prompt.intervalSeconds
                    devicePrompt = prompt
                    authStatus = DriveAuthStatus.NotConnected
                    message = "Open ${prompt.verificationUrl} and enter ${prompt.userCode}."
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(prompt.verificationUrl)))
                    }
                }
                .onFailure { error ->
                    isAuthorizing = false
                    val text = error.message ?: "Google Drive authorization failed."
                    authStatus = DriveAuthStatus.Failed(text)
                    message = text
                }
        }
    }

    fun signOut() {
        scope.launch {
            authorizer.revokeAccess()
            appContainer.googleDriveClient.clearCache()
            authStatus = authorizer.status()
            message = null
            copyMessage = null
            devicePrompt = null
            isAuthorizing = false
        }
    }

    BackHandler(onBack = onClose)
    val colorScheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    scrolledContainerColor = colorScheme.background,
                ),
                title = { Text("Syncing", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                if (!screenState.isContentReady || currentSettings == null || currentAuthStatus == null) {
                    return@item
                }
                SettingsCard {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Enable") },
                        trailingContent = {
                            Switch(
                                checked = currentSettings.enabled,
                                onCheckedChange = { save(currentSettings.copy(enabled = it)) },
                            )
                        },
                    )
                    SettingsDivider()
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Direction") },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { directionMenuExpanded = true }) {
                                    Text(currentSettings.mode.rawValue)
                                }
                                DropdownMenu(
                                    expanded = directionMenuExpanded,
                                    onDismissRequest = { directionMenuExpanded = false },
                                ) {
                                    SyncMode.entries.forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.rawValue) },
                                            onClick = {
                                                directionMenuExpanded = false
                                                save(currentSettings.copy(mode = mode))
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                    SettingsDivider()
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Auto Sync") },
                        trailingContent = {
                            Switch(
                                checked = currentSettings.autoSyncEnabled,
                                onCheckedChange = { save(currentSettings.copy(autoSyncEnabled = it)) },
                            )
                        },
                    )
                }
            }
            item {
                if (!screenState.isContentReady || currentAuthStatus == null) {
                    return@item
                }
                SettingsCard {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Google Drive") },
                        supportingContent = { Text(currentAuthStatus.label()) },
                    )
                    SettingsDivider()
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("Device client ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = clientSecret,
                            onValueChange = { clientSecret = it },
                            label = { Text("Device client secret") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                devicePrompt?.let { prompt ->
                    SettingsCard {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Authorize Google Drive",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = prompt.verificationUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = prompt.userCode,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            OutlinedButton(
                                onClick = {
                                    context.copyTextToClipboard("Google device code", prompt.userCode)
                                    copyMessage = "Device code copied"
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text("Copy code")
                            }
                        }
                    }
                }
                copyMessage?.let { text ->
                    Text(
                        text = text,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
                message?.let { text ->
                    Text(
                        text = text,
                        color = if (currentAuthStatus is DriveAuthStatus.Failed) colorScheme.error else colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
            }
            item {
                if (!screenState.isContentReady || connectionActions == null) {
                    return@item
                }
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (connectionActions.showConnect) {
                        Button(
                            onClick = ::connectGoogleDrive,
                            enabled = connectionActions.connectEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Connect Google Drive")
                        }
                    }
                    if (connectionActions.showSignOut) {
                        OutlinedButton(
                            onClick = ::signOut,
                            enabled = connectionActions.signOutEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Sign out")
                        }
                    }
                    GoogleCloudOAuthSetupCard()
                }
            }
        }
    }
}

@Composable
private fun GoogleCloudOAuthSetupCard() {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    SettingsCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Device Code setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Text(
                text = GoogleCloudOAuthConfiguration.introduction,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(GoogleCloudOAuthConfiguration.ttuSetupUrl)),
                    )
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("TTU Google Cloud setup")
            }
            GoogleCloudOAuthConfiguration.instructions.forEachIndexed { index, instruction ->
                Text(
                    text = "${index + 1}. $instruction",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CopyValueButton(label: String, value: String, onCopied: () -> Unit) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            context.copyTextToClipboard(label, value)
            onCopied()
        },
    ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy $label")
    }
}

private fun DriveAuthStatus.label(): String =
    when (this) {
        DriveAuthStatus.Connected -> "Connected"
        DriveAuthStatus.NotConnected -> "Not connected"
        DriveAuthStatus.MissingConfiguration -> "OAuth client not configured"
        is DriveAuthStatus.Failed -> message
    }

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(content = { content() })
    }
}

private fun Context.copyTextToClipboard(label: String, value: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

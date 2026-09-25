package com.phonosassist.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phonosassist.R
import com.phonosassist.data.ChatSession
import com.phonosassist.data.export.ChatExporter
import com.phonosassist.data.settings.AssistantSettings
import com.phonosassist.domain.InputMode
import com.phonosassist.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isFinalizing by viewModel.isFinalizing.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsState()
    val showHistoryDrawer by viewModel.showHistoryDrawer.collectAsState()
    val llmHost by viewModel.llmHost.collectAsState()
    val llmPort by viewModel.llmPort.collectAsState()
    val llmModel by viewModel.llmModel.collectAsState()
    val maxTokens by viewModel.maxTokens.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()
    val preferOfflineStt by viewModel.preferOfflineStt.collectAsState()
    val useSherpaOnnx by viewModel.useSherpaOnnx.collectAsState()
    val sherpaOnnxAvailable by viewModel.sherpaOnnxAvailable.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val inputLanguage by viewModel.inputLanguage.collectAsState()
    val responseLanguage by viewModel.responseLanguage.collectAsState()
    val inputMode by viewModel.inputMode.collectAsState()
    val textDraft by viewModel.textDraft.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // ── Transcript export to a persisted SAF folder ──────────────────────
    val exportTreeUri by viewModel.exportTreeUri.collectAsState()
    val exportSavedMsg = stringResource(R.string.export_saved)
    val exportFailedMsg = stringResource(R.string.export_failed)
    val exportEmptyMsg = stringResource(R.string.export_nothing)
    val exportFolderSetMsg = stringResource(R.string.export_folder_set)

    // Pending export payload (json to file name) awaiting a folder choice.
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    suspend fun writeIntoTree(treeUri: Uri, fileName: String, json: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val parent = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
                val document = DocumentsContract.createDocument(
                    resolver,
                    parent,
                    "application/json",
                    fileName,
                ) ?: return@runCatching false
                resolver.openOutputStream(document, "wt")?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: return@runCatching false
                true
            }.getOrDefault(false)
        }

    // First export (or an explicit "change folder") asks for a directory once;
    // the URI is persisted so later exports land in the same folder silently.
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            pendingExport = null
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.setExportTreeUri(uri.toString())
        val pending = pendingExport
        pendingExport = null
        if (pending != null) {
            scope.launch {
                val ok = writeIntoTree(uri, pending.second, pending.first)
                toast(if (ok) exportSavedMsg else exportFailedMsg)
            }
        } else {
            toast(exportFolderSetMsg)
        }
    }

    fun deliverExport(fileName: String, json: String) {
        val tree = exportTreeUri
        if (tree.isBlank()) {
            pendingExport = json to fileName
            folderLauncher.launch(null)
        } else {
            scope.launch {
                val ok = writeIntoTree(Uri.parse(tree), fileName, json)
                toast(if (ok) exportSavedMsg else exportFailedMsg)
            }
        }
    }

    val onExportCurrent: () -> Unit = {
        scope.launch {
            val messages = viewModel.chatMessages.value
            val json = viewModel.currentConversationJson()
            if (json == null) {
                toast(exportEmptyMsg)
            } else {
                val title = messages.firstOrNull()?.content.orEmpty()
                val stamp = messages.lastOrNull()?.timestamp ?: System.currentTimeMillis()
                deliverExport(ChatExporter.suggestedFileName(title, stamp), json)
            }
        }
    }

    val onExportSession: (ChatSession) -> Unit = { session ->
        scope.launch {
            val json = viewModel.sessionExportJson(session)
            if (json == null) {
                toast(exportEmptyMsg)
            } else {
                deliverExport(ChatExporter.suggestedFileName(session.title, session.timestamp), json)
            }
        }
    }

    val onChooseExportFolder: () -> Unit = {
        pendingExport = null
        folderLauncher.launch(null)
    }

    // Notification permission (Android 13+) is best-effort: the foreground
    // service still runs if it's denied, it just won't show its notification.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* best effort */ }
    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // RECORD_AUDIO is a runtime permission: request it on first use, then record.
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            requestNotificationsIfNeeded()
            viewModel.toggleRecording()
        } else {
            viewModel.onMicPermissionDenied()
        }
    }
    val onRecordClick: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            requestNotificationsIfNeeded()
            viewModel.toggleRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val buttonColor by animateColorAsState(
        targetValue = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "buttonColor",
    )

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(showHistoryDrawer) {
        if (showHistoryDrawer) {
            scope.launch { drawerState.open() }
        } else if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                viewModel = viewModel,
                chatHistory = chatHistory,
                onExportSession = onExportSession,
                onChooseExportFolder = onChooseExportFolder,
            )
        },
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(R.string.menu),
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.toggleInputMode() }) {
                                if (inputMode == InputMode.VOICE) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = stringResource(R.string.switch_to_text),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = stringResource(R.string.switch_to_voice),
                                    )
                                }
                            }
                            IconButton(onClick = onExportCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.FileDownload,
                                    contentDescription = stringResource(R.string.export_chat),
                                )
                            }
                            IconButton(onClick = { viewModel.createNewChat() }) {
                                Icon(
                                    imageVector = Icons.Filled.ClearAll,
                                    contentDescription = stringResource(R.string.new_chat),
                                )
                            }
                            IconButton(onClick = { viewModel.toggleHistoryDrawer() }) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = stringResource(R.string.previous_chats),
                                )
                            }
                            IconButton(onClick = { viewModel.showSettings() }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.settings),
                                )
                            }
                        },
                    )
                    AssistantLanguageBar(
                        inputLanguage = inputLanguage,
                        responseLanguage = responseLanguage,
                        onInputLanguageChange = viewModel::setInputLanguage,
                        onResponseLanguageChange = viewModel::setResponseLanguage,
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Chat area - top half
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // Partial transcript display
                    if (partialTranscript.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Text(
                                text = partialTranscript,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ChatScreen(
                        messages = chatMessages,
                        isProcessing = isProcessing,
                        streamingText = streamingText,
                        modifier = Modifier.weight(1f),
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )

                // Bottom half: big voice record button, or the text prompt area
                // when text input mode is active (STT bypassed).
                if (inputMode == InputMode.VOICE) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // Tappable to start a capture, or to stop-and-send the active
                    // one. Only disabled while finalizing/thinking/speaking.
                    val interactive = isRecording || (!isFinalizing && !isProcessing && !isSpeaking)
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "pulseScale",
                    )
                    val iconTint = if (interactive) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }

                    FilledTonalButton(
                        onClick = onRecordClick,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isRecording) Modifier.scale(pulseScale) else Modifier,
                            ),
                        shape = RoundedCornerShape(32.dp),
                        enabled = interactive,
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = buttonColor,
                            disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        ),
                    ) {
                        if (isRecording) {
                            // Mic + send arrow: tapping stops and sends the audio.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = iconTint,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = stringResource(R.string.stop_recording),
                                    modifier = Modifier.size(56.dp),
                                    tint = iconTint,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.start_recording),
                                modifier = Modifier.size(80.dp),
                                tint = iconTint,
                            )
                        }
                    }

                    // Small floating rectangle while recording: cancels the
                    // capture and discards the audio entirely (nothing is sent).
                    if (isRecording) {
                        FilledTonalButton(
                            onClick = { viewModel.cancelRecording() },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 24.dp)
                                .width(96.dp)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = stringResource(R.string.cancel_recording),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                } else {
                    TextInputArea(
                        draft = textDraft,
                        onDraftChange = viewModel::onTextDraftChanged,
                        onSend = viewModel::sendTextMessage,
                        isBusy = isFinalizing || isProcessing,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            initialHost = llmHost,
            initialPort = llmPort,
            initialModel = llmModel,
            initialMaxTokens = maxTokens,
            initialTtsEnabled = ttsEnabled,
            initialPreferOfflineStt = preferOfflineStt,
            initialUseSherpaOnnx = useSherpaOnnx,
            sherpaOnnxAvailable = sherpaOnnxAvailable,
        )
    }
}

@Composable
fun TextInputArea(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    val canSend = draft.isNotBlank() && !isBusy
    Row(
        modifier = modifier.padding(12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.text_input_hint)) },
            enabled = !isBusy,
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(
                onSend = { if (canSend) onSend() },
            ),
        )
        FilledTonalIconButton(
            onClick = onSend,
            enabled = canSend,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.send_message),
            )
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    initialHost: String,
    initialPort: String,
    initialModel: String,
    initialMaxTokens: Int,
    initialTtsEnabled: Boolean,
    initialPreferOfflineStt: Boolean,
    initialUseSherpaOnnx: Boolean,
    sherpaOnnxAvailable: Boolean,
) {
    var host by remember { mutableStateOf(initialHost) }
    var port by remember { mutableStateOf(initialPort) }
    var model by remember { mutableStateOf(initialModel) }
    var maxTokens by remember { mutableStateOf(initialMaxTokens.toString()) }
    var ttsEnabled by remember { mutableStateOf(initialTtsEnabled) }
    var preferOfflineStt by remember { mutableStateOf(initialPreferOfflineStt) }
    var useSherpaOnnx by remember { mutableStateOf(initialUseSherpaOnnx) }
    val testingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestMessage by viewModel.connectionTestMessage.collectAsState()

    AlertDialog(
        onDismissRequest = { viewModel.hideSettings() },
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.llm_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(R.string.llm_port)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.llm_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { input -> maxTokens = input.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.max_tokens)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { viewModel.testConnection() },
                        enabled = !testingConnection,
                    ) {
                        Text(
                            if (testingConnection) {
                                stringResource(R.string.connection_testing)
                            } else {
                                stringResource(R.string.connection_test)
                            },
                        )
                    }
                    connectionTestMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tts_enabled),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    androidx.compose.material3.Switch(
                        checked = ttsEnabled,
                        onCheckedChange = { ttsEnabled = it },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.prefer_offline_label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    androidx.compose.material3.Switch(
                        checked = preferOfflineStt,
                        onCheckedChange = { preferOfflineStt = it },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.use_sherpa_label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (!sherpaOnnxAvailable) {
                            Text(
                                text = stringResource(R.string.model_files_missing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    androidx.compose.material3.Switch(
                        checked = useSherpaOnnx,
                        onCheckedChange = { useSherpaOnnx = it },
                        enabled = sherpaOnnxAvailable,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.updateSettings(host, port, model)
                viewModel.setMaxTokens(
                    maxTokens.toIntOrNull() ?: AssistantSettings.DEFAULT_MAX_TOKENS,
                )
                viewModel.setTtsEnabled(ttsEnabled)
                viewModel.setPreferOfflineStt(preferOfflineStt)
                viewModel.setUseSherpaOnnx(useSherpaOnnx)
                viewModel.hideSettings()
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.hideSettings() }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun DrawerContent(
    viewModel: MainViewModel,
    chatHistory: List<ChatSession>,
    onExportSession: (ChatSession) -> Unit,
    onChooseExportFolder: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.previous_chats),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onChooseExportFolder) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = stringResource(R.string.export_folder),
                    )
                }
                if (chatHistory.isNotEmpty()) {
                    IconButton(onClick = {
                        viewModel.clearAllHistory()
                        Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.clear_all),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (chatHistory.isEmpty()) {
            Text(
                text = stringResource(R.string.no_chats),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chatHistory.forEach { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                    )
                                    Text(
                                        text = formatTimestamp(session.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row {
                                    IconButton(onClick = { onExportSession(session) }) {
                                        Icon(
                                            imageVector = Icons.Filled.FileDownload,
                                            contentDescription = stringResource(R.string.export_chat),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    IconButton(onClick = {
                                        viewModel.loadHistorySession(session)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Restore,
                                            contentDescription = stringResource(R.string.load_chat),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    IconButton(onClick = {
                                        viewModel.deleteHistorySession(session.id)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_info),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.personal_assistant),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

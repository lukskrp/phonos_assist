package com.phonosassist.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phonosassist.BuildConfig
import com.phonosassist.R
import com.phonosassist.data.ChatDatabase
import com.phonosassist.data.ChatMessageEntry
import com.phonosassist.data.ChatRepository
import com.phonosassist.data.ChatSession
import com.phonosassist.data.export.ChatExporter
import com.phonosassist.data.settings.AssistantSettings
import com.phonosassist.data.settings.SettingsRepository
import com.phonosassist.data.settings.settingsDataStore
import com.phonosassist.domain.AssistantLanguages
import com.phonosassist.domain.InputMode
import com.phonosassist.domain.SentenceChunker
import com.phonosassist.domain.SystemPrompt
import com.phonosassist.domain.trimContextForModel
import com.phonosassist.service.ChatMessage
import com.phonosassist.service.ChatRequest
import com.phonosassist.service.LlmService
import com.phonosassist.service.SherpaOnnxService
import com.phonosassist.tts.PiperSpeechSynthesizer
import com.phonosassist.voice.AndroidTtsSynthesizer
import com.phonosassist.voice.RoutingSpeechSynthesizer
import com.phonosassist.voice.VoiceAssistantController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "PhonosAssist"

/** Rough character budget for the conversation sent to the model. */
private const val CONTEXT_CHAR_BUDGET = 12_000

/**
 * Orchestrates a conversation: sessions/messages (via [ChatRepository]),
 * settings (via [SettingsRepository]) and the voice pipeline (via
 * [VoiceAssistantController]). Voice I/O and persistence live in their own
 * classes so this stays focused on turn orchestration.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ChatDatabase.getInstance(application).chatSessionDao()
    private val repository = ChatRepository(dao)
    private val settingsRepository = SettingsRepository(application.settingsDataStore)
    private val legacyPrefs = application.getSharedPreferences("phonosassist_settings", Context.MODE_PRIVATE)

    private val voice = VoiceAssistantController(
        context = application,
        scope = viewModelScope,
        synthesizer = RoutingSpeechSynthesizer(
            piper = PiperSpeechSynthesizer(application),
            device = AndroidTtsSynthesizer(application),
            onWarn = { warn(it) },
        ),
    )

    private val persistMutex = Mutex()

    val settings: StateFlow<AssistantSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantSettings.Default)

    val llmHost: StateFlow<String> = settings.map { it.llmHost }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantSettings.DEFAULT_HOST)
    val llmPort: StateFlow<String> = settings.map { it.llmPort }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantSettings.DEFAULT_PORT)
    val llmModel: StateFlow<String> = settings.map { it.llmModel }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantSettings.DEFAULT_MODEL)
    val maxTokens: StateFlow<Int> = settings.map { it.maxTokens }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantSettings.DEFAULT_MAX_TOKENS)
    val ttsEnabled: StateFlow<Boolean> = settings.map { it.ttsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val preferOfflineStt: StateFlow<Boolean> = settings.map { it.preferOfflineStt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val useSherpaOnnx: StateFlow<Boolean> = settings.map { it.useWhisper }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val inputLanguage: StateFlow<String> = settings.map { it.inputLanguage }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "en")
    val responseLanguage: StateFlow<String> = settings.map { it.responseLanguage }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantLanguages.AUTO)

    /** Persisted folder (SAF tree URI) for transcript exports; empty until chosen. */
    val exportTreeUri: StateFlow<String> = settings.map { it.exportTreeUri }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Persisted input mode (voice STT vs typed text); defaults to voice. */
    val inputMode: StateFlow<InputMode> = settings.map { it.inputMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InputMode.VOICE)

    /**
     * Unsent text-prompt draft. Lives in the ViewModel (not the composable) so
     * it survives mode toggles and rotation. Cleared on any successful send
     * (text or voice); preserved otherwise.
     */
    private val _textDraft = MutableStateFlow("")
    val textDraft: StateFlow<String> = _textDraft.asStateFlow()

    val chatHistory: StateFlow<List<ChatSession>> = repository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)

    private val _chatMessages = MutableStateFlow<List<ChatMessageEntry>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageEntry>> = _chatMessages.asStateFlow()

    /** Assistant text streamed so far for the in-flight reply (null when idle). */
    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isFinalizing = MutableStateFlow(false)
    val isFinalizing: StateFlow<Boolean> = _isFinalizing.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _showHistoryDrawer = MutableStateFlow(false)
    val showHistoryDrawer: StateFlow<Boolean> = _showHistoryDrawer.asStateFlow()

    private val _sherpaOnnxAvailable = MutableStateFlow(false)
    val sherpaOnnxAvailable: StateFlow<Boolean> = _sherpaOnnxAvailable.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestMessage = MutableStateFlow<String?>(null)
    val connectionTestMessage: StateFlow<String?> = _connectionTestMessage.asStateFlow()

    init {
        voice.start()
        voice.onPartialTranscript = { _partialTranscript.value = it }
        voice.onFinalTranscript = { processTranscript(it) }
        voice.onListeningChanged = { _isRecording.value = it }
        voice.onFinalizingChanged = { _isFinalizing.value = it }
        voice.onSpeakingChanged = { _isSpeaking.value = it }
        voice.onError = { message ->
            _errorMessage.value = message
                ?: getApplication<Application>().getString(R.string.speech_error)
        }

        _sherpaOnnxAvailable.value = SherpaOnnxService.isNativeAvailable() &&
            SherpaOnnxService.checkModelFiles(getApplication())

        // Keep the voice pipeline in sync with settings.
        viewModelScope.launch {
            settings.collect { s ->
                // The controller gates Whisper on availability; the preference is
                // kept intact so it works again once the models are installed.
                voice.whisperAvailable = _sherpaOnnxAvailable.value
                voice.useWhisper = s.useWhisper
                voice.inputLanguageCode = s.inputLanguage
                voice.inputSttTag = AssistantLanguages.sttTag(s.inputLanguage)
                voice.responseLanguageTag = if (s.responseLanguage == AssistantLanguages.AUTO) {
                    null
                } else {
                    AssistantLanguages.ttsTag(s.responseLanguage)
                }
                voice.preferOffline = s.preferOfflineStt
                voice.ttsEnabled = s.ttsEnabled
            }
        }

        // Import values from the pre-DataStore preferences once.
        viewModelScope.launch { settingsRepository.migrateFromLegacy(legacyPrefs) }

        // Debug-only probe so a broken Whisper install is visible in logcat.
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Whisper models available: ${_sherpaOnnxAvailable.value}")
            if (_sherpaOnnxAvailable.value) {
                viewModelScope.launch {
                    val probe = SherpaOnnxService(getApplication())
                    val ok = withContext(Dispatchers.IO) { probe.initialize() }
                    Log.i(TAG, "Whisper init probe: $ok")
                    withContext(Dispatchers.IO) { probe.destroy() }
                }
            }
        }

        loadLastSession()
    }

    // ── Session persistence ──────────────────────────────────────────────

    private fun loadLastSession() {
        viewModelScope.launch {
            repository.sessions.firstOrNull().orEmpty().firstOrNull()?.let { loadSession(it) }
        }
    }

    private fun loadSession(session: ChatSession) {
        _currentSessionId.value = session.id
        _chatMessages.value = repository.messages(session)
    }

    private fun persistCurrentSession() {
        val snapshot = _chatMessages.value
        if (snapshot.isEmpty()) return
        viewModelScope.launch {
            persistMutex.withLock {
                val id = repository.saveSession(_currentSessionId.value, snapshot)
                if (_currentSessionId.value == null) _currentSessionId.value = id
            }
        }
    }

    fun createNewChat() {
        val oldId = _currentSessionId.value
        val snapshot = _chatMessages.value
        voice.cancel()
        _currentSessionId.value = null
        _chatMessages.value = emptyList()
        _partialTranscript.value = ""
        _textDraft.value = ""
        _streamingText.value = null
        _errorMessage.value = null
        if (snapshot.isNotEmpty()) {
            viewModelScope.launch {
                persistMutex.withLock { repository.saveSession(oldId, snapshot) }
            }
        }
    }

    fun loadHistorySession(session: ChatSession) {
        _showHistoryDrawer.value = false
        loadSession(session)
        _partialTranscript.value = ""
        _textDraft.value = ""
        _streamingText.value = null
    }

    fun deleteHistorySession(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
            if (_currentSessionId.value == id) {
                _currentSessionId.value = null
                _chatMessages.value = emptyList()
                _streamingText.value = null
                _textDraft.value = ""
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.deleteAll()
            _currentSessionId.value = null
            _chatMessages.value = emptyList()
            _streamingText.value = null
            _textDraft.value = ""
        }
    }

    // ── Transcript export ────────────────────────────────────────────────

    /**
     * `cobaltium.chat` JSON for the active conversation, or null when there is
     * nothing to export. Runs off the main thread; never throws.
     */
    suspend fun currentConversationJson(): String? = withContext(Dispatchers.IO) {
        val messages = _chatMessages.value
        if (messages.isEmpty()) return@withContext null
        runCatching {
            ChatExporter.buildExportJson(
                title = repository.titleFor(messages),
                createdAt = messages.first().timestamp,
                updatedAt = System.currentTimeMillis(),
                messages = messages,
            )
        }.getOrNull()
    }

    /** `cobaltium.chat` JSON for a persisted history session, or null. */
    suspend fun sessionExportJson(session: ChatSession): String? = withContext(Dispatchers.IO) {
        val messages = repository.messages(session)
        if (messages.isEmpty()) return@withContext null
        runCatching {
            ChatExporter.buildExportJson(
                title = session.title,
                createdAt = messages.first().timestamp,
                updatedAt = session.timestamp,
                messages = messages,
            )
        }.getOrNull()
    }

    /** Remembers the folder chosen for exports (SAF tree URI). */
    fun setExportTreeUri(uri: String) {
        viewModelScope.launch { settingsRepository.update { it.copy(exportTreeUri = uri) } }
    }

    // ── Recording / input mode ───────────────────────────────────────────

    fun toggleRecording() {
        // The voice pipeline is only reachable in VOICE mode; the text path
        // uses sendTextMessage() instead so STT is never started by mistake.
        if (settings.value.inputMode != InputMode.VOICE) return
        when {
            _isRecording.value -> voice.stopCapture()
            !_isProcessing.value && !_isSpeaking.value && !_isFinalizing.value -> {
                _partialTranscript.value = ""
                _errorMessage.value = null
                voice.beginCapture()
            }
        }
    }

    /** Switches between voice and text input, cancelling voice-side activity. */
    fun setInputMode(mode: InputMode) {
        if (settings.value.inputMode == mode) return
        // Auto-cancel + switch: drop any in-flight capture/audio so the new
        // mode starts clean. The in-flight LLM stream (if any) is left alone
        // so its reply is not lost; only new sends are gated by the new mode.
        if (_isRecording.value || _isFinalizing.value) {
            voice.cancel()
            _partialTranscript.value = ""
        }
        if (_isSpeaking.value) voice.stopSpeaking()
        viewModelScope.launch { settingsRepository.update { it.copy(inputMode = mode) } }
    }

    fun toggleInputMode() {
        setInputMode(
            if (settings.value.inputMode == InputMode.VOICE) InputMode.TEXT else InputMode.VOICE,
        )
    }

    /** Updates the cached (unsent) text draft; survives mode toggles. */
    fun onTextDraftChanged(text: String) {
        _textDraft.value = text
    }

    /**
     * Text-prompt pipeline: bypasses STT entirely and feeds the typed text
     * into the same LLM → TTS tail as voice transcripts.
     */
    fun sendTextMessage() {
        if (settings.value.inputMode != InputMode.TEXT) return
        if (_isProcessing.value || _isFinalizing.value) return
        val text = _textDraft.value.trim()
        if (text.isEmpty()) return
        _textDraft.value = ""
        _errorMessage.value = null
        submitUserText(text)
    }

    /** Cancels the active capture and discards the audio (no transcript sent). */
    fun cancelRecording() {
        voice.cancel()
        _partialTranscript.value = ""
    }

    /** Surfaces a TTS/voice warning to the user (non-fatal). */
    private fun warn(message: String) {
        _errorMessage.value = message
    }

    /** Called when the user denies the microphone permission. */
    fun onMicPermissionDenied() {
        _errorMessage.value = getApplication<Application>().getString(R.string.mic_permission_required)
    }

    // ── Conversation ─────────────────────────────────────────────────────

    private fun processTranscript(text: String) {
        // A sent voice transcript discards any cached (unsent) text draft.
        _textDraft.value = ""
        submitUserText(text)
    }

    /** Shared tail for both pipelines: persist user turn, then stream a reply. */
    private fun submitUserText(text: String) {
        _chatMessages.value = _chatMessages.value + ChatMessageEntry(
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis(),
        )
        persistCurrentSession()
        sendToLlm()
    }

    private fun sendToLlm() {
        if (_isProcessing.value) return
        val current = settings.value
        _isProcessing.value = true
        _errorMessage.value = null
        _streamingText.value = ""

        val baseUrl = "http://${current.llmHost}:${current.llmPort}"
        val history = trimContextForModel(_chatMessages.value, CONTEXT_CHAR_BUDGET)
            .map { ChatMessage(role = it.role, content = it.content) }
        val context = buildList {
            add(ChatMessage(role = "system", content = SystemPrompt.build(current.responseLanguage)))
            addAll(history)
        }

        viewModelScope.launch {
            val builder = StringBuilder()
            // Sentences already complete enough to speak while the rest streams.
            val speechPending = StringBuilder()
            try {
                LlmService.streamChat(
                    baseUrl = baseUrl,
                    request = ChatRequest(
                        model = current.llmModel,
                        messages = context,
                        max_tokens = current.maxTokens,
                    ),
                ).collect { token ->
                    builder.append(token)
                    _streamingText.value = builder.toString()
                    if (current.ttsEnabled) {
                        speechPending.append(token)
                        val (sentences, remainder) = SentenceChunker.split(speechPending.toString())
                        if (sentences.isNotEmpty()) {
                            voice.enqueueSpeech(sentences.joinToString(" "))
                            speechPending.clear()
                            speechPending.append(remainder)
                        }
                    }
                }

                val responseText = builder.toString().trim().ifBlank { "No response" }
                _streamingText.value = null
                _chatMessages.value = _chatMessages.value + ChatMessageEntry(
                    role = "assistant",
                    content = responseText,
                    timestamp = System.currentTimeMillis(),
                )
                persistCurrentSession()
                _isProcessing.value = false
                if (current.ttsEnabled) {
                    val tail = speechPending.toString().trim()
                    if (tail.isNotEmpty()) voice.enqueueSpeech(tail)
                    voice.finishSpeaking()
                } else {
                    voice.endSession()
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM request failed", e)
                _streamingText.value = null
                _isProcessing.value = false
                _errorMessage.value = getApplication<Application>().getString(
                    R.string.llm_connection_failed, e.message,
                )
                _chatMessages.value = _chatMessages.value + ChatMessageEntry(
                    role = "assistant",
                    content = "Error: ${e.message}",
                    timestamp = System.currentTimeMillis(),
                )
                persistCurrentSession()
                voice.endSession()
            }
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────

    fun showSettings() { _showSettingsDialog.value = true }

    fun hideSettings() { _showSettingsDialog.value = false }

    fun updateSettings(host: String, port: String, model: String) {
        viewModelScope.launch {
            settingsRepository.update {
                it.copy(llmHost = host.trim(), llmPort = port.trim(), llmModel = model.trim())
            }
        }
    }

    /** Sends a tiny prompt to the configured endpoint to validate connectivity. */
    fun testConnection() {
        if (_isTestingConnection.value) return
        _isTestingConnection.value = true
        _connectionTestMessage.value = null
        val current = settings.value
        val baseUrl = "http://${current.llmHost}:${current.llmPort}"
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withTimeoutOrNull(10_000) {
                runCatching {
                    LlmService.complete(
                        baseUrl = baseUrl,
                        request = ChatRequest(
                            model = current.llmModel,
                            messages = listOf(ChatMessage(role = "user", content = "ping")),
                            max_tokens = 8,
                        ),
                    )
                }
            }
            _isTestingConnection.value = false
            _connectionTestMessage.value = when {
                result == null -> app.getString(R.string.connection_timeout)
                result.isSuccess -> app.getString(R.string.connection_ok)
                else -> result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                    ?: app.getString(R.string.connection_failed)
            }
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(ttsEnabled = enabled) } }
        if (!enabled) voice.stopSpeaking()
    }

    fun setInputLanguage(code: String) {
        viewModelScope.launch { settingsRepository.update { it.copy(inputLanguage = code) } }
    }

    fun setResponseLanguage(code: String) {
        viewModelScope.launch { settingsRepository.update { it.copy(responseLanguage = code) } }
    }

    fun setMaxTokens(tokens: Int) {
        viewModelScope.launch {
            settingsRepository.update {
                it.copy(maxTokens = tokens.coerceIn(1, AssistantSettings.MAX_TOKENS_LIMIT))
            }
        }
    }

    fun setPreferOfflineStt(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(preferOfflineStt = enabled) } }
    }

    fun setUseSherpaOnnx(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(useWhisper = enabled) } }
    }

    fun dismissError() { _errorMessage.value = null }

    fun toggleHistoryDrawer() { _showHistoryDrawer.value = !_showHistoryDrawer.value }

    override fun onCleared() {
        super.onCleared()
        voice.shutdown()
    }
}

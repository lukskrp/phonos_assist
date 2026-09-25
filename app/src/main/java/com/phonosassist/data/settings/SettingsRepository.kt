package com.phonosassist.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phonosassist.domain.InputMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Flow-based persistence for [AssistantSettings] backed by DataStore. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AssistantSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map(::toSettings)

    suspend fun update(transform: (AssistantSettings) -> AssistantSettings) {
        dataStore.edit { prefs ->
            val next = transform(toSettings(prefs))
            prefs[Keys.HOST] = next.llmHost
            prefs[Keys.PORT] = next.llmPort
            prefs[Keys.MODEL] = next.llmModel
            prefs[Keys.MAX_TOKENS] = next.maxTokens
            prefs[Keys.TTS] = next.ttsEnabled
            prefs[Keys.PREFER_OFFLINE] = next.preferOfflineStt
            prefs[Keys.USE_WHISPER] = next.useWhisper
            prefs[Keys.INPUT_LANG] = next.inputLanguage
            prefs[Keys.RESPONSE_LANG] = next.responseLanguage
            prefs[Keys.EXPORT_TREE_URI] = next.exportTreeUri
            prefs[Keys.INPUT_MODE] = next.inputMode.name
        }
    }

    /** One-time import of the pre-DataStore SharedPreferences, if present. */
    suspend fun migrateFromLegacy(prefs: SharedPreferences) {
        val current = dataStore.data.first()
        if (current[Keys.HOST] != null) return
        if (!prefs.contains("llm_host")) return
        val legacyFinnish = prefs.getBoolean("finnish_stt_enabled", false)
        update {
            AssistantSettings(
                llmHost = prefs.getString("llm_host", it.llmHost) ?: it.llmHost,
                llmPort = prefs.getString("llm_port", it.llmPort) ?: it.llmPort,
                llmModel = prefs.getString("llm_model", it.llmModel) ?: it.llmModel,
                ttsEnabled = prefs.getBoolean("tts_enabled", it.ttsEnabled),
                preferOfflineStt = prefs.getBoolean("prefer_offline_stt", it.preferOfflineStt),
                useWhisper = prefs.getBoolean("use_sherpa_onnx", it.useWhisper),
                inputLanguage = if (legacyFinnish) "fi" else "en",
                responseLanguage = "auto",
            )
        }
    }

    private fun toSettings(prefs: Preferences) = AssistantSettings(
        llmHost = prefs[Keys.HOST] ?: AssistantSettings.DEFAULT_HOST,
        llmPort = prefs[Keys.PORT] ?: AssistantSettings.DEFAULT_PORT,
        llmModel = prefs[Keys.MODEL] ?: AssistantSettings.DEFAULT_MODEL,
        maxTokens = prefs[Keys.MAX_TOKENS] ?: AssistantSettings.DEFAULT_MAX_TOKENS,
        ttsEnabled = prefs[Keys.TTS] ?: true,
        preferOfflineStt = prefs[Keys.PREFER_OFFLINE] ?: true,
        useWhisper = prefs[Keys.USE_WHISPER] ?: false,
        inputLanguage = prefs[Keys.INPUT_LANG] ?: "en",
        responseLanguage = prefs[Keys.RESPONSE_LANG] ?: "auto",
        exportTreeUri = prefs[Keys.EXPORT_TREE_URI] ?: "",
        inputMode = prefs[Keys.INPUT_MODE]?.let { runCatching { InputMode.valueOf(it) }.getOrNull() }
            ?: InputMode.VOICE,
    )

    private object Keys {
        val HOST = stringPreferencesKey("llm_host")
        val PORT = stringPreferencesKey("llm_port")
        val MODEL = stringPreferencesKey("llm_model")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val TTS = booleanPreferencesKey("tts_enabled")
        val PREFER_OFFLINE = booleanPreferencesKey("prefer_offline_stt")
        val USE_WHISPER = booleanPreferencesKey("use_sherpa_onnx")
        val INPUT_LANG = stringPreferencesKey("input_language")
        val RESPONSE_LANG = stringPreferencesKey("response_language")
        val EXPORT_TREE_URI = stringPreferencesKey("export_tree_uri")
        val INPUT_MODE = stringPreferencesKey("input_mode")
    }
}

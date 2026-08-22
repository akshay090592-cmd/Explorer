package com.example.malaysiaitinerary.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_preferences")

enum class AiEngineMode(val displayName: String) {
    AUTO_ON_DEVICE("On-Device Gemma 4"),
    ON_DEVICE_GEMMA("On-Device Gemma 4"),
    GEMINI_CLOUD("Gemini Cloud API")
}

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light Theme"),
    DARK("Dark Theme")
}

enum class GemmaModelChoice(val displayName: String, val filename: String) {
    GEMMA_4_1B_INT4("Gemma 4 1B Ultra-Lite (Default - 4-bit INT4 - ~650 MB)", "gemma-4-1b-it-gpu-int4.bin"),
    GEMMA_4_2B_INT4("Gemma 4 2B Fast (4-bit INT4 - ~1.3 GB)", "gemma-4-2b-it-gpu-int4.bin"),
    GEMMA_4_E2B_TASK("Gemma 4 E2B IT (Hugging Face - ~1.9 GB)", "gemma-4-E2B-it-web.task"),
    CUSTOM_IMPORTED("Custom Imported Model (Any .task or .bin)", "custom_model.bin")
}

class AiPreferencesRepository(private val context: Context) {

    private companion object {
        val KEY_ENGINE_MODE = stringPreferencesKey("engine_mode")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_GEMMA_MODEL_CHOICE = stringPreferencesKey("gemma_model_choice")
        val KEY_GEMMA_MODEL_PATH = stringPreferencesKey("gemma_model_path")
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_SEARCH_GROUNDING = booleanPreferencesKey("search_grounding")
        val KEY_FIRST_TIME_USER = booleanPreferencesKey("first_time_user")
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        try {
            AppThemeMode.valueOf(modeStr)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    val engineMode: Flow<AiEngineMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_ENGINE_MODE] ?: AiEngineMode.AUTO_ON_DEVICE.name
        try {
            AiEngineMode.valueOf(modeStr)
        } catch (e: Exception) {
            AiEngineMode.AUTO_ON_DEVICE
        }
    }

    val gemmaModelChoice: Flow<GemmaModelChoice> = context.dataStore.data.map { prefs ->
        val choiceStr = prefs[KEY_GEMMA_MODEL_CHOICE] ?: GemmaModelChoice.GEMMA_4_1B_INT4.name
        try {
            GemmaModelChoice.valueOf(choiceStr)
        } catch (e: Exception) {
            GemmaModelChoice.GEMMA_4_1B_INT4
        }
    }

    val gemmaModelPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GEMMA_MODEL_PATH] ?: ""
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY] ?: ""
    }

    val isSearchGroundingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SEARCH_GROUNDING] ?: true
    }

    val isFirstTimeUser: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIRST_TIME_USER] ?: true
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setEngineMode(mode: AiEngineMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENGINE_MODE] = mode.name
        }
    }

    suspend fun setGemmaModelChoice(choice: GemmaModelChoice) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GEMMA_MODEL_CHOICE] = choice.name
        }
    }

    suspend fun setGemmaModelPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GEMMA_MODEL_PATH] = path
        }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GEMINI_API_KEY] = apiKey
        }
    }

    suspend fun setSearchGroundingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SEARCH_GROUNDING] = enabled
        }
    }

    suspend fun setFirstTimeUserCompleted(completed: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIRST_TIME_USER] = !completed
        }
    }
}

package com.example.malaysiaitinerary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.malaysiaitinerary.data.repository.AiEngineMode
import com.example.malaysiaitinerary.data.repository.AiPreferencesRepository
import com.example.malaysiaitinerary.data.repository.AppThemeMode
import com.example.malaysiaitinerary.data.repository.GemmaModelChoice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val aiPreferencesRepository: AiPreferencesRepository
) : ViewModel() {

    val themeMode: StateFlow<AppThemeMode> = aiPreferencesRepository.themeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeMode.SYSTEM
    )

    val engineMode: StateFlow<AiEngineMode> = aiPreferencesRepository.engineMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AiEngineMode.ON_DEVICE_GEMMA
    )

    val gemmaModelChoice: StateFlow<GemmaModelChoice> = aiPreferencesRepository.gemmaModelChoice.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), GemmaModelChoice.GEMMA_4_1B_INT4
    )

    val gemmaModelPath: StateFlow<String> = aiPreferencesRepository.gemmaModelPath.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val geminiApiKey: StateFlow<String> = aiPreferencesRepository.geminiApiKey.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val isSearchGroundingEnabled: StateFlow<Boolean> = aiPreferencesRepository.isSearchGroundingEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            aiPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setEngineMode(mode: AiEngineMode) {
        viewModelScope.launch {
            aiPreferencesRepository.setEngineMode(mode)
        }
    }

    fun setGemmaModelChoice(choice: GemmaModelChoice) {
        viewModelScope.launch {
            aiPreferencesRepository.setGemmaModelChoice(choice)
        }
    }

    fun setGemmaModelPath(path: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setGemmaModelPath(path)
        }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setGeminiApiKey(key)
        }
    }

    fun setSearchGroundingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setSearchGroundingEnabled(enabled)
        }
    }
}

class SettingsViewModelFactory(
    private val aiPreferencesRepository: AiPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(aiPreferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

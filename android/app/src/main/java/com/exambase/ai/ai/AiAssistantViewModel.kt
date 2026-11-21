package com.exambase.ai.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class AiAssistantViewModel(source: AiSource) : ViewModel() {
    private val engine = AiAssistantEngine(source)
    private var runJob: Job? = null
    val state: StateFlow<AiUiState> = engine.state

    init { load() }

    fun load() { viewModelScope.launch { engine.load() } }
    fun onTask(task: String) = engine.onTask(task)
    fun onLanguage(language: String) = engine.onLanguage(language)
    fun onInput(text: String) = engine.onInput(text)
    fun onOutput(text: String) = engine.onOutput(text)
    fun dismissError() = engine.dismissError()
    fun run() { if (runJob?.isActive != true) runJob = viewModelScope.launch { engine.run() } }
    fun cancelRun() { runJob?.cancel(); runJob = null }
    fun acceptConsent() { viewModelScope.launch { engine.acceptConsent() } }
    fun declineConsent() = engine.declineConsent()
    fun revokeConsent() { viewModelScope.launch { engine.revokeConsent() } }
}

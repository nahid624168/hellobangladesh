package com.exambase.ai.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class NoteAiSheetState(val open: Boolean = false, val target: AiTarget? = null)

/** AI sheet inside the note editor. Reuses [AiAssistantEngine] so consent, quota and failures behave as on the assistant screen. */
class NoteAiViewModel(source: AiSource) : ViewModel() {
    private val engine = AiAssistantEngine(source)
    val ai: StateFlow<AiUiState> = engine.state

    private val _sheet = MutableStateFlow(NoteAiSheetState())
    val sheet: StateFlow<NoteAiSheetState> = _sheet.asStateFlow()
    private var loaded = false
    private var runJob: Job? = null

    /** Opens the sheet for [target]. Consent and quota are read the first time only (and again after a failed read). */
    fun open(target: AiTarget) {
        engine.clearOutput()
        engine.onInput(target.text)
        _sheet.value = NoteAiSheetState(open = true, target = target)
        if (!loaded || (engine.state.value.consent == null && engine.state.value.quota == null)) {
            loaded = true
            viewModelScope.launch { engine.load() }
        }
    }

    fun close() { _sheet.update { it.copy(open = false) } }

    fun onTask(task: String) = engine.onTask(task)
    fun onLanguage(language: String) = engine.onLanguage(language)
    fun onOutput(text: String) = engine.onOutput(text)
    fun run() { if (runJob?.isActive != true) runJob = viewModelScope.launch { engine.run() } }
    fun cancelRun() { runJob?.cancel(); runJob = null }
    fun acceptConsent() { viewModelScope.launch { engine.acceptConsent() } }
    fun declineConsent() = engine.declineConsent()
    fun discard() { engine.clearOutput() }
}

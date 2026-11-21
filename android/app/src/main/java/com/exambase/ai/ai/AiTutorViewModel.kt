package com.exambase.ai.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.core.network.AiConversationDetailDto
import com.exambase.core.network.AiConversationItemDto
import com.exambase.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

 data class AiTutorUiState(
    val conversations: List<AiConversationItemDto> = emptyList(),
    val selected: AiConversationDetailDto? = null,
    val loadingHistory: Boolean = false,
    val error: String? = null,
 )

class AiTutorViewModel(private val source: AiSource) : ViewModel() {
    private val engine = AiAssistantEngine(source)
    val aiState = engine.state
    private val _state = MutableStateFlow(AiTutorUiState())
    val state: StateFlow<AiTutorUiState> = _state.asStateFlow()
    init { viewModelScope.launch { engine.load(); loadHistory() } }

    fun loadHistory() { viewModelScope.launch {
        _state.value = _state.value.copy(loadingHistory = true, error = null)
        when (val r = source.conversations()) {
            is ApiResult.Success -> _state.value = _state.value.copy(conversations = r.value.items, loadingHistory = false)
            is ApiResult.Failure -> _state.value = _state.value.copy(loadingHistory = false, error = r.failure.message)
        }
    } }

    fun open(id: String) { viewModelScope.launch {
        _state.value = _state.value.copy(loadingHistory = true, error = null)
        when (val r = source.conversation(id)) {
            is ApiResult.Success -> _state.value = _state.value.copy(selected = r.value, loadingHistory = false)
            is ApiResult.Failure -> _state.value = _state.value.copy(loadingHistory = false, error = r.failure.message)
        }
    } }

    fun onLanguage(language: String) = engine.onLanguage(language)
    fun newConversation() { _state.value = _state.value.copy(selected = null); engine.onInput(""); engine.clearOutput() }
    fun delete(id: String) { viewModelScope.launch {
        when (val r = source.deleteConversation(id)) {
            is ApiResult.Success -> { if (_state.value.selected?.id == id) _state.value = _state.value.copy(selected = null); loadHistory() }
            is ApiResult.Failure -> _state.value = _state.value.copy(error = r.failure.message)
        }
    } }

    fun send(text: String) { if (text.isBlank() || aiState.value.running) return
        viewModelScope.launch {
            engine.clearOutput()
            engine.onTask(AiTasks.CHAT); engine.onInput(text.trim()); engine.run(_state.value.selected?.id)
            afterTutorRun()
        }
    }
    fun hint(text: String) { if (text.isBlank() || aiState.value.running) return
        viewModelScope.launch { engine.clearOutput(); engine.onTask(AiTasks.TUTOR_HINT); engine.onInput(text.trim()); engine.run(_state.value.selected?.id); afterTutorRun() }
    }
    fun steps(text: String) { if (text.isBlank() || aiState.value.running) return
        viewModelScope.launch { engine.clearOutput(); engine.onTask(AiTasks.TUTOR_STEPS); engine.onInput(text.trim()); engine.run(_state.value.selected?.id); afterTutorRun() }
    }
    private suspend fun afterTutorRun() { val id = aiState.value.conversationId; if (id != null) open(id); loadHistory() }
    fun acceptConsent() { viewModelScope.launch { engine.acceptConsent(); afterTutorRun() } }
    fun declineConsent() = engine.declineConsent()
}

package com.exambase.ai.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.ai.flashcards.FlashcardSource
import com.exambase.core.network.ApiResult
import com.exambase.core.network.AiGeneratedQuestionDto
import com.exambase.core.network.AiGeneratedQuizCreateRequest
import com.exambase.core.network.AiGeneratedQuizSetDto
import com.exambase.core.network.AiGeneratedQuizDetailDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

 data class AiContentUiState(
    val title: String = "My AI study set",
    val input: String = "",
    val questions: List<GeneratedQuestionDraft> = emptyList(),
    val cards: List<GeneratedFlashcardDraft> = emptyList(),
    val savedQuiz: AiGeneratedQuizSetDto? = null,
    val savedSets: List<AiGeneratedQuizSetDto> = emptyList(),
    val selectedSaved: AiGeneratedQuizDetailDto? = null,
    val savedFlashcardTitle: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
 )

class AiContentViewModel(private val source: AiSource, private val flashcards: FlashcardSource, initialSource: String? = null) : ViewModel() {
    private val engine = AiAssistantEngine(source)
    val aiState = engine.state
    private val _state = MutableStateFlow(AiContentUiState())
    val state: StateFlow<AiContentUiState> = _state.asStateFlow()

    init {
        if (!initialSource.isNullOrBlank()) {
            _state.value = _state.value.copy(input = initialSource)
            engine.onInput(initialSource)
        }
        viewModelScope.launch { engine.load(); loadSavedQuizzes() }
    }
    fun input(v: String) { _state.value = _state.value.copy(input = v, error = null, message = null); engine.onInput(v) }
    fun title(v: String) { _state.value = _state.value.copy(title = v) }
    fun editQuestion(i: Int, q: GeneratedQuestionDraft) { _state.value = _state.value.copy(questions = _state.value.questions.toMutableList().also { it[i] = q }) }
    fun editCard(i: Int, c: GeneratedFlashcardDraft) { _state.value = _state.value.copy(cards = _state.value.cards.toMutableList().also { it[i] = c }) }
    fun removeQuestion(i: Int) { _state.value = _state.value.copy(questions = _state.value.questions.filterIndexed { n, _ -> n != i }) }
    fun removeCard(i: Int) { _state.value = _state.value.copy(cards = _state.value.cards.filterIndexed { n, _ -> n != i }) }

    fun generate(task: String) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            pendingTask = task
            engine.clearOutput()
            engine.onTask(task); engine.onInput(_state.value.input); engine.run()
            val a = aiState.value
            if (a.hasOutput) {
                parseOutput(task)
            }
            _state.value = _state.value.copy(busy = false)
        }
    }

    private var pendingTask: String? = null
    fun loadSavedQuizzes() { viewModelScope.launch {
        when (val r = source.generatedQuizSets()) {
            is ApiResult.Success -> _state.value = _state.value.copy(savedSets = r.value)
            is ApiResult.Failure -> _state.value = _state.value.copy(error = r.failure.message)
        }
    } }
    fun openSavedQuiz(id: String) { viewModelScope.launch {
        when (val r = source.generatedQuiz(id)) {
            is ApiResult.Success -> _state.value = _state.value.copy(selectedSaved = r.value)
            is ApiResult.Failure -> _state.value = _state.value.copy(error = r.failure.message)
        }
    } }
    fun closeSavedQuiz() { _state.value = _state.value.copy(selectedSaved = null) }
    fun deleteSavedQuiz(id: String) { viewModelScope.launch {
        when (val r = source.deleteGeneratedQuiz(id)) {
            is ApiResult.Success -> { if (_state.value.selectedSaved?.quizSet?.id == id) _state.value = _state.value.copy(selectedSaved = null); loadSavedQuizzes() }
            is ApiResult.Failure -> _state.value = _state.value.copy(error = r.failure.message)
        }
    } }
    fun acceptConsent() { viewModelScope.launch {
        engine.acceptConsent()
        val task = pendingTask
        if (task != null && aiState.value.hasOutput) parseOutput(task)
    } }
    private fun parseOutput(task: String) {
        val a = aiState.value
        val qs = if (task == AiTasks.MAKE_QUESTIONS) AiGeneratedContent.questions(a.output) else emptyList()
        val cs = if (task == AiTasks.MAKE_FLASHCARDS) AiGeneratedContent.flashcards(a.output) else emptyList()
        _state.value = _state.value.copy(questions = qs, cards = cs, message = if (qs.isEmpty() && cs.isEmpty()) "No valid items were generated. Add more source text and try again." else "Review and edit every item before saving.")
    }
    fun declineConsent() = engine.declineConsent()
    fun cancel() { _state.value = _state.value.copy(busy = false) }

    fun saveQuiz() {
        val s = _state.value
        if (s.title.isBlank() || s.questions.isEmpty() || s.busy) return
        viewModelScope.launch {
            _state.value = s.copy(busy = true, error = null, message = null)
            val req = AiGeneratedQuizCreateRequest(s.title.trim(), null, s.questions.map { AiGeneratedQuestionDto(it.question.trim(), it.options.map(String::trim), it.correctOptionIndex, it.explanation.trim()) })
            when (val r = source.createGeneratedQuiz(req)) {
                is ApiResult.Success -> { _state.value = _state.value.copy(busy = false, savedQuiz = r.value.quizSet, message = "Saved to My AI Quiz Sets. It is private to your account."); loadSavedQuizzes() }
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = r.failure.message)
            }
        }
    }

    fun saveFlashcards() {
        val s = _state.value
        if (s.title.isBlank() || s.cards.isEmpty() || s.busy) return
        viewModelScope.launch {
            _state.value = s.copy(busy = true, error = null, message = null)
            when (val r = AiGeneratedFlashcardSaver(flashcards).save(s.title, "AI-generated and reviewed flashcards", s.cards)) {
                is ApiResult.Success -> _state.value = _state.value.copy(busy = false, savedFlashcardTitle = r.value.title, message = "Saved to My Flashcard Sets.")
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = r.failure.message)
            }
        }
    }
}

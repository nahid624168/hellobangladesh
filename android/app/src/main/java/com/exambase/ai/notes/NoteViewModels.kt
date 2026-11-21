package com.exambase.ai.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.ai.academic.PagedList
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import com.exambase.core.network.Note
import com.exambase.core.network.NoteFolder
import com.exambase.core.network.NoteSummary
import com.exambase.core.network.NoteVersion
import com.exambase.core.network.UpdateNoteRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesListUiState(
    val folders: List<NoteFolder> = emptyList(),
    val folderId: String? = null,
    val query: String = "",
    val pinnedOnly: Boolean = false,
    val favoriteOnly: Boolean = false,
    val list: PagedList<NoteSummary> = PagedList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val signedOut: Boolean = false,
)

class NotesListViewModel(private val source: NoteSource) : ViewModel() {
    private val _state = MutableStateFlow(NotesListUiState())
    val state: StateFlow<NotesListUiState> = _state.asStateFlow()
    private var job: Job? = null

    init { reload() }

    fun onQuery(q: String) = _state.update { it.copy(query = q) }
    fun submitSearch() = reload()
    fun selectFolder(id: String?) { _state.update { it.copy(folderId = id) }; reload() }
    fun togglePinned() { _state.update { it.copy(pinnedOnly = !it.pinnedOnly) }; reload() }
    fun toggleFavorite() { _state.update { it.copy(favoriteOnly = !it.favoriteOnly) }; reload() }

    fun reload() {
        job?.cancel()
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val folders = source.folders()
            val notes = source.notes(
                folderId = s.folderId,
                q = s.query.ifBlank { null },
                pinned = s.pinnedOnly.takeIf { it },
                favorite = s.favoriteOnly.takeIf { it },
            )
            if (folders is ApiResult.Failure && folders.failure.kind == FailureKind.UNAUTHORIZED) {
                _state.update { it.copy(loading = false, signedOut = true) }
                return@launch
            }
            when (notes) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        folders = (folders as? ApiResult.Success)?.value ?: it.folders,
                        list = PagedList.first(notes.value),
                        loading = false,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        signedOut = notes.failure.kind == FailureKind.UNAUTHORIZED,
                        error = notes.failure.message ?: "Unable to load notes.",
                    )
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.list.nextCursor ?: return
        if (s.loadingMore || s.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val r = source.notes(s.folderId, s.query.ifBlank { null }, s.pinnedOnly.takeIf { it }, s.favoriteOnly.takeIf { it }, cursor)) {
                is ApiResult.Success -> _state.update { it.copy(list = it.list.append(r.value) { n -> n.id }, loadingMore = false) }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            when (source.createFolder(name.trim())) {
                is ApiResult.Success -> reload()
                is ApiResult.Failure -> { }
            }
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            when (source.deleteNote(id)) {
                is ApiResult.Success -> reload()
                is ApiResult.Failure -> { }
            }
        }
    }

    fun createNote(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            when (val r = source.createNote(folderId = _state.value.folderId)) {
                is ApiResult.Success -> onCreated(r.value.id)
                is ApiResult.Failure -> {
                    if (r.failure.kind == FailureKind.UNAUTHORIZED) _state.update { it.copy(signedOut = true) }
                }
            }
        }
    }
}

enum class SaveStatus { Idle, Saving, Saved, Error, Conflict }

data class NoteEditorUiState(
    val noteId: String? = null,
    val title: String = "",
    val body: String = "",
    val version: Int = 1,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val loading: Boolean = true,
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val error: String? = null,
    val signedOut: Boolean = false,
    val wordCount: Int = 0,
    val charCount: Int = 0,
    /** Bumped when the body is changed from outside the text field (AI answer, undo), so the field moves its cursor. */
    val bodyRevision: Int = 0,
    val cursor: Int = 0,
    /** Body before the last AI change; null when there is nothing to undo. */
    val undoBody: String? = null,
    /** A short one-off message key for the screen: "ai_applied_at_end", "new_note_saved", "new_note_failed". */
    val notice: String? = null,
)

class NoteEditorViewModel(
    private val existingId: String?,
    private val source: NoteSource,
) : ViewModel() {
    private val _state = MutableStateFlow(NoteEditorUiState(noteId = existingId, loading = existingId != null))
    val state: StateFlow<NoteEditorUiState> = _state.asStateFlow()
    private var autosaveJob: Job? = null
    private var dirty = false

    init {
        if (existingId != null) load(existingId)
        else _state.update { it.copy(loading = false) }
    }

    private fun load(id: String) {
        viewModelScope.launch {
            when (val r = source.note(id)) {
                is ApiResult.Success -> applyNote(r.value)
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, signedOut = r.failure.kind == FailureKind.UNAUTHORIZED, error = r.failure.message)
                }
            }
        }
    }

    private fun applyNote(n: Note) {
        _state.update {
            it.copy(
                noteId = n.id,
                title = n.title,
                body = n.body,
                version = n.version,
                pinned = n.pinned,
                favorite = n.favorite,
                loading = false,
                wordCount = countWords(n.body),
                charCount = n.body.length,
                saveStatus = SaveStatus.Idle,
            )
        }
        dirty = false
    }

    fun onTitle(v: String) {
        _state.update { it.copy(title = v) }
        scheduleAutosave()
    }

    fun onBody(v: String) {
        _state.update { it.copy(body = v, wordCount = countWords(v), charCount = v.length, undoBody = null) }
        scheduleAutosave()
    }

    /**
     * Puts a reviewed AI answer into the note. The previous text is kept for one-tap undo
     * (and older versions stay available in the note history). Autosave then stores it like any other edit.
     */
    fun applyAiResult(result: com.exambase.ai.ai.AiApplyResult) {
        val before = _state.value.body
        _state.update {
            it.copy(
                body = result.body,
                wordCount = countWords(result.body),
                charCount = result.body.length,
                bodyRevision = it.bodyRevision + 1,
                cursor = result.cursor,
                undoBody = before,
                notice = if (result.fellBackToEnd) "ai_applied_at_end" else null,
            )
        }
        scheduleAutosave()
    }

    fun undoAi() {
        val old = _state.value.undoBody ?: return
        _state.update {
            it.copy(
                body = old,
                wordCount = countWords(old),
                charCount = old.length,
                bodyRevision = it.bodyRevision + 1,
                cursor = old.length,
                undoBody = null,
                notice = null,
            )
        }
        scheduleAutosave()
    }

    /** Saves an AI answer as its own note; the open note is not touched. */
    fun saveAsNewNote(title: String, body: String) {
        viewModelScope.launch {
            when (val r = source.createNote(title = title, body = body)) {
                is ApiResult.Success -> _state.update { it.copy(notice = "new_note_saved") }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        notice = "new_note_failed",
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                    )
                }
            }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun togglePinned() {
        _state.update { it.copy(pinned = !it.pinned) }
        scheduleAutosave()
    }

    fun toggleFavorite() {
        _state.update { it.copy(favorite = !it.favorite) }
        scheduleAutosave()
    }

    /** Insert markdown-ish snippet at end (simple toolbar helpers). */
    fun insertFormat(prefix: String, suffix: String = prefix, placeholder: String = "") {
        val body = _state.value.body
        val insertion = if (placeholder.isNotEmpty()) "$prefix$placeholder$suffix" else "$prefix$suffix"
        onBody(if (body.isEmpty()) insertion else "$body\n$insertion")
    }

    private fun scheduleAutosave() {
        dirty = true
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(800)
            save(silent = true)
        }
    }

    fun saveNow() {
        autosaveJob?.cancel()
        viewModelScope.launch { save(silent = false) }
    }

    private suspend fun save(silent: Boolean) {
        val s = _state.value
        if (!dirty && s.noteId != null) return
        _state.update { it.copy(saveStatus = SaveStatus.Saving, error = null) }
        val result = if (s.noteId == null) {
            source.createNote(title = s.title, body = s.body)
        } else {
            source.updateNote(
                s.noteId,
                UpdateNoteRequest(
                    title = s.title,
                    body = s.body,
                    pinned = s.pinned,
                    favorite = s.favorite,
                    baseVersion = s.version,
                ),
            )
        }
        when (result) {
            is ApiResult.Success -> {
                dirty = false
                applyNote(result.value)
                _state.update { it.copy(saveStatus = SaveStatus.Saved) }
            }
            is ApiResult.Failure -> {
                val conflict = result.failure.code == "version_conflict"
                _state.update {
                    it.copy(
                        saveStatus = if (conflict) SaveStatus.Conflict else SaveStatus.Error,
                        signedOut = result.failure.kind == FailureKind.UNAUTHORIZED,
                        error = if (silent && !conflict) it.error else (result.failure.message ?: "Save failed"),
                    )
                }
            }
        }
    }

    private fun countWords(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotBlank() }
}


data class NoteVersionsUiState(
    val noteId: String,
    val versions: List<NoteVersion> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val signedOut: Boolean = false,
    val restoring: Boolean = false,
    val restored: Boolean = false,
    /** Pair selected for simple comparison (older, newer). */
    val compareA: NoteVersion? = null,
    val compareB: NoteVersion? = null,
)

class NoteVersionsViewModel(
    private val noteId: String,
    private val source: NoteSource,
) : ViewModel() {
    private val _state = MutableStateFlow(NoteVersionsUiState(noteId = noteId))
    val state: StateFlow<NoteVersionsUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        _state.update { it.copy(loading = true, error = null, restored = false) }
        viewModelScope.launch {
            when (val r = source.versions(noteId)) {
                is ApiResult.Success -> _state.update { it.copy(versions = r.value, loading = false) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                        error = r.failure.message ?: "Unable to load versions.",
                    )
                }
            }
        }
    }

    fun selectForCompare(v: NoteVersion) {
        _state.update { s ->
            when {
                s.compareA == null -> s.copy(compareA = v, compareB = null)
                s.compareA.version == v.version -> s.copy(compareA = null, compareB = null)
                else -> s.copy(compareB = v)
            }
        }
    }

    fun clearCompare() = _state.update { it.copy(compareA = null, compareB = null) }

    fun restore(version: Int, onDone: () -> Unit = {}) {
        if (_state.value.restoring) return
        _state.update { it.copy(restoring = true, error = null) }
        viewModelScope.launch {
            when (val r = source.restoreVersion(noteId, version)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(restoring = false, restored = true) }
                    reload()
                    onDone()
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        restoring = false,
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                        error = r.failure.message ?: "Restore failed.",
                    )
                }
            }
        }
    }
}

/** Plain-text export body for share intents. */
fun noteExportText(title: String, body: String, version: Int?): String = buildString {
    appendLine(title.ifBlank { "Untitled note" })
    if (version != null) appendLine("Version $version")
    appendLine("---")
    append(body)
}

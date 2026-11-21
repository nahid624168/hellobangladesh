package com.exambase.ai.offline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val lastSyncAt: String? = null,
    val status: String? = null,
    val conflictCount: Int = 0,
    val running: Boolean = false,
)

class SyncStatusViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = EncryptedSyncPrefs(app)
    private val dao = ExamBaseDatabase.get(app).offlineDao()
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val conflicts = dao.conflictNotes().size
            _state.update {
                it.copy(
                    lastSyncAt = prefs.lastSyncAt,
                    status = prefs.lastSyncStatus,
                    conflictCount = conflicts,
                )
            }
        }
    }

    fun requestSync() {
        _state.update { it.copy(running = true) }
        NotesSyncWorker.enqueue(getApplication())
        // Worker updates prefs; UI refreshes when user returns or pulls
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            refresh()
            _state.update { it.copy(running = false) }
        }
    }
}

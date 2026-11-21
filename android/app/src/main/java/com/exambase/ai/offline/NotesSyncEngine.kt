package com.exambase.ai.offline

import com.exambase.ai.notes.NoteSource
import com.exambase.core.network.ApiResult
import com.exambase.core.network.Note
import com.exambase.core.network.NoteFolder
import com.exambase.core.network.UpdateNoteRequest
import java.time.Instant

/**
 * Pull/push notes using version-based conflict detection ([ConflictPolicy]).
 */
class NotesSyncEngine(
    private val dao: OfflineDao,
    private val remote: NoteSource,
    private val prefs: EncryptedSyncPrefs,
) {
    data class Result(
        val pushed: Int,
        val pulled: Int,
        val conflicts: Int,
        val error: String? = null,
    )

    suspend fun syncOnce(): Result {
        var pushed = 0
        var conflicts = 0
        // 1) Push dirty local notes
        for (local in dao.dirtyNotes()) {
            if (local.conflict) {
                conflicts++
                continue
            }
            when (
                val r = remote.updateNote(
                    local.id,
                    UpdateNoteRequest(
                        title = local.title,
                        body = local.body,
                        pinned = local.pinned,
                        favorite = local.favorite,
                        baseVersion = local.baseVersion,
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    dao.upsertNote(r.value.toEntity(dirty = false, conflict = false))
                    pushed++
                }
                is ApiResult.Failure -> {
                    if (r.failure.code == "version_conflict") {
                        dao.upsertNote(local.copy(conflict = true, localDirty = true))
                        conflicts++
                    } else {
                        prefs.lastSyncStatus = "error:${r.failure.message}"
                        return Result(pushed, 0, conflicts, r.failure.message)
                    }
                }
            }
        }

        // 2) Pull changes since watermark
        val since = prefs.notesWatermark
        val pull = when (val r = remote.notesSync(since)) {
            is ApiResult.Success -> r.value
            is ApiResult.Failure -> {
                prefs.lastSyncStatus = "error:${r.failure.message}"
                return Result(pushed, 0, conflicts, r.failure.message)
            }
        }

        var pulled = 0
        for (server in pull.notes) {
            val local = dao.note(server.id)
            when {
                local == null -> {
                    dao.upsertNote(server.toEntity(dirty = false, conflict = false))
                    pulled++
                }
                local.localDirty && server.version > local.baseVersion -> {
                    dao.upsertNote(local.copy(conflict = true))
                    conflicts++
                }
                !local.localDirty -> {
                    dao.upsertNote(server.toEntity(dirty = false, conflict = false))
                    pulled++
                }
            }
        }
        if (pull.folders.isNotEmpty()) {
            dao.upsertFolders(pull.folders.map { it.toEntity() })
        }

        prefs.notesWatermark = pull.serverTime
        prefs.lastSyncAt = Instant.now().toString()
        prefs.lastSyncStatus = if (conflicts > 0) "conflicts:$conflicts" else "ok"
        return Result(pushed, pulled, conflicts)
    }

    suspend fun resolve(noteId: String, resolution: ConflictResolution): Boolean {
        val local = dao.note(noteId) ?: return false
        if (!local.conflict) return true
        return when (resolution) {
            ConflictResolution.KEEP_LOCAL -> {
                // Force overwrite: send without baseVersion check by using server fetch then update with current server version after user chose local
                when (val remoteNote = remote.note(noteId)) {
                    is ApiResult.Success -> {
                        val forced = remote.updateNote(
                            noteId,
                            UpdateNoteRequest(
                                title = local.title,
                                body = local.body,
                                pinned = local.pinned,
                                favorite = local.favorite,
                                baseVersion = remoteNote.value.version,
                            ),
                        )
                        if (forced is ApiResult.Success) {
                            dao.upsertNote(forced.value.toEntity(dirty = false, conflict = false))
                            true
                        } else false
                    }
                    is ApiResult.Failure -> false
                }
            }
            ConflictResolution.TAKE_SERVER -> {
                when (val remoteNote = remote.note(noteId)) {
                    is ApiResult.Success -> {
                        dao.upsertNote(remoteNote.value.toEntity(dirty = false, conflict = false))
                        true
                    }
                    is ApiResult.Failure -> false
                }
            }
        }
    }
}

private fun Note.toEntity(dirty: Boolean, conflict: Boolean) = OfflineNoteEntity(
    id = id,
    folderId = folderId,
    title = title,
    body = body,
    color = color,
    pinned = pinned,
    favorite = favorite,
    tagsCsv = tags.joinToString(","),
    serverVersion = version,
    baseVersion = version,
    localDirty = dirty,
    deleted = deleted,
    conflict = conflict,
    updatedAt = updatedAt,
    createdAt = createdAt,
)

private fun NoteFolder.toEntity() = OfflineFolderEntity(
    id = id,
    parentId = parentId,
    name = name,
    color = color,
    position = position,
    deleted = deleted,
    updatedAt = updatedAt,
    localDirty = false,
)

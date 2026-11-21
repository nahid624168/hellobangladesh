package com.exambase.ai.notes

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.ApiResult
import com.exambase.core.network.CreateNoteFolderRequest
import com.exambase.core.network.CreateNoteRequest
import com.exambase.core.network.Note
import com.exambase.core.network.NoteApi
import com.exambase.core.network.NoteFolder
import com.exambase.core.network.NoteSummary
import com.exambase.core.network.NoteVersion
import com.exambase.core.network.NotesSyncResponse
import com.exambase.core.network.PageResponse
import com.exambase.core.network.UpdateNoteFolderRequest
import com.exambase.core.network.UpdateNoteRequest

interface NoteSource {
    suspend fun folders(): ApiResult<List<NoteFolder>>
    suspend fun createFolder(name: String, parentId: String? = null): ApiResult<NoteFolder>
    suspend fun deleteFolder(id: String): ApiResult<Unit>
    suspend fun notes(
        folderId: String? = null,
        q: String? = null,
        pinned: Boolean? = null,
        favorite: Boolean? = null,
        cursor: String? = null,
        limit: Int = 50,
    ): ApiResult<PageResponse<NoteSummary>>
    suspend fun note(id: String): ApiResult<Note>
    suspend fun createNote(title: String = "", body: String = "", folderId: String? = null): ApiResult<Note>
    suspend fun updateNote(id: String, req: UpdateNoteRequest): ApiResult<Note>
    suspend fun deleteNote(id: String): ApiResult<Unit>
    suspend fun versions(id: String): ApiResult<List<NoteVersion>>
    suspend fun restoreVersion(id: String, version: Int): ApiResult<Note>
    suspend fun notesSync(since: String?, limit: Int = 200): ApiResult<NotesSyncResponse>
}

class NoteRepository(private val api: NoteApi, private val auth: AuthRepository) : NoteSource {
    override suspend fun folders() = auth.authed { api.noteFolders(it) }
    override suspend fun createFolder(name: String, parentId: String?) =
        auth.authed { api.createNoteFolder(it, CreateNoteFolderRequest(name, parentId)) }
    override suspend fun deleteFolder(id: String) = auth.authed { api.deleteNoteFolder(it, id) }
    override suspend fun notes(folderId: String?, q: String?, pinned: Boolean?, favorite: Boolean?, cursor: String?, limit: Int) =
        auth.authed { api.notes(it, folderId, q, null, pinned, favorite, limit, cursor) }
    override suspend fun note(id: String) = auth.authed { api.note(it, id) }
    override suspend fun createNote(title: String, body: String, folderId: String?) =
        auth.authed { api.createNote(it, CreateNoteRequest(title = title, body = body, folderId = folderId)) }
    override suspend fun updateNote(id: String, req: UpdateNoteRequest) = auth.authed { api.updateNote(it, id, req) }
    override suspend fun deleteNote(id: String) = auth.authed { api.deleteNote(it, id) }
    override suspend fun versions(id: String) = auth.authed { api.noteVersions(it, id) }
    override suspend fun restoreVersion(id: String, version: Int) = auth.authed { api.restoreNoteVersion(it, id, version) }
    override suspend fun notesSync(since: String?, limit: Int) = auth.authed { api.notesSync(it, since, limit) }
}

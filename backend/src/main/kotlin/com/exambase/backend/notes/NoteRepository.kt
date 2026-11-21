package com.exambase.backend.notes

import com.exambase.backend.academic.Page
import java.time.Instant
import java.util.UUID

interface NoteRepository {
    fun createFolder(ownerId: UUID, name: String, parentId: UUID?, color: String?): NoteFolderDto
    fun listFolders(ownerId: UUID, includeDeleted: Boolean = false): List<NoteFolderDto>
    fun updateFolder(ownerId: UUID, id: UUID, req: UpdateFolderRequest): NoteFolderDto?
    fun deleteFolder(ownerId: UUID, id: UUID, now: Instant): Boolean

    fun createNote(ownerId: UUID, req: CreateNoteRequest): NoteDto
    fun listNotes(
        ownerId: UUID, folderId: UUID?, q: String?, tag: String?, pinned: Boolean?, favorite: Boolean?,
        limit: Int, offset: Int,
    ): Page<NoteSummaryDto>
    fun getNote(ownerId: UUID, id: UUID): NoteDto?
    fun updateNote(ownerId: UUID, id: UUID, req: UpdateNoteRequest, now: Instant): NoteDto?
    fun deleteNote(ownerId: UUID, id: UUID, now: Instant): Boolean

    fun listVersions(ownerId: UUID, noteId: UUID): List<NoteVersionDto>?
    fun restoreVersion(ownerId: UUID, noteId: UUID, version: Int, now: Instant): NoteDto?

    fun addAttachment(ownerId: UUID, noteId: UUID, req: AddAttachmentRequest): NoteAttachmentDto?
    fun removeAttachment(ownerId: UUID, noteId: UUID, attachmentId: UUID): Boolean

    fun sync(ownerId: UUID, since: Instant?, limit: Int): NotesSyncResponse
}

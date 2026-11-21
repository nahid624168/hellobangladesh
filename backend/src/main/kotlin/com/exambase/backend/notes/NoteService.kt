package com.exambase.backend.notes

import com.exambase.backend.ApiException
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Instant
import java.util.UUID

class NoteService(
    private val repo: NoteRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val colorRe = Regex("^#[0-9A-Fa-f]{6}$")

    fun createFolder(userId: UUID, req: CreateFolderRequest): NoteFolderDto {
        val name = req.name.trim()
        if (name.isEmpty() || name.length > 120) bad("name", "Name must be 1–120 characters.")
        req.color?.let { if (!colorRe.matches(it)) bad("color", "Use #RRGGBB.") }
        val parent = optionalUuid("parentId", req.parentId)
        return try {
            repo.createFolder(userId, name, parent, req.color)
        } catch (_: Exception) {
            notFound()
        }
    }

    fun listFolders(userId: UUID): List<NoteFolderDto> = repo.listFolders(userId)

    fun updateFolder(userId: UUID, id: String, req: UpdateFolderRequest): NoteFolderDto {
        req.name?.trim()?.let { if (it.isEmpty() || it.length > 120) bad("name", "Name must be 1–120 characters.") }
        req.color?.let { if (!colorRe.matches(it)) bad("color", "Use #RRGGBB.") }
        return repo.updateFolder(userId, pathId(id), req.copy(name = req.name?.trim())) ?: notFound()
    }

    fun deleteFolder(userId: UUID, id: String) {
        if (!repo.deleteFolder(userId, pathId(id), Instant.now(clock))) notFound()
    }

    fun createNote(userId: UUID, req: CreateNoteRequest): NoteDto {
        validateNoteContent(req.title, req.body)
        req.color?.let { if (!colorRe.matches(it)) bad("color", "Use #RRGGBB.") }
        if (req.tags.size > 20) bad("tags", "At most 20 tags.")
        return try {
            repo.createNote(userId, req.copy(title = req.title.take(200), body = req.body.take(200_000), tags = req.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()))
        } catch (_: Exception) {
            notFound()
        }
    }

    fun listNotes(
        userId: UUID, folderId: String?, q: String?, tag: String?, pinned: String?, favorite: String?,
        limitRaw: String?, cursor: String?,
    ): Page<NoteSummaryDto> {
        val limit = Paging.limit(limitRaw)
        val offset = Paging.decode(cursor)
        return repo.listNotes(
            userId,
            optionalUuid("folderId", folderId),
            q?.trim()?.takeIf { it.isNotEmpty() },
            tag?.trim()?.takeIf { it.isNotEmpty() },
            pinned?.toBooleanStrictOrNull(),
            favorite?.toBooleanStrictOrNull(),
            limit, offset,
        )
    }

    fun getNote(userId: UUID, id: String): NoteDto =
        repo.getNote(userId, pathId(id))?.takeUnless { it.deleted } ?: notFound()

    fun updateNote(userId: UUID, id: String, req: UpdateNoteRequest): NoteDto {
        req.title?.let { if (it.length > 200) bad("title", "At most 200 characters.") }
        req.body?.let { if (it.length > 200_000) bad("body", "At most 200000 characters.") }
        req.color?.let { if (!colorRe.matches(it)) bad("color", "Use #RRGGBB.") }
        val noteId = pathId(id)
        if (req.baseVersion != null) {
            val current = repo.getNote(userId, noteId) ?: notFound()
            if (current.deleted) notFound()
            if (current.version != req.baseVersion) {
                throw ApiException(HttpStatusCode.Conflict, "version_conflict", "Note was updated elsewhere. Reload and retry.")
            }
        }
        return repo.updateNote(userId, noteId, req, Instant.now(clock)) ?: notFound()
    }

    fun deleteNote(userId: UUID, id: String) {
        if (!repo.deleteNote(userId, pathId(id), Instant.now(clock))) notFound()
    }

    fun versions(userId: UUID, id: String): List<NoteVersionDto> =
        repo.listVersions(userId, pathId(id)) ?: notFound()

    fun restore(userId: UUID, id: String, version: Int): NoteDto =
        repo.restoreVersion(userId, pathId(id), version, Instant.now(clock)) ?: notFound()

    fun addAttachment(userId: UUID, noteId: String, req: AddAttachmentRequest): NoteAttachmentDto {
        if (req.url.isBlank() || req.url.length > 2000) bad("url", "URL required (max 2000).")
        return repo.addAttachment(userId, pathId(noteId), req) ?: notFound()
    }

    fun removeAttachment(userId: UUID, noteId: String, attachmentId: String) {
        if (!repo.removeAttachment(userId, pathId(noteId), pathId(attachmentId))) notFound()
    }

    fun sync(userId: UUID, sinceRaw: String?, limitRaw: String?): NotesSyncResponse {
        val since = sinceRaw?.let {
            runCatching { Instant.parse(it) }.getOrElse { bad("since", "Use ISO-8601 timestamp.") }
        }
        val limit = (limitRaw?.toIntOrNull() ?: 200).coerceIn(1, 500)
        return repo.sync(userId, since, limit)
    }

    private fun validateNoteContent(title: String, body: String) {
        if (title.length > 200) bad("title", "At most 200 characters.")
        if (body.length > 200_000) bad("body", "At most 200000 characters.")
    }

    private fun pathId(raw: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Not found.")

    private fun optionalUuid(field: String, raw: String?): UUID? {
        if (raw.isNullOrBlank()) return null
        return runCatching { UUID.fromString(raw.trim()) }.getOrNull()
            ?: throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid id.", mapOf(field to "Must be a valid id."))
    }

    private fun bad(field: String, message: String): Nothing =
        throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid input.", mapOf(field to message))

    private fun notFound(): Nothing =
        throw ApiException(HttpStatusCode.NotFound, "not_found", "Not found.")
}

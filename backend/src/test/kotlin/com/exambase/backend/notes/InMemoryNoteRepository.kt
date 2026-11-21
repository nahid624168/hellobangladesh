package com.exambase.backend.notes

import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryNoteRepository : NoteRepository {
    data class Folder(val id: UUID, val ownerId: UUID, var parentId: UUID?, var name: String, var color: String?, var position: Int = 0, var deletedAt: Instant? = null, val createdAt: Instant = Instant.parse("2026-11-21T00:00:00Z"), var updatedAt: Instant = Instant.parse("2026-11-21T00:00:00Z"))
    data class Note(
        val id: UUID, val ownerId: UUID, var folderId: UUID?, var title: String, var body: String, var color: String?,
        var pinned: Boolean, var favorite: Boolean, var tags: List<String>, var lessonId: UUID?, var quizSetId: UUID?,
        var flashcardSetId: UUID?, var version: Int = 1, var deletedAt: Instant? = null,
        val createdAt: Instant = Instant.parse("2026-11-21T00:00:00Z"), var updatedAt: Instant = Instant.parse("2026-11-21T00:00:00Z"),
    )
    data class Ver(val version: Int, val title: String, val body: String, val createdAt: Instant = Instant.parse("2026-11-21T00:00:00Z"))

    private val folders = ConcurrentHashMap<UUID, Folder>()
    private val notes = ConcurrentHashMap<UUID, Note>()
    private val versions = ConcurrentHashMap<UUID, MutableList<Ver>>()
    private val attachments = ConcurrentHashMap<UUID, MutableList<NoteAttachmentDto>>()

    private fun folderDto(f: Folder) = NoteFolderDto(f.id.toString(), f.parentId?.toString(), f.name, f.color, f.position, f.updatedAt.toString(), f.createdAt.toString(), f.deletedAt != null)
    private fun noteDto(n: Note) = NoteDto(
        n.id.toString(), n.folderId?.toString(), n.title, n.body, n.color, n.pinned, n.favorite, n.tags,
        n.lessonId?.toString(), n.quizSetId?.toString(), n.flashcardSetId?.toString(), n.version,
        n.updatedAt.toString(), n.createdAt.toString(), n.deletedAt != null, attachments[n.id].orEmpty(),
    )
    private fun summary(n: Note) = NoteSummaryDto(n.id.toString(), n.folderId?.toString(), n.title, n.body.take(160), n.color, n.pinned, n.favorite, n.tags, n.version, n.updatedAt.toString())

    override fun createFolder(ownerId: UUID, name: String, parentId: UUID?, color: String?): NoteFolderDto {
        if (parentId != null && folders[parentId]?.let { it.ownerId == ownerId && it.deletedAt == null } != true) error("parent")
        val id = UUID.randomUUID()
        val f = Folder(id, ownerId, parentId, name, color)
        folders[id] = f
        return folderDto(f)
    }

    override fun listFolders(ownerId: UUID, includeDeleted: Boolean) =
        folders.values.filter { it.ownerId == ownerId && (includeDeleted || it.deletedAt == null) }.sortedBy { it.name }.map { folderDto(it) }

    override fun updateFolder(ownerId: UUID, id: UUID, req: UpdateFolderRequest): NoteFolderDto? {
        val f = folders[id]?.takeIf { it.ownerId == ownerId && it.deletedAt == null } ?: return null
        req.name?.let { f.name = it }
        if (req.clearParent) f.parentId = null else req.parentId?.let { f.parentId = UUID.fromString(it) }
        if (req.clearColor) f.color = null else req.color?.let { f.color = it }
        req.position?.let { f.position = it }
        f.updatedAt = Instant.now()
        return folderDto(f)
    }

    override fun deleteFolder(ownerId: UUID, id: UUID, now: Instant): Boolean {
        val f = folders[id]?.takeIf { it.ownerId == ownerId && it.deletedAt == null } ?: return false
        f.deletedAt = now; f.updatedAt = now
        return true
    }

    override fun createNote(ownerId: UUID, req: CreateNoteRequest): NoteDto {
        val folderId = req.folderId?.let(UUID::fromString)
        if (folderId != null && folders[folderId]?.let { it.ownerId == ownerId && it.deletedAt == null } != true) error("folder")
        val id = UUID.randomUUID()
        val n = Note(id, ownerId, folderId, req.title, req.body, req.color, req.pinned, req.favorite, req.tags, req.lessonId?.let(UUID::fromString), req.quizSetId?.let(UUID::fromString), req.flashcardSetId?.let(UUID::fromString))
        notes[id] = n
        versions.getOrPut(id) { mutableListOf() }.add(Ver(1, n.title, n.body))
        return noteDto(n)
    }

    override fun listNotes(ownerId: UUID, folderId: UUID?, q: String?, tag: String?, pinned: Boolean?, favorite: Boolean?, limit: Int, offset: Int): Page<NoteSummaryDto> {
        var list = notes.values.filter { it.ownerId == ownerId && it.deletedAt == null }
        if (folderId != null) list = list.filter { it.folderId == folderId }
        if (pinned == true) list = list.filter { it.pinned }
        if (favorite == true) list = list.filter { it.favorite }
        if (!q.isNullOrBlank()) list = list.filter { it.title.contains(q, true) || it.body.contains(q, true) }
        if (!tag.isNullOrBlank()) list = list.filter { tag in it.tags }
        list = list.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
        val total = list.size
        return Page(list.drop(offset).take(limit).map { summary(it) }, Paging.encode(offset, limit, total))
    }

    override fun getNote(ownerId: UUID, id: UUID): NoteDto? = notes[id]?.takeIf { it.ownerId == ownerId }?.let { noteDto(it) }

    override fun updateNote(ownerId: UUID, id: UUID, req: UpdateNoteRequest, now: Instant): NoteDto? {
        val n = notes[id]?.takeIf { it.ownerId == ownerId && it.deletedAt == null } ?: return null
        if (req.baseVersion != null && req.baseVersion != n.version) return null
        req.title?.let { n.title = it }
        req.body?.let { n.body = it }
        if (req.clearFolder) n.folderId = null else req.folderId?.let { n.folderId = UUID.fromString(it) }
        if (req.clearColor) n.color = null else req.color?.let { n.color = it }
        req.pinned?.let { n.pinned = it }
        req.favorite?.let { n.favorite = it }
        req.tags?.let { n.tags = it }
        n.version += 1
        n.updatedAt = now
        versions.getOrPut(id) { mutableListOf() }.add(Ver(n.version, n.title, n.body, now))
        return noteDto(n)
    }

    override fun deleteNote(ownerId: UUID, id: UUID, now: Instant): Boolean {
        val n = notes[id]?.takeIf { it.ownerId == ownerId && it.deletedAt == null } ?: return false
        n.deletedAt = now; n.updatedAt = now; n.version += 1
        return true
    }

    override fun listVersions(ownerId: UUID, noteId: UUID): List<NoteVersionDto>? {
        notes[noteId]?.takeIf { it.ownerId == ownerId } ?: return null
        return versions[noteId].orEmpty().sortedByDescending { it.version }.map { NoteVersionDto(it.version, it.title, it.body, it.createdAt.toString()) }
    }

    override fun restoreVersion(ownerId: UUID, noteId: UUID, version: Int, now: Instant): NoteDto? {
        val snap = versions[noteId]?.firstOrNull { it.version == version } ?: return null
        return updateNote(ownerId, noteId, UpdateNoteRequest(title = snap.title, body = snap.body), now)
    }

    override fun addAttachment(ownerId: UUID, noteId: UUID, req: AddAttachmentRequest): NoteAttachmentDto? {
        notes[noteId]?.takeIf { it.ownerId == ownerId && it.deletedAt == null } ?: return null
        val a = NoteAttachmentDto(UUID.randomUUID().toString(), req.url, req.fileName, req.contentType, req.sizeBytes)
        attachments.getOrPut(noteId) { mutableListOf() }.add(a)
        return a
    }

    override fun removeAttachment(ownerId: UUID, noteId: UUID, attachmentId: UUID): Boolean {
        notes[noteId]?.takeIf { it.ownerId == ownerId } ?: return false
        val list = attachments[noteId] ?: return false
        return list.removeIf { it.id == attachmentId.toString() }
    }

    override fun sync(ownerId: UUID, since: Instant?, limit: Int): NotesSyncResponse {
        val ns = notes.values.filter { it.ownerId == ownerId && (since == null || it.updatedAt > since) }.take(limit).map { noteDto(it) }
        val fs = folders.values.filter { it.ownerId == ownerId && (since == null || it.updatedAt > since) }.take(limit).map { folderDto(it) }
        return NotesSyncResponse(ns, fs, Instant.now().toString())
    }
}

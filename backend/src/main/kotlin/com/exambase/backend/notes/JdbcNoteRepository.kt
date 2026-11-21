package com.exambase.backend.notes

import com.exambase.backend.Database
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import com.exambase.backend.academic.escapeLike
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class JdbcNoteRepository(private val db: Database) : NoteRepository {

    override fun createFolder(ownerId: UUID, name: String, parentId: UUID?, color: String?): NoteFolderDto =
        db.withConnection { c ->
            if (parentId != null && !ownsFolder(c, ownerId, parentId)) error("parent")
            c.prepareStatement(
                """
                INSERT INTO note_folders (owner_id, parent_id, name, color)
                VALUES (?, ?, ?, ?)
                RETURNING id, parent_id, name, color, position, updated_at, created_at, deleted_at
                """,
            ).use { ps ->
                ps.setObject(1, ownerId)
                ps.setObject(2, parentId)
                ps.setString(3, name)
                ps.setString(4, color)
                ps.executeQuery().use { rs -> rs.next(); folderDto(rs) }
            }
        }

    override fun listFolders(ownerId: UUID, includeDeleted: Boolean): List<NoteFolderDto> = db.withConnection { c ->
        val del = if (includeDeleted) "" else "AND deleted_at IS NULL"
        c.prepareStatement(
            "SELECT id, parent_id, name, color, position, updated_at, created_at, deleted_at FROM note_folders WHERE owner_id = ? $del ORDER BY position, name",
        ).use { ps ->
            ps.setObject(1, ownerId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(folderDto(rs)) } }
        }
    }

    override fun updateFolder(ownerId: UUID, id: UUID, req: UpdateFolderRequest): NoteFolderDto? = db.withConnection { c ->
        if (!ownsFolder(c, ownerId, id)) return@withConnection null
        if (req.parentId != null) {
            val pid = UUID.fromString(req.parentId)
            if (pid == id || !ownsFolder(c, ownerId, pid)) return@withConnection null
        }
        c.prepareStatement(
            """
            UPDATE note_folders SET
              name = COALESCE(?, name),
              parent_id = CASE WHEN ? THEN NULL WHEN ? IS NOT NULL THEN ?::uuid ELSE parent_id END,
              color = CASE WHEN ? THEN NULL WHEN ? IS NOT NULL THEN ? ELSE color END,
              position = COALESCE(?, position),
              updated_at = now()
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            RETURNING id, parent_id, name, color, position, updated_at, created_at, deleted_at
            """,
        ).use { ps ->
            ps.setString(1, req.name)
            ps.setBoolean(2, req.clearParent)
            ps.setString(3, req.parentId)
            ps.setString(4, req.parentId)
            ps.setBoolean(5, req.clearColor)
            ps.setString(6, req.color)
            ps.setString(7, req.color)
            if (req.position != null) ps.setInt(8, req.position) else ps.setObject(8, null)
            ps.setObject(9, id)
            ps.setObject(10, ownerId)
            ps.executeQuery().use { rs -> if (rs.next()) folderDto(rs) else null }
        }
    }

    override fun deleteFolder(ownerId: UUID, id: UUID, now: Instant): Boolean = db.withConnection { c ->
        c.prepareStatement(
            "UPDATE note_folders SET deleted_at = ?, updated_at = ? WHERE id = ? AND owner_id = ? AND deleted_at IS NULL",
        ).use { ps ->
            ps.setTimestamp(1, Timestamp.from(now))
            ps.setTimestamp(2, Timestamp.from(now))
            ps.setObject(3, id)
            ps.setObject(4, ownerId)
            ps.executeUpdate() > 0
        }
    }

    override fun createNote(ownerId: UUID, req: CreateNoteRequest): NoteDto = db.withConnection { c ->
        val folderId = req.folderId?.let(UUID::fromString)
        if (folderId != null && !ownsFolder(c, ownerId, folderId)) error("folder")
        val id = c.prepareStatement(
            """
            INSERT INTO notes (owner_id, folder_id, title, body, color, pinned, favorite, tags, lesson_id, quiz_set_id, flashcard_set_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """,
        ).use { ps ->
            ps.setObject(1, ownerId)
            ps.setObject(2, folderId)
            ps.setString(3, req.title)
            ps.setString(4, req.body)
            ps.setString(5, req.color)
            ps.setBoolean(6, req.pinned)
            ps.setBoolean(7, req.favorite)
            ps.setArray(8, c.createArrayOf("text", req.tags.toTypedArray()))
            ps.setObject(9, req.lessonId?.let(UUID::fromString))
            ps.setObject(10, req.quizSetId?.let(UUID::fromString))
            ps.setObject(11, req.flashcardSetId?.let(UUID::fromString))
            ps.executeQuery().use { rs -> rs.next(); rs.getObject(1, UUID::class.java) }
        }
        // initial version snapshot
        c.prepareStatement(
            "INSERT INTO note_versions (note_id, version, title, body) SELECT id, version, title, body FROM notes WHERE id = ?",
        ).use { ps -> ps.setObject(1, id); ps.executeUpdate() }
        getNote(ownerId, id)!!
    }

    override fun listNotes(
        ownerId: UUID, folderId: UUID?, q: String?, tag: String?, pinned: Boolean?, favorite: Boolean?,
        limit: Int, offset: Int,
    ): Page<NoteSummaryDto> = db.withConnection { c ->
        val where = StringBuilder("owner_id = ? AND deleted_at IS NULL")
        val params = mutableListOf<Any>(ownerId)
        if (folderId != null) { where.append(" AND folder_id = ?"); params += folderId }
        if (pinned == true) where.append(" AND pinned = TRUE")
        if (favorite == true) where.append(" AND favorite = TRUE")
        if (!q.isNullOrBlank()) {
            where.append(" AND (title ILIKE ? OR body ILIKE ?)")
            val like = "%" + escapeLike(q.trim()) + "%"
            params += like; params += like
        }
        if (!tag.isNullOrBlank()) {
            where.append(" AND ? = ANY(tags)")
            params += tag.trim().lowercase()
        }
        val total = c.prepareStatement("SELECT count(*) FROM notes WHERE $where").use { ps ->
            params.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        val items = c.prepareStatement(
            """
            SELECT id, folder_id, title, left(body, 160) AS preview, color, pinned, favorite, tags, version, updated_at
            FROM notes WHERE $where
            ORDER BY pinned DESC, updated_at DESC
            LIMIT ? OFFSET ?
            """,
        ).use { ps ->
            params.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
            ps.setInt(params.size + 1, limit)
            ps.setInt(params.size + 2, offset)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(summaryDto(rs)) } }
        }
        Page(items, Paging.encode(offset, limit, total))
    }

    override fun getNote(ownerId: UUID, id: UUID): NoteDto? = db.withConnection { c ->
        val note = c.prepareStatement(
            """
            SELECT id, folder_id, title, body, color, pinned, favorite, tags, lesson_id, quiz_set_id, flashcard_set_id,
                   version, updated_at, created_at, deleted_at
            FROM notes WHERE id = ? AND owner_id = ?
            """,
        ).use { ps ->
            ps.setObject(1, id); ps.setObject(2, ownerId)
            ps.executeQuery().use { rs -> if (!rs.next()) return@withConnection null else noteDto(rs, emptyList()) }
        }
        val atts = c.prepareStatement(
            "SELECT id, url, file_name, content_type, size_bytes FROM note_attachments WHERE note_id = ? ORDER BY created_at",
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            NoteAttachmentDto(
                                id = rs.getObject("id", UUID::class.java).toString(),
                                url = rs.getString("url"),
                                fileName = rs.getString("file_name"),
                                contentType = rs.getString("content_type"),
                                sizeBytes = rs.getObject("size_bytes")?.let { rs.getLong("size_bytes") },
                            ),
                        )
                    }
                }
            }
        }
        note.copy(attachments = atts)
    }

    override fun updateNote(ownerId: UUID, id: UUID, req: UpdateNoteRequest, now: Instant): NoteDto? = db.withConnection { c ->
        val cur = c.prepareStatement(
            "SELECT version, title, body FROM notes WHERE id = ? AND owner_id = ? AND deleted_at IS NULL",
        ).use { ps ->
            ps.setObject(1, id); ps.setObject(2, ownerId)
            ps.executeQuery().use { rs -> if (!rs.next()) return@withConnection null; rs }
        }
        val currentVersion = cur.getInt("version")
        if (req.baseVersion != null && req.baseVersion != currentVersion) {
            return@withConnection null // service maps to conflict via sentinel — use exception path in service
        }
        val newVersion = currentVersion + 1
        c.prepareStatement(
            """
            UPDATE notes SET
              title = COALESCE(?, title),
              body = COALESCE(?, body),
              folder_id = CASE WHEN ? THEN NULL WHEN ? IS NOT NULL THEN ?::uuid ELSE folder_id END,
              color = CASE WHEN ? THEN NULL WHEN ? IS NOT NULL THEN ? ELSE color END,
              pinned = COALESCE(?, pinned),
              favorite = COALESCE(?, favorite),
              tags = COALESCE(?, tags),
              lesson_id = CASE WHEN ? THEN NULL WHEN ? IS NOT NULL THEN ?::uuid ELSE lesson_id END,
              quiz_set_id = CASE WHEN ? THEN NULL WHEN ? IS NOT NULL THEN ?::uuid ELSE quiz_set_id END,
              flashcard_set_id = CASE WHEN ? THEN NULL WHEN ? IS NOT NULL THEN ?::uuid ELSE flashcard_set_id END,
              version = ?,
              updated_at = ?
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """,
        ).use { ps ->
            var i = 1
            ps.setString(i++, req.title)
            ps.setString(i++, req.body)
            ps.setBoolean(i++, req.clearFolder); ps.setString(i++, req.folderId); ps.setString(i++, req.folderId)
            ps.setBoolean(i++, req.clearColor); ps.setString(i++, req.color); ps.setString(i++, req.color)
            if (req.pinned != null) ps.setBoolean(i++, req.pinned) else ps.setObject(i++, null)
            if (req.favorite != null) ps.setBoolean(i++, req.favorite) else ps.setObject(i++, null)
            if (req.tags != null) ps.setArray(i++, c.createArrayOf("text", req.tags.toTypedArray())) else ps.setObject(i++, null)
            ps.setBoolean(i++, req.clearLesson); ps.setString(i++, req.lessonId); ps.setString(i++, req.lessonId)
            ps.setBoolean(i++, req.clearQuizSet); ps.setString(i++, req.quizSetId); ps.setString(i++, req.quizSetId)
            ps.setBoolean(i++, req.clearFlashcardSet); ps.setString(i++, req.flashcardSetId); ps.setString(i++, req.flashcardSetId)
            ps.setInt(i++, newVersion)
            ps.setTimestamp(i++, Timestamp.from(now))
            ps.setObject(i++, id)
            ps.setObject(i, ownerId)
            if (ps.executeUpdate() == 0) return@withConnection null
        }
        // snapshot new version
        c.prepareStatement(
            "INSERT INTO note_versions (note_id, version, title, body) SELECT id, version, title, body FROM notes WHERE id = ?",
        ).use { ps -> ps.setObject(1, id); ps.executeUpdate() }
        getNote(ownerId, id)
    }

    override fun deleteNote(ownerId: UUID, id: UUID, now: Instant): Boolean = db.withConnection { c ->
        c.prepareStatement(
            "UPDATE notes SET deleted_at = ?, updated_at = ?, version = version + 1 WHERE id = ? AND owner_id = ? AND deleted_at IS NULL",
        ).use { ps ->
            ps.setTimestamp(1, Timestamp.from(now))
            ps.setTimestamp(2, Timestamp.from(now))
            ps.setObject(3, id)
            ps.setObject(4, ownerId)
            ps.executeUpdate() > 0
        }
    }

    override fun listVersions(ownerId: UUID, noteId: UUID): List<NoteVersionDto>? = db.withConnection { c ->
        if (!ownsNote(c, ownerId, noteId)) return@withConnection null
        c.prepareStatement(
            "SELECT version, title, body, created_at FROM note_versions WHERE note_id = ? ORDER BY version DESC",
        ).use { ps ->
            ps.setObject(1, noteId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            NoteVersionDto(
                                version = rs.getInt("version"),
                                title = rs.getString("title"),
                                body = rs.getString("body"),
                                createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun restoreVersion(ownerId: UUID, noteId: UUID, version: Int, now: Instant): NoteDto? = db.withConnection { c ->
        if (!ownsNote(c, ownerId, noteId)) return@withConnection null
        val snap = c.prepareStatement(
            "SELECT title, body FROM note_versions WHERE note_id = ? AND version = ?",
        ).use { ps ->
            ps.setObject(1, noteId); ps.setInt(2, version)
            ps.executeQuery().use { rs -> if (!rs.next()) return@withConnection null; rs.getString("title") to rs.getString("body") }
        }
        updateNote(ownerId, noteId, UpdateNoteRequest(title = snap.first, body = snap.second), now)
    }

    override fun addAttachment(ownerId: UUID, noteId: UUID, req: AddAttachmentRequest): NoteAttachmentDto? = db.withConnection { c ->
        if (!ownsNote(c, ownerId, noteId)) return@withConnection null
        c.prepareStatement(
            """
            INSERT INTO note_attachments (note_id, url, file_name, content_type, size_bytes)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id, url, file_name, content_type, size_bytes
            """,
        ).use { ps ->
            ps.setObject(1, noteId)
            ps.setString(2, req.url)
            ps.setString(3, req.fileName)
            ps.setString(4, req.contentType)
            if (req.sizeBytes != null) ps.setLong(5, req.sizeBytes) else ps.setObject(5, null)
            ps.executeQuery().use { rs ->
                rs.next()
                NoteAttachmentDto(
                    id = rs.getObject("id", UUID::class.java).toString(),
                    url = rs.getString("url"),
                    fileName = rs.getString("file_name"),
                    contentType = rs.getString("content_type"),
                    sizeBytes = rs.getObject("size_bytes")?.let { rs.getLong("size_bytes") },
                )
            }
        }
    }

    override fun removeAttachment(ownerId: UUID, noteId: UUID, attachmentId: UUID): Boolean = db.withConnection { c ->
        if (!ownsNote(c, ownerId, noteId)) return@withConnection false
        c.prepareStatement("DELETE FROM note_attachments WHERE id = ? AND note_id = ?").use { ps ->
            ps.setObject(1, attachmentId); ps.setObject(2, noteId)
            ps.executeUpdate() > 0
        }
    }

    override fun sync(ownerId: UUID, since: Instant?, limit: Int): NotesSyncResponse = db.withConnection { c ->
        val now = Instant.now()
        val noteSql = if (since == null) {
            "SELECT id, folder_id, title, body, color, pinned, favorite, tags, lesson_id, quiz_set_id, flashcard_set_id, version, updated_at, created_at, deleted_at FROM notes WHERE owner_id = ? ORDER BY updated_at LIMIT ?"
        } else {
            "SELECT id, folder_id, title, body, color, pinned, favorite, tags, lesson_id, quiz_set_id, flashcard_set_id, version, updated_at, created_at, deleted_at FROM notes WHERE owner_id = ? AND updated_at > ? ORDER BY updated_at LIMIT ?"
        }
        val notes = c.prepareStatement(noteSql).use { ps ->
            ps.setObject(1, ownerId)
            if (since != null) {
                ps.setTimestamp(2, Timestamp.from(since))
                ps.setInt(3, limit)
            } else ps.setInt(2, limit)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(noteDto(rs, emptyList())) } }
        }
        val folderSql = if (since == null) {
            "SELECT id, parent_id, name, color, position, updated_at, created_at, deleted_at FROM note_folders WHERE owner_id = ? ORDER BY updated_at LIMIT ?"
        } else {
            "SELECT id, parent_id, name, color, position, updated_at, created_at, deleted_at FROM note_folders WHERE owner_id = ? AND updated_at > ? ORDER BY updated_at LIMIT ?"
        }
        val folders = c.prepareStatement(folderSql).use { ps ->
            ps.setObject(1, ownerId)
            if (since != null) {
                ps.setTimestamp(2, Timestamp.from(since))
                ps.setInt(3, limit)
            } else ps.setInt(2, limit)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(folderDto(rs)) } }
        }
        NotesSyncResponse(notes, folders, now.toString())
    }

    private fun ownsFolder(c: Connection, ownerId: UUID, id: UUID): Boolean =
        c.prepareStatement("SELECT 1 FROM note_folders WHERE id = ? AND owner_id = ? AND deleted_at IS NULL").use { ps ->
            ps.setObject(1, id); ps.setObject(2, ownerId); ps.executeQuery().use { it.next() }
        }

    private fun ownsNote(c: Connection, ownerId: UUID, id: UUID): Boolean =
        c.prepareStatement("SELECT 1 FROM notes WHERE id = ? AND owner_id = ?").use { ps ->
            ps.setObject(1, id); ps.setObject(2, ownerId); ps.executeQuery().use { it.next() }
        }

    private fun folderDto(rs: ResultSet) = NoteFolderDto(
        id = rs.getObject("id", UUID::class.java).toString(),
        parentId = rs.getObject("parent_id", UUID::class.java)?.toString(),
        name = rs.getString("name"),
        color = rs.getString("color"),
        position = rs.getInt("position"),
        updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
        createdAt = rs.getTimestamp("created_at").toInstant().toString(),
        deleted = rs.getTimestamp("deleted_at") != null,
    )

    private fun summaryDto(rs: ResultSet): NoteSummaryDto {
        @Suppress("UNCHECKED_CAST")
        val tags = (rs.getArray("tags")?.array as? Array<String>)?.toList() ?: emptyList()
        return NoteSummaryDto(
            id = rs.getObject("id", UUID::class.java).toString(),
            folderId = rs.getObject("folder_id", UUID::class.java)?.toString(),
            title = rs.getString("title"),
            preview = rs.getString("preview") ?: "",
            color = rs.getString("color"),
            pinned = rs.getBoolean("pinned"),
            favorite = rs.getBoolean("favorite"),
            tags = tags,
            version = rs.getInt("version"),
            updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
        )
    }

    private fun noteDto(rs: ResultSet, atts: List<NoteAttachmentDto>): NoteDto {
        @Suppress("UNCHECKED_CAST")
        val tags = (rs.getArray("tags")?.array as? Array<String>)?.toList() ?: emptyList()
        return NoteDto(
            id = rs.getObject("id", UUID::class.java).toString(),
            folderId = rs.getObject("folder_id", UUID::class.java)?.toString(),
            title = rs.getString("title"),
            body = rs.getString("body"),
            color = rs.getString("color"),
            pinned = rs.getBoolean("pinned"),
            favorite = rs.getBoolean("favorite"),
            tags = tags,
            lessonId = rs.getObject("lesson_id", UUID::class.java)?.toString(),
            quizSetId = rs.getObject("quiz_set_id", UUID::class.java)?.toString(),
            flashcardSetId = rs.getObject("flashcard_set_id", UUID::class.java)?.toString(),
            version = rs.getInt("version"),
            updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
            createdAt = rs.getTimestamp("created_at").toInstant().toString(),
            deleted = rs.getTimestamp("deleted_at") != null,
            attachments = atts,
        )
    }
}

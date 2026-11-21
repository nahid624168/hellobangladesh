package com.exambase.backend.notes

import com.exambase.backend.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFailsWith

class NoteServiceTest {
    private val repo = InMemoryNoteRepository()
    private val service = NoteService(repo)
    private val user = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test fun notesArePrivate() {
        val note = service.createNote(user, CreateNoteRequest(title = "Secret", body = "private"))
        assertFailsWith<ApiException> { service.getNote(other, note.id) }
        assertEquals(0, service.listNotes(other, null, null, null, null, null, null, null).items.size)
    }

    @Test fun versionConflict() {
        val note = service.createNote(user, CreateNoteRequest(title = "v1", body = "a"))
        service.updateNote(user, note.id, UpdateNoteRequest(body = "b", baseVersion = 1))
        val ex = assertFailsWith<ApiException> {
            service.updateNote(user, note.id, UpdateNoteRequest(body = "c", baseVersion = 1))
        }
        assertEquals("version_conflict", ex.code)
    }

    @Test fun versionsAndRestore() {
        val note = service.createNote(user, CreateNoteRequest(title = "t", body = "one"))
        service.updateNote(user, note.id, UpdateNoteRequest(body = "two"))
        val versions = service.versions(user, note.id)
        assertTrue(versions.size >= 2)
        val restored = service.restore(user, note.id, 1)
        assertEquals("one", restored.body)
    }

    @Test fun syncIncludesTombstones() {
        val note = service.createNote(user, CreateNoteRequest(title = "gone", body = "x"))
        service.deleteNote(user, note.id)
        val sync = service.sync(user, null, null)
        assertTrue(sync.notes.any { it.id == note.id && it.deleted })
    }
}

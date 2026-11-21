package com.exambase.backend.search

import com.exambase.backend.ApiException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SearchServiceTest {
    @Test
    fun privateNotesNeverLeak() {
        val repo = InMemorySearchRepository()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        val noteId = UUID.randomUUID()
        repo.rows += InMemorySearchRepository.Row(SearchType.NOTE, noteId, "Alice secret algebra", ownerId = alice)
        repo.rows += InMemorySearchRepository.Row(SearchType.SUBJECT, UUID.randomUUID(), "Algebra public")

        val svc = SearchService(repo)
        val aliceHits = svc.search(alice, "algebra", null, "20", null).items
        assertTrue(aliceHits.any { it.type == SearchType.NOTE && it.id == noteId.toString() })

        val bobHits = svc.search(bob, "algebra", null, "20", null).items
        assertTrue(bobHits.none { it.type == SearchType.NOTE })
        assertTrue(bobHits.any { it.type == SearchType.SUBJECT })
    }

    @Test
    fun unpublishedQuizOnlyVisibleToOwner() {
        val repo = InMemorySearchRepository()
        val owner = UUID.randomUUID()
        val other = UUID.randomUUID()
        val quizId = UUID.randomUUID()
        repo.rows += InMemorySearchRepository.Row(SearchType.QUIZ, quizId, "Private calculus quiz", ownerId = owner, published = false)

        val svc = SearchService(repo)
        assertTrue(svc.search(owner, "calculus", "quiz", null, null).items.any { it.id == quizId.toString() })
        assertTrue(svc.search(other, "calculus", "quiz", null, null).items.none { it.id == quizId.toString() })
    }

    @Test
    fun emptyQueryRejected() {
        val svc = SearchService(InMemorySearchRepository())
        val ex = assertFailsWith<ApiException> { svc.search(UUID.randomUUID(), "  ", null, null, null) }
        assertEquals("invalid_request", ex.code)
    }

    @Test
    fun recentSaved() {
        val repo = InMemorySearchRepository()
        val svc = SearchService(repo)
        val user = UUID.randomUUID()
        repo.rows += InMemorySearchRepository.Row(SearchType.SUBJECT, UUID.randomUUID(), "Biology")
        svc.search(user, "Biology", "subject", null, null)
        assertEquals(listOf("biology"), svc.recent(user).items)
    }
}

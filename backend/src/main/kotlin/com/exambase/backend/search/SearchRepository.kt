package com.exambase.backend.search

import java.util.UUID

interface SearchRepository {
    fun searchSubjects(q: String, limit: Int): List<SearchHit>
    fun searchLessons(q: String, limit: Int): List<SearchHit>
    /** Only published questions; never returns answer keys. */
    fun searchQuestions(q: String, limit: Int): List<SearchHit>
    /** Public + caller's own quiz sets. */
    fun searchQuizzes(userId: UUID, q: String, limit: Int): List<SearchHit>
    fun searchFlashcards(userId: UUID, q: String, limit: Int): List<SearchHit>
    /** Caller's notes only — never other users' notes. */
    fun searchNotes(userId: UUID, q: String, limit: Int): List<SearchHit>

    fun saveRecent(userId: UUID, query: String)
    fun listRecent(userId: UUID, limit: Int): List<String>
}

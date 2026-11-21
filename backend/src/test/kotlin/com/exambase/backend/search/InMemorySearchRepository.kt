package com.exambase.backend.search

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemorySearchRepository : SearchRepository {
    data class Row(val type: String, val id: UUID, val title: String, val ownerId: UUID? = null, val published: Boolean = true)

    val rows = mutableListOf<Row>()
    private val recent = ConcurrentHashMap<UUID, MutableList<String>>()

    override fun searchSubjects(q: String, limit: Int) =
        rows.filter { it.type == SearchType.SUBJECT && it.title.contains(q, true) }
            .take(limit)
            .map { SearchHit(it.type, it.id.toString(), it.title) }

    override fun searchLessons(q: String, limit: Int) =
        rows.filter { it.type == SearchType.LESSON && it.title.contains(q, true) }
            .take(limit)
            .map { SearchHit(it.type, it.id.toString(), it.title) }

    override fun searchQuestions(q: String, limit: Int) =
        rows.filter { it.type == SearchType.QUESTION && it.published && it.title.contains(q, true) }
            .take(limit)
            .map { SearchHit(it.type, it.id.toString(), it.title, snippet = it.title.take(80)) }

    override fun searchQuizzes(userId: UUID, q: String, limit: Int) =
        rows.filter {
            it.type == SearchType.QUIZ && it.title.contains(q, true) &&
                (it.published || it.ownerId == userId)
        }.take(limit).map {
            SearchHit(it.type, it.id.toString(), it.title, privateOwned = it.ownerId == userId && !it.published)
        }

    override fun searchFlashcards(userId: UUID, q: String, limit: Int) =
        rows.filter {
            it.type == SearchType.FLASHCARD && it.title.contains(q, true) &&
                (it.published || it.ownerId == userId)
        }.take(limit).map {
            SearchHit(it.type, it.id.toString(), it.title, privateOwned = it.ownerId == userId && !it.published)
        }

    override fun searchNotes(userId: UUID, q: String, limit: Int) =
        rows.filter { it.type == SearchType.NOTE && it.ownerId == userId && it.title.contains(q, true) }
            .take(limit)
            .map { SearchHit(it.type, it.id.toString(), it.title, privateOwned = true) }

    override fun saveRecent(userId: UUID, query: String) {
        val list = recent.getOrPut(userId) { mutableListOf() }
        list.remove(query)
        list.add(0, query)
        while (list.size > 10) list.removeLast()
    }

    override fun listRecent(userId: UUID, limit: Int): List<String> =
        recent[userId]?.take(limit) ?: emptyList()
}

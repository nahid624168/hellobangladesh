package com.exambase.backend.search

import com.exambase.backend.ApiException
import io.ktor.http.HttpStatusCode
import java.util.UUID

class SearchService(
    private val repo: SearchRepository,
) {
    companion object {
        const val MAX_Q = 120
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
        val SUGGESTIONS = listOf(
            "algebra", "biology", "practice", "quiz", "flashcards", "exam",
        )
    }

    fun search(
        userId: UUID,
        qRaw: String?,
        typesRaw: String?,
        limitRaw: String?,
        cursor: String?,
    ): SearchResponse {
        val q = qRaw?.trim().orEmpty()
        if (q.isEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Query is required.", mapOf("q" to "Required."))
        }
        if (q.length > MAX_Q) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Query is too long.", mapOf("q" to "At most $MAX_Q characters."))
        }
        val types = parseTypes(typesRaw)
        val limit = limitRaw?.toIntOrNull()?.coerceIn(1, MAX_LIMIT) ?: DEFAULT_LIMIT
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0

        // Per-type cap so one type cannot monopolize the page; then merge and page.
        val perType = (limit + types.size).coerceAtLeast(limit)
        val hits = mutableListOf<SearchHit>()
        if (SearchType.SUBJECT in types) hits += repo.searchSubjects(q, perType)
        if (SearchType.LESSON in types) hits += repo.searchLessons(q, perType)
        if (SearchType.QUESTION in types) hits += repo.searchQuestions(q, perType)
        if (SearchType.QUIZ in types) hits += repo.searchQuizzes(userId, q, perType)
        if (SearchType.FLASHCARD in types) hits += repo.searchFlashcards(userId, q, perType)
        if (SearchType.NOTE in types) hits += repo.searchNotes(userId, q, perType)

        // Stable order: type then title; page in memory for MVP.
        val sorted = hits.sortedWith(compareBy({ it.type }, { it.title.lowercase() }))
        val page = sorted.drop(offset).take(limit)
        val next = if (offset + limit < sorted.size) (offset + limit).toString() else null

        runCatching { repo.saveRecent(userId, q.lowercase()) }
        return SearchResponse(query = q, items = page, nextCursor = next, types = types.toList())
    }

    fun recent(userId: UUID): RecentSearchesResponse =
        RecentSearchesResponse(repo.listRecent(userId, 10))

    fun suggested(): SuggestedSearchesResponse = SuggestedSearchesResponse(SUGGESTIONS)

    private fun parseTypes(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return SearchType.all
        val parts = raw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val invalid = parts.filter { it !in SearchType.all }
        if (invalid.isNotEmpty()) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                "invalid_request",
                "Unknown type filter.",
                mapOf("types" to "Allowed: ${SearchType.all.joinToString()}"),
            )
        }
        return parts.toSet().ifEmpty { SearchType.all }
    }
}

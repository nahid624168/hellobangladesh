package com.exambase.backend.flashcards

import com.exambase.backend.ApiException
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Instant
import java.util.UUID

class FlashcardService(
    private val repo: FlashcardRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(userId: UUID, req: CreateFlashcardSetRequest): FlashcardSetDto {
        val title = req.title.trim()
        if (title.isEmpty() || title.length > 120) bad("title", "Title must be 1–120 characters.")
        val status = (req.status ?: FlashcardSetStatus.DRAFT).lowercase()
        if (status !in FlashcardSetStatus.all) bad("status", "Use draft, private or archived.")
        val lessonId = optionalUuid("lessonId", req.lessonId)
        val desc = req.description?.trim()?.takeIf { it.isNotEmpty() }
        if (desc != null && desc.length > 1000) bad("description", "At most 1000 characters.")
        return repo.createSet(userId, title, desc, lessonId, status)
    }

    fun list(userId: UUID, q: String?, status: String?, sort: String?, limitRaw: String?, cursor: String?): Page<FlashcardSetListItem> {
        val limit = Paging.limit(limitRaw)
        val offset = Paging.decode(cursor)
        val st = status?.lowercase()?.also {
            if (it !in FlashcardSetStatus.all) bad("status", "Use draft, private or archived.")
        }
        val chosen = when (sort) {
            "title", "cards", "recent", null -> sort ?: "recent"
            else -> bad("sort", "Use recent, title or cards.")
        }
        return repo.listSets(userId, q?.trim()?.takeIf { it.isNotEmpty() }, st, chosen, limit, offset)
    }

    fun get(userId: UUID, id: String): FlashcardSetDetail =
        repo.getSet(userId, pathId(id)) ?: notFound()

    fun update(userId: UUID, id: String, req: UpdateFlashcardSetRequest): FlashcardSetDto {
        req.title?.trim()?.let { if (it.isEmpty() || it.length > 120) bad("title", "Title must be 1–120 characters.") }
        req.status?.lowercase()?.let { if (it !in FlashcardSetStatus.all) bad("status", "Use draft, private or archived.") }
        req.description?.let { if (it.length > 1000) bad("description", "At most 1000 characters.") }
        return repo.updateSet(
            userId, pathId(id),
            title = req.title?.trim(),
            description = req.description,
            lessonId = optionalUuid("lessonId", req.lessonId),
            clearLesson = req.clearLesson,
            status = req.status?.lowercase(),
        ) ?: notFound()
    }

    fun delete(userId: UUID, id: String) {
        if (!repo.deleteSet(userId, pathId(id))) notFound()
    }

    fun duplicate(userId: UUID, id: String): FlashcardSetDto =
        repo.duplicateSet(userId, pathId(id)) ?: notFound()

    fun addCard(userId: UUID, setId: String, req: CreateFlashcardRequest): FlashcardDto {
        validateCardText(req.frontText, "frontText", 2000)
        validateCardText(req.backText, "backText", 4000)
        req.difficulty?.let { if (it !in setOf("easy", "medium", "hard")) bad("difficulty", "Use easy, medium or hard.") }
        return repo.addCard(userId, pathId(setId), req) ?: notFound()
    }

    fun updateCard(userId: UUID, cardId: String, req: UpdateFlashcardRequest): FlashcardDto {
        req.frontText?.let { validateCardText(it, "frontText", 2000) }
        req.backText?.let { validateCardText(it, "backText", 4000) }
        req.difficulty?.let { if (it !in setOf("easy", "medium", "hard")) bad("difficulty", "Use easy, medium or hard.") }
        return repo.updateCard(userId, pathId(cardId), req) ?: notFound()
    }

    fun deleteCard(userId: UUID, cardId: String) {
        if (!repo.deleteCard(userId, pathId(cardId))) notFound()
    }

    fun reorder(userId: UUID, setId: String, req: ReorderCardsRequest) {
        val ids = req.cardIds.map { pathId(it) }
        if (!repo.reorderCards(userId, pathId(setId), ids)) notFound()
    }

    fun due(userId: UUID, setId: String, limitRaw: String?): List<StudyCardDto> {
        val limit = (limitRaw?.toIntOrNull() ?: 20).coerceIn(1, 50)
        return repo.dueCards(userId, pathId(setId), limit, Instant.now(clock))
    }

    fun review(userId: UUID, cardId: String, req: ReviewCardRequest): ReviewResultDto {
        val rating = req.rating.trim().lowercase()
        if (rating !in ReviewRating.all) bad("rating", "Use again, hard, good or easy.")
        return repo.review(userId, pathId(cardId), rating, Instant.now(clock)) ?: notFound()
    }

    fun analytics(userId: UUID, setId: String): SetAnalyticsDto =
        repo.analytics(userId, pathId(setId), Instant.now(clock)) ?: notFound()

    private fun validateCardText(text: String, field: String, max: Int) {
        val t = text.trim()
        if (t.isEmpty() || t.length > max) bad(field, "Must be 1–$max characters.")
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

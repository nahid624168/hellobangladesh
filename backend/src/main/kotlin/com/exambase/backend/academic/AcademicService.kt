package com.exambase.backend.academic

import com.exambase.backend.ApiException
import io.ktor.http.HttpStatusCode
import java.util.UUID

class AcademicService(private val repo: AcademicRepository) {

    fun subjects(search: String?, sort: String?, featured: String?, limitRaw: String?, cursor: String?): Page<SubjectSummary> {
        val limit = Paging.limit(limitRaw)
        val offset = Paging.decode(cursor)
        val chosenSort = sort ?: SubjectQuery.SORT_ORDER
        if (chosenSort !in SubjectQuery.sorts) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Unknown sort.", mapOf("sort" to "Use order, title or newest."))
        }
        val text = search?.trim()?.takeIf { it.isNotEmpty() }
        if (text != null && text.length > MAX_SEARCH) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Search text is too long.", mapOf("q" to "At most $MAX_SEARCH characters."))
        }
        return repo.listSubjects(SubjectQuery(text, chosenSort, featured == "true"), limit, offset)
    }

    fun subject(id: String): SubjectDetail = repo.getSubject(uuid(id)) ?: notFound()
    fun category(id: String): CategoryDetail = repo.getCategory(uuid(id)) ?: notFound()
    fun subcategory(id: String, userId: UUID): SubcategoryDetail = repo.getSubcategory(uuid(id), userId) ?: notFound()
    fun lesson(id: String, userId: UUID): LessonDetail = repo.getLesson(uuid(id), userId) ?: notFound()

    fun recordView(id: String, userId: UUID) {
        if (!repo.recordView(userId, uuid(id))) notFound()
    }

    fun setBookmark(id: String, userId: UUID, bookmarked: Boolean) {
        if (!repo.setBookmark(userId, uuid(id), bookmarked)) notFound()
    }

    fun setProgress(id: String, userId: UUID, status: String) {
        if (status !in ProgressStatus.settable) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Unknown status.", mapOf("status" to "Use in_progress or completed."))
        }
        if (!repo.setProgress(userId, uuid(id), status)) notFound()
    }

    fun bookmarks(userId: UUID, limitRaw: String?, cursor: String?): Page<LessonSummary> =
        repo.listBookmarks(userId, Paging.limit(limitRaw), Paging.decode(cursor))

    fun recent(userId: UUID, limitRaw: String?): List<LessonSummary> =
        repo.listRecent(userId, Paging.limit(limitRaw ?: "10"))

    /** A malformed id is reported the same way as a missing one. */
    private fun uuid(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull() ?: notFound()

    private fun notFound(): Nothing = throw ApiException(HttpStatusCode.NotFound, "not_found", "Content not found.")

    private companion object {
        const val MAX_SEARCH = 100
    }
}

package com.exambase.backend.questions

import com.exambase.backend.ApiException
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import io.ktor.http.HttpStatusCode
import java.util.UUID

class QuestionService(private val repo: QuestionRepository) {

    @Suppress("LongParameterList")
    fun list(
        subjectId: String?, categoryId: String?, subcategoryId: String?, lessonId: String?,
        type: String?, difficulty: String?, tag: String?, search: String?, sort: String?,
        limitRaw: String?, cursor: String?,
    ): Page<QuestionSummary> {
        val limit = Paging.limit(limitRaw)
        val offset = Paging.decode(cursor)
        val chosenSort = sort ?: QuestionFilter.SORT_ORDER
        if (chosenSort !in QuestionFilter.sorts) bad("sort", "Use order, newest or difficulty.")
        if (type != null && type !in QuestionType.all) bad("type", "Use one of: ${QuestionType.all.sorted().joinToString(", ")}.")
        if (difficulty != null && difficulty !in Difficulty.all) bad("difficulty", "Use easy, medium or hard.")
        val tagSlug = tag?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (tagSlug != null && !TAG_SLUG.matches(tagSlug)) bad("tag", "Tags use lowercase letters, digits and hyphens.")
        val text = search?.trim()?.takeIf { it.isNotEmpty() }
        if (text != null && text.length > MAX_SEARCH) bad("q", "At most $MAX_SEARCH characters.")
        val filter = QuestionFilter(
            subjectId = filterId("subjectId", subjectId),
            categoryId = filterId("categoryId", categoryId),
            subcategoryId = filterId("subcategoryId", subcategoryId),
            lessonId = filterId("lessonId", lessonId),
            type = type,
            difficulty = difficulty,
            tag = tagSlug,
            search = text,
            sort = chosenSort,
        )
        return repo.listQuestions(filter, limit, offset)
    }

    fun detail(id: String): QuestionDetail = repo.getQuestion(pathId(id)) ?: notFound()

    fun tags(): List<TagDto> = repo.listTags()

    fun setBookmark(userId: UUID, questionId: String, bookmarked: Boolean) {
        if (!repo.setBookmark(userId, pathId(questionId), bookmarked)) notFound()
    }

    fun bookmarks(userId: UUID, limitRaw: String?, cursor: String?): Page<QuestionBookmarkItem> {
        val limit = Paging.limit(limitRaw)
        val offset = Paging.decode(cursor)
        return repo.listBookmarks(userId, limit, offset)
    }

    fun isBookmarked(userId: UUID, questionId: String): Boolean =
        repo.isBookmarked(userId, pathId(questionId))

    fun report(userId: UUID, questionId: String, req: ReportQuestionRequest): QuestionReportDto {
        val reason = req.reason.trim().lowercase()
        if (reason !in ReportReason.all) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                "invalid_request",
                "Invalid report.",
                mapOf("reason" to "Use one of: ${ReportReason.all.sorted().joinToString(", ")}."),
            )
        }
        val details = req.details.trim().take(2000)
        return repo.createReport(userId, pathId(questionId), reason, details)
            ?: notFound()
    }

    /** A malformed id in the path is reported the same way as a missing one (same rule as the academic module). */
    private fun pathId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull() ?: notFound()

    /** A malformed id in a query filter is the caller's mistake, so it is a 400 that names the field. */
    private fun filterId(field: String, raw: String?): UUID? {
        if (raw.isNullOrBlank()) return null
        return runCatching { UUID.fromString(raw.trim()) }.getOrNull() ?: bad(field, "Must be a valid id.")
    }

    private fun bad(field: String, message: String): Nothing =
        throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid filter.", mapOf(field to message))

    private fun notFound(): Nothing = throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not found.")

    private companion object {
        const val MAX_SEARCH = 100
        val TAG_SLUG = Regex("^[a-z0-9][a-z0-9-]{0,49}$")
    }
}

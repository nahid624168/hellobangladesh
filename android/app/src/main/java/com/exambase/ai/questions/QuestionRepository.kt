package com.exambase.ai.questions

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.ApiResult
import com.exambase.core.network.CheckAnswerRequest
import com.exambase.core.network.CheckAnswerResponse
import com.exambase.core.network.PracticeSummary
import com.exambase.core.network.QuestionApi
import com.exambase.core.network.PageResponse
import com.exambase.core.network.PracticeAttemptDto
import com.exambase.core.network.QuestionBookmarkItem
import com.exambase.core.network.QuestionDetail
import com.exambase.core.network.QuestionReportDto
import com.exambase.core.network.QuestionSummary
import com.exambase.core.network.ReportQuestionRequest

/** Thin layer over [QuestionApi]; every call refreshes an expired token once through [AuthRepository.authed]. */
class QuestionRepository(private val api: QuestionApi, private val auth: AuthRepository) : PracticeSource {

    override suspend fun questionIds(lessonId: String): ApiResult<List<String>> {
        val ids = ArrayList<String>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val after = cursor
            when (val r = auth.authed { api.questions(it, lessonId, PAGE_SIZE, after) }) {
                is ApiResult.Failure -> return r
                is ApiResult.Success -> {
                    r.value.items.mapTo(ids) { q -> q.id }
                    cursor = r.value.nextCursor
                    if (cursor == null) return ApiResult.Success(ids.distinct())
                }
            }
        }
        return ApiResult.Success(ids.distinct())
    }

    override suspend fun question(id: String): ApiResult<QuestionDetail> = auth.authed { api.question(it, id) }

    override suspend fun check(id: String, request: CheckAnswerRequest): ApiResult<CheckAnswerResponse> =
        auth.authed { api.checkAnswer(it, id, request) }

    suspend fun practiceSummary(lessonId: String): ApiResult<PracticeSummary> = auth.authed { api.practiceSummary(it, lessonId) }

    suspend fun search(
        q: String? = null,
        lessonId: String? = null,
        subjectId: String? = null,
        cursor: String? = null,
        limit: Int = 50,
    ): ApiResult<PageResponse<QuestionSummary>> = auth.authed {
        api.searchQuestions(it, q, lessonId, subjectId, limit, cursor)
    }

    suspend fun practiceHistory(lessonId: String? = null, cursor: String? = null, limit: Int = 50) =
        auth.authed { api.practiceHistory(it, lessonId, limit, cursor) }

    suspend fun setBookmark(id: String, bookmarked: Boolean) =
        auth.authed { api.setQuestionBookmark(it, id, bookmarked) }

    suspend fun bookmarks(cursor: String? = null, limit: Int = 50) =
        auth.authed { api.questionBookmarks(it, limit, cursor) }

    suspend fun report(id: String, reason: String, details: String = "") =
        auth.authed { api.reportQuestion(it, id, ReportQuestionRequest(reason, details)) }

    private companion object {
        const val PAGE_SIZE = 50
        /** A lesson with more than 200 questions practises the first 200; revisit when real content exists. */
        const val MAX_PAGES = 4
    }
}

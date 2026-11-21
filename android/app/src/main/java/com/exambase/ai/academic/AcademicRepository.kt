package com.exambase.ai.academic

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.AcademicApi
import com.exambase.core.network.ApiResult
import com.exambase.core.network.CategoryDetail
import com.exambase.core.network.LessonDetail
import com.exambase.core.network.LessonSummary
import com.exambase.core.network.MessageResponse
import com.exambase.core.network.PageResponse
import com.exambase.core.network.SubcategoryDetail
import com.exambase.core.network.SubjectDetail
import com.exambase.core.network.SubjectSummary

/**
 * Thin layer over [AcademicApi]. Every call goes through [AuthRepository.authed], so an expired access token is
 * refreshed once and the call retried. Offline caching arrives with the sync work in Step 22.
 */
class AcademicRepository(private val api: AcademicApi, private val auth: AuthRepository) {

    suspend fun subjects(search: String?, sort: String, featuredOnly: Boolean, cursor: String?, limit: Int = PAGE_SIZE): ApiResult<PageResponse<SubjectSummary>> =
        auth.authed { api.subjects(it, search?.trim()?.takeIf(String::isNotEmpty), sort, featuredOnly, limit, cursor) }

    suspend fun subject(id: String): ApiResult<SubjectDetail> = auth.authed { api.subject(it, id) }
    suspend fun category(id: String): ApiResult<CategoryDetail> = auth.authed { api.category(it, id) }
    suspend fun subcategory(id: String): ApiResult<SubcategoryDetail> = auth.authed { api.subcategory(it, id) }
    suspend fun lesson(id: String): ApiResult<LessonDetail> = auth.authed { api.lesson(it, id) }

    suspend fun recordView(lessonId: String): ApiResult<MessageResponse> = auth.authed { api.recordView(it, lessonId) }
    suspend fun setBookmark(lessonId: String, bookmarked: Boolean): ApiResult<MessageResponse> =
        auth.authed { api.setBookmark(it, lessonId, bookmarked) }
    suspend fun setProgress(lessonId: String, status: String): ApiResult<MessageResponse> =
        auth.authed { api.setProgress(it, lessonId, status) }

    suspend fun bookmarks(cursor: String?, limit: Int = PAGE_SIZE): ApiResult<PageResponse<LessonSummary>> =
        auth.authed { api.bookmarks(it, limit, cursor) }
    suspend fun recent(limit: Int = RECENT_SIZE): ApiResult<List<LessonSummary>> = auth.authed { api.recentLessons(it, limit) }

    companion object {
        const val PAGE_SIZE = 20
        const val RECENT_SIZE = 20
    }
}

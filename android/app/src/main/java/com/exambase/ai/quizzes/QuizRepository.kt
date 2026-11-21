package com.exambase.ai.quizzes

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.ApiResult
import com.exambase.core.network.CreateQuizSetRequest
import com.exambase.core.network.PageResponse
import com.exambase.core.network.QuizApi
import com.exambase.core.network.QuizAttemptSummary
import com.exambase.core.network.QuizAttemptView
import com.exambase.core.network.QuizResult
import com.exambase.core.network.QuizSet
import com.exambase.core.network.QuizSetDetail
import com.exambase.core.network.QuizSetListItem
import com.exambase.core.network.SetQuestionsRequest
import com.exambase.core.network.SubmitQuizRequest
import com.exambase.core.network.UpdateQuizSetRequest

/** What the quiz screens and view models need from the outside; tests supply a fake. */
interface QuizSource {
    suspend fun quizSets(
        sort: String,
        cursor: String?,
        limit: Int = QuizRepository.PAGE_SIZE,
        q: String? = null,
        status: String? = null,
        lessonId: String? = null,
    ): ApiResult<PageResponse<QuizSetListItem>>
    suspend fun quizSet(id: String): ApiResult<QuizSetDetail>
    suspend fun createQuizSet(req: CreateQuizSetRequest): ApiResult<QuizSet>
    suspend fun updateQuizSet(id: String, req: UpdateQuizSetRequest): ApiResult<QuizSet>
    suspend fun deleteQuizSet(id: String): ApiResult<Unit>
    suspend fun duplicateQuizSet(id: String): ApiResult<QuizSet>
    suspend fun setQuizQuestions(id: String, questionIds: List<String>): ApiResult<QuizSetDetail>
    suspend fun attempts(quizSetId: String, cursor: String?, limit: Int = QuizRepository.PAGE_SIZE): ApiResult<PageResponse<QuizAttemptSummary>>
    suspend fun startAttempt(quizSetId: String): ApiResult<QuizAttemptView>
    suspend fun attempt(attemptId: String): ApiResult<QuizAttemptView>
    suspend fun submit(attemptId: String, request: SubmitQuizRequest): ApiResult<QuizResult>
}

/** Thin layer over [QuizApi]; every call refreshes an expired token once through [AuthRepository.authed]. */
class QuizRepository(private val api: QuizApi, private val auth: AuthRepository) : QuizSource {

    override suspend fun quizSets(
        sort: String,
        cursor: String?,
        limit: Int,
        q: String?,
        status: String?,
        lessonId: String?,
    ) = auth.authed { api.quizSets(it, sort, limit, cursor, q, status, lessonId) }

    override suspend fun quizSet(id: String) = auth.authed { api.quizSet(it, id) }
    override suspend fun createQuizSet(req: CreateQuizSetRequest) = auth.authed { api.createQuizSet(it, req) }
    override suspend fun updateQuizSet(id: String, req: UpdateQuizSetRequest) = auth.authed { api.updateQuizSet(it, id, req) }
    override suspend fun deleteQuizSet(id: String) = auth.authed { api.deleteQuizSet(it, id) }
    override suspend fun duplicateQuizSet(id: String) = auth.authed { api.duplicateQuizSet(it, id) }
    override suspend fun setQuizQuestions(id: String, questionIds: List<String>) =
        auth.authed { api.setQuizQuestions(it, id, SetQuestionsRequest(questionIds)) }
    override suspend fun attempts(quizSetId: String, cursor: String?, limit: Int) = auth.authed { api.quizAttempts(it, quizSetId, limit, cursor) }
    override suspend fun startAttempt(quizSetId: String) = auth.authed { api.startQuizAttempt(it, quizSetId) }
    override suspend fun attempt(attemptId: String) = auth.authed { api.quizAttempt(it, attemptId) }
    override suspend fun submit(attemptId: String, request: SubmitQuizRequest) = auth.authed { api.submitQuizAttempt(it, attemptId, request) }

    companion object {
        const val PAGE_SIZE = 50
        const val MAX_QUESTIONS = 100
    }
}

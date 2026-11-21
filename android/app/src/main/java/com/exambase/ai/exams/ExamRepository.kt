package com.exambase.ai.exams

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.ApiResult
import com.exambase.core.network.ExamApi
import com.exambase.core.network.ExamDetail
import com.exambase.core.network.ExamResultDto
import com.exambase.core.network.ExamSummary
import com.exambase.core.network.PageResponse
import com.exambase.core.network.SaveExamAnswerRequest
import com.exambase.core.network.SaveExamAnswerResponse
import com.exambase.core.network.StartExamResponse

interface ExamSource {
    suspend fun exams(cursor: String?, limit: Int = PAGE_SIZE): ApiResult<PageResponse<ExamSummary>>
    suspend fun exam(id: String): ApiResult<ExamDetail>
    suspend fun start(id: String): ApiResult<StartExamResponse>
    suspend fun saveAnswer(attemptId: String, req: SaveExamAnswerRequest): ApiResult<SaveExamAnswerResponse>
    suspend fun submit(attemptId: String): ApiResult<ExamResultDto>
    suspend fun result(attemptId: String): ApiResult<ExamResultDto>

    companion object {
        const val PAGE_SIZE = 20
    }
}

class ExamRepository(
    private val api: ExamApi,
    private val auth: AuthRepository,
) : ExamSource {
    override suspend fun exams(cursor: String?, limit: Int) =
        auth.authed { api.exams(it, limit, cursor) }

    override suspend fun exam(id: String) = auth.authed { api.exam(it, id) }

    override suspend fun start(id: String) = auth.authed { api.startExam(it, id) }

    override suspend fun saveAnswer(attemptId: String, req: SaveExamAnswerRequest) =
        auth.authed { api.saveExamAnswer(it, attemptId, req) }

    override suspend fun submit(attemptId: String) = auth.authed { api.submitExam(it, attemptId) }

    override suspend fun result(attemptId: String) = auth.authed { api.examResult(it, attemptId) }
}

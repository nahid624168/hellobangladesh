package com.exambase.ai.ai

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.AiApi
import com.exambase.core.network.AiConsentDto
import com.exambase.core.network.AiQuotaDto
import com.exambase.core.network.AiRunRequest
import com.exambase.core.network.AiRunResponse
import com.exambase.core.network.AiConversationItemDto
import com.exambase.core.network.AiConversationDetailDto
import com.exambase.core.network.AiGeneratedQuizSetDto
import com.exambase.core.network.AiGeneratedQuizDetailDto
import com.exambase.core.network.AiGeneratedQuizCreateRequest
import com.exambase.core.network.PageResponse
import com.exambase.core.network.ApiResult

/** What the AI screens need from the server. A fake of this is all the tests need. */
private fun <T> unsupportedAiResult(): ApiResult<T> = ApiResult.Failure(
    com.exambase.core.network.ApiFailure(com.exambase.core.network.FailureKind.OTHER, "unsupported", "Not supported by this AI source."),
)

interface AiSource {
    suspend fun consent(): ApiResult<AiConsentDto>
    suspend fun accept(version: String): ApiResult<AiConsentDto>
    suspend fun revoke(): ApiResult<AiConsentDto>
    suspend fun quota(): ApiResult<AiQuotaDto>
    suspend fun run(task: String, text: String, language: String?, conversationId: String? = null): ApiResult<AiRunResponse>
    suspend fun conversations(): ApiResult<PageResponse<AiConversationItemDto>> = unsupportedAiResult()
    suspend fun conversation(id: String): ApiResult<AiConversationDetailDto> = unsupportedAiResult()
    suspend fun deleteConversation(id: String): ApiResult<Unit> = unsupportedAiResult()
    suspend fun generatedQuizSets(): ApiResult<List<AiGeneratedQuizSetDto>> = unsupportedAiResult()
    suspend fun createGeneratedQuiz(req: AiGeneratedQuizCreateRequest): ApiResult<AiGeneratedQuizDetailDto> = unsupportedAiResult()
    suspend fun generatedQuiz(id: String): ApiResult<AiGeneratedQuizDetailDto> = unsupportedAiResult()
    suspend fun deleteGeneratedQuiz(id: String): ApiResult<Unit> = unsupportedAiResult()
}

class AiRepository(
    private val api: AiApi,
    private val auth: AuthRepository,
) : AiSource {
    override suspend fun consent() = auth.authed { api.aiConsent(it) }
    override suspend fun accept(version: String) = auth.authed { api.acceptAiConsent(it, version) }
    override suspend fun revoke() = auth.authed { api.revokeAiConsent(it) }
    override suspend fun quota() = auth.authed { api.aiQuota(it) }
    override suspend fun run(task: String, text: String, language: String?, conversationId: String?) =
        auth.authed { api.aiRun(it, AiRunRequest(task = task, text = text, language = language, conversationId = conversationId)) }
    override suspend fun conversations() = auth.authed { api.aiConversations(it) }
    override suspend fun conversation(id: String) = auth.authed { api.aiConversation(it, id) }
    override suspend fun deleteConversation(id: String) = auth.authed { api.deleteAiConversation(it, id) }
    override suspend fun generatedQuizSets() = auth.authed { api.aiGeneratedQuizSets(it) }
    override suspend fun createGeneratedQuiz(req: AiGeneratedQuizCreateRequest) = auth.authed { api.createAiGeneratedQuiz(it, req) }
    override suspend fun generatedQuiz(id: String) = auth.authed { api.aiGeneratedQuiz(it, id) }
    override suspend fun deleteGeneratedQuiz(id: String) = auth.authed { api.deleteAiGeneratedQuiz(it, id) }
}

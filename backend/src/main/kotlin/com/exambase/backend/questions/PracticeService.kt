package com.exambase.backend.questions

import com.exambase.backend.ApiException
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import com.exambase.backend.auth.RateLimiter
import io.ktor.http.HttpStatusCode
import java.util.UUID

class PracticeService(private val repo: PracticeRepository, private val limiter: RateLimiter) {

    fun check(rawId: String, userId: UUID, request: CheckAnswerRequest): CheckAnswerResponse {
        val id = runCatching { UUID.fromString(rawId) }.getOrNull() ?: notFound()
        if (!limiter.tryAcquire("practice:$userId")) {
            throw ApiException(HttpStatusCode.TooManyRequests, "rate_limited", "Too many answers too quickly. Please wait a moment.")
        }
        val key = repo.answerKey(id) ?: notFound()
        val submission = Grader.validate(key, request)
        val graded = Grader.grade(key, submission)
        val attemptId = repo.recordAttempt(userId, key, submission, graded)
        return CheckAnswerResponse(
            attemptId = attemptId.toString(),
            result = graded.result,
            awardedMarks = graded.awardedMarks,
            correctOptionIds = key.correctOptionIds.map { it.toString() }.sorted(),
            acceptedAnswers = if (key.type == QuestionType.SHORT_ANSWER) key.acceptedAnswers else emptyList(),
            explanation = key.explanation,
            explanationBn = key.explanationBn,
            explanationImages = key.explanationImages,
            version = key.version,
        )
    }

    fun history(userId: UUID, lessonId: String?, questionId: String?, limitRaw: String?, cursor: String?): Page<PracticeAttemptDto> {
        val limit = Paging.limit(limitRaw)
        val offset = Paging.decode(cursor)
        return repo.history(userId, filterId("lessonId", lessonId), filterId("questionId", questionId), limit, offset)
    }

    fun summary(userId: UUID, lessonId: String?): PracticeSummary {
        val id = filterId("lessonId", lessonId) ?: throw ApiException(
            HttpStatusCode.BadRequest, "invalid_request", "Invalid filter.", mapOf("lessonId" to "lessonId is required."),
        )
        return repo.summary(userId, id)
    }

    private fun filterId(field: String, raw: String?): UUID? {
        if (raw.isNullOrBlank()) return null
        return runCatching { UUID.fromString(raw.trim()) }.getOrNull() ?: throw ApiException(
            HttpStatusCode.BadRequest, "invalid_request", "Invalid filter.", mapOf(field to "Must be a valid id."),
        )
    }

    private fun notFound(): Nothing = throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not found.")
}

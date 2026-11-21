package com.exambase.ai.quizzes

import com.exambase.core.network.ApiFailure
import com.exambase.core.network.ApiResult
import com.exambase.core.network.AttemptStatus
import com.exambase.core.network.FailureKind
import com.exambase.core.network.PageResponse
import com.exambase.core.network.QuizAttemptSummary
import com.exambase.core.network.QuizAttemptView
import com.exambase.core.network.QuizResult
import com.exambase.core.network.QuizSet
import com.exambase.core.network.QuizSetDetail
import com.exambase.core.network.QuizSetListItem
import com.exambase.core.network.QuizSettings
import com.exambase.core.network.QuizStatus
import com.exambase.core.network.SubmitQuizRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun attempt(id: String, status: String, percent: Double? = null, passed: Boolean? = null) =
    QuizAttemptSummary(attemptId = id, status = status, startedAt = "2026-11-21T08:00:00Z", percent = percent, passed = passed)

private class FakeQuizSource(
    var detail: ApiResult<QuizSetDetail> = ApiResult.Success(
        QuizSetDetail(QuizSet("s1", "Algebra", status = QuizStatus.PRIVATE, questionCount = 3, settings = QuizSettings(maxAttempts = 2)), listOf("a", "b", "c")),
    ),
    /** One entry per page, served in order; the cursor is the page number. */
    var pages: List<ApiResult<PageResponse<QuizAttemptSummary>>> = listOf(ApiResult.Success(PageResponse(emptyList()))),
) : QuizSource {
    var attemptCalls = 0
    override suspend fun quizSet(id: String) = detail
    override suspend fun attempts(quizSetId: String, cursor: String?, limit: Int): ApiResult<PageResponse<QuizAttemptSummary>> {
        attemptCalls++
        return pages[cursor?.toInt() ?: 0]
    }
    override suspend fun quizSets(
        sort: String,
        cursor: String?,
        limit: Int,
        q: String?,
        status: String?,
        lessonId: String?,
    ): ApiResult<PageResponse<QuizSetListItem>> = error("not used")
    override suspend fun createQuizSet(req: com.exambase.core.network.CreateQuizSetRequest) = error("not used")
    override suspend fun updateQuizSet(id: String, req: com.exambase.core.network.UpdateQuizSetRequest) = error("not used")
    override suspend fun deleteQuizSet(id: String) = error("not used")
    override suspend fun duplicateQuizSet(id: String) = error("not used")
    override suspend fun setQuizQuestions(id: String, questionIds: List<String>) = error("not used")
    override suspend fun startAttempt(quizSetId: String): ApiResult<QuizAttemptView> = error("not used")
    override suspend fun attempt(attemptId: String): ApiResult<QuizAttemptView> = error("not used")
    override suspend fun submit(attemptId: String, request: SubmitQuizRequest): ApiResult<QuizResult> = error("not used")
}

private fun failure(kind: FailureKind) = ApiResult.Failure(ApiFailure(kind, kind.name, kind.name))

class QuizDetailLoadTest {
    @Test fun loadsSetAndHistoryAndDerivesTheNumbersTheScreenShows() = runBlocking {
        val source = FakeQuizSource(
            pages = listOf(
                ApiResult.Success(
                    PageResponse(
                        listOf(
                            attempt("a3", AttemptStatus.IN_PROGRESS),
                            attempt("a2", AttemptStatus.SUBMITTED, 40.0, false),
                            attempt("a1", AttemptStatus.SUBMITTED, 75.5, true),
                        ),
                    ),
                ),
            ),
        )
        val data = (source.loadDetail("s1") as ApiResult.Success).value
        assertEquals("a3", data.running?.attemptId)
        assertEquals(2, data.finishedCount)
        assertEquals(75.5, data.bestPercent!!, 0.0001)
        assertEquals(Startability.RESUME, data.startability)
        assertEquals(0, data.attemptsLeft)
        assertFalse(data.attemptsTruncated)
    }

    @Test fun expiredAttemptsCountAsFinishedButNeverAsBest() = runBlocking {
        val source = FakeQuizSource(
            pages = listOf(ApiResult.Success(PageResponse(listOf(attempt("a2", AttemptStatus.EXPIRED, 0.0), attempt("a1", AttemptStatus.EXPIRED, 0.0))))),
        )
        val data = (source.loadDetail("s1") as ApiResult.Success).value
        assertEquals(2, data.finishedCount)
        assertEquals(Startability.LIMIT_REACHED, data.startability)
    }

    @Test fun noAttemptsMeansNoBestAndReadyToStart() = runBlocking {
        val data = (FakeQuizSource().loadDetail("s1") as ApiResult.Success).value
        assertNull(data.bestPercent)
        assertNull(data.running)
        assertEquals(Startability.READY, data.startability)
        assertEquals(2, data.attemptsLeft)
    }

    @Test fun followsCursorsAndDropsRepeatedRows() = runBlocking {
        val source = FakeQuizSource(
            pages = listOf(
                ApiResult.Success(PageResponse(listOf(attempt("a3", AttemptStatus.SUBMITTED, 10.0)), nextCursor = "1")),
                ApiResult.Success(PageResponse(listOf(attempt("a3", AttemptStatus.SUBMITTED, 10.0), attempt("a2", AttemptStatus.SUBMITTED, 20.0)))),
            ),
        )
        val data = (source.loadDetail("s1") as ApiResult.Success).value
        assertEquals(listOf("a3", "a2"), data.attempts.map { it.attemptId })
        assertEquals(2, source.attemptCalls)
    }

    @Test fun stopsAtTheHistoryPageLimitAndSaysSo() = runBlocking {
        val endless = (0 until 5).map { n -> ApiResult.Success(PageResponse(listOf(attempt("a$n", AttemptStatus.SUBMITTED, 1.0)), nextCursor = "${n + 1}")) }
        val source = FakeQuizSource(pages = endless)
        val data = (source.loadDetail("s1") as ApiResult.Success).value
        assertEquals(QuizRules.MAX_HISTORY_PAGES, source.attemptCalls)
        assertTrue(data.attemptsTruncated)
        assertEquals(QuizRules.MAX_HISTORY_PAGES, data.attempts.size)
    }

    @Test fun aFailedSetLoadFailsWholeAndSkipsTheHistory() = runBlocking {
        val source = FakeQuizSource(detail = failure(FailureKind.NETWORK))
        val result = source.loadDetail("s1")
        assertEquals(FailureKind.NETWORK, (result as ApiResult.Failure).failure.kind)
        assertEquals(0, source.attemptCalls)
    }

    @Test fun aFailedHistoryPageFailsWholeSoTheCountIsNeverWrong() = runBlocking {
        val source = FakeQuizSource(
            pages = listOf(
                ApiResult.Success(PageResponse(listOf(attempt("a1", AttemptStatus.SUBMITTED, 1.0)), nextCursor = "1")),
                failure(FailureKind.UNAUTHORIZED),
            ),
        )
        val result = source.loadDetail("s1")
        assertEquals(FailureKind.UNAUTHORIZED, (result as ApiResult.Failure).failure.kind)
    }
}

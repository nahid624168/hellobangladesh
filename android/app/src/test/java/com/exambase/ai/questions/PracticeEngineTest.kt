package com.exambase.ai.questions

import com.exambase.core.network.ApiFailure
import com.exambase.core.network.ApiResult
import com.exambase.core.network.CheckAnswerRequest
import com.exambase.core.network.CheckAnswerResponse
import com.exambase.core.network.CheckResults
import com.exambase.core.network.FailureKind
import com.exambase.core.network.OptionDto
import com.exambase.core.network.QuestionDetail
import com.exambase.core.network.QuestionSummary
import com.exambase.core.network.QuestionTypes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSource : PracticeSource {
    var ids: ApiResult<List<String>> = ApiResult.Success(emptyList())
    val details = mutableMapOf<String, ApiResult<QuestionDetail>>()
    var checkResult: (String, CheckAnswerRequest) -> ApiResult<CheckAnswerResponse> = { _, _ -> ok(CheckResults.CORRECT) }
    val checked = mutableListOf<Pair<String, CheckAnswerRequest>>()

    override suspend fun questionIds(lessonId: String): ApiResult<List<String>> = ids
    override suspend fun question(id: String): ApiResult<QuestionDetail> = details[id] ?: failure(FailureKind.OTHER, "not_found")
    override suspend fun check(id: String, request: CheckAnswerRequest): ApiResult<CheckAnswerResponse> {
        checked += id to request
        return checkResult(id, request)
    }
}

private fun ok(result: String) = ApiResult.Success(CheckAnswerResponse(attemptId = "x", result = result, correctOptionIds = listOf("o1"), explanation = "why"))
private fun failure(kind: FailureKind, message: String = "msg") = ApiResult.Failure(ApiFailure(kind, "code", message))
private fun detail(id: String, type: String = QuestionTypes.SINGLE_CHOICE) = ApiResult.Success(
    QuestionDetail(
        question = QuestionSummary(id = id, lessonId = "l", type = type, text = "Q $id"),
        options = if (type == QuestionTypes.SHORT_ANSWER || type == QuestionTypes.WRITTEN) emptyList() else listOf(OptionDto("o1", "A"), OptionDto("o2", "B")),
    ),
)

class PracticeEngineTest {
    private fun engine(source: FakeSource) = PracticeEngine(source, "lesson")
    private fun PracticeEngine.step() = state.value.step

    @Test fun noQuestionsShowsEmpty() = runBlocking {
        val e = engine(FakeSource())
        e.start()
        assertEquals(PracticeStep.Empty, e.step())
    }

    @Test fun listFailureShowsErrorAndRetryRecovers() = runBlocking {
        val s = FakeSource().apply { ids = failure(FailureKind.NETWORK) }
        val e = engine(s)
        e.start()
        assertTrue((e.step() as PracticeStep.Error).error.network)
        s.ids = ApiResult.Success(listOf("q1")); s.details["q1"] = detail("q1")
        e.retry()
        assertTrue(e.step() is PracticeStep.Asking)
    }

    @Test fun fullRunTalliesAndFinishes() = runBlocking {
        val s = FakeSource().apply {
            ids = ApiResult.Success(listOf("q1", "q2")); details["q1"] = detail("q1"); details["q2"] = detail("q2")
            checkResult = { id, _ -> ok(if (id == "q1") CheckResults.CORRECT else CheckResults.INCORRECT) }
        }
        val e = engine(s)
        e.start()
        assertEquals(1, e.state.value.position); assertEquals(2, e.state.value.total)
        e.select("o1"); e.submit()
        val first = e.step() as PracticeStep.Feedback
        assertEquals(false, first.isLast)
        e.next()
        assertEquals(2, e.state.value.position)
        e.select("o2"); e.submit()
        assertEquals(true, (e.step() as PracticeStep.Feedback).isLast)
        e.next()
        assertEquals(PracticeStep.Done(Tally(correct = 1, incorrect = 1)), e.step())
    }

    @Test fun submitWithoutAValidAnswerDoesNothing() = runBlocking {
        val s = FakeSource().apply { ids = ApiResult.Success(listOf("q1")); details["q1"] = detail("q1") }
        val e = engine(s)
        e.start()
        e.submit()
        assertTrue(s.checked.isEmpty())
        assertTrue(e.step() is PracticeStep.Asking)
    }

    @Test fun nextIsIgnoredBeforeAnAnswerIsChecked() = runBlocking {
        val s = FakeSource().apply { ids = ApiResult.Success(listOf("q1", "q2")); details["q1"] = detail("q1"); details["q2"] = detail("q2") }
        val e = engine(s)
        e.start()
        e.next()
        assertEquals(1, e.state.value.position)
    }

    @Test fun unpublishedQuestionIsSkipped() = runBlocking {
        val s = FakeSource().apply { ids = ApiResult.Success(listOf("gone", "q2")); details["q2"] = detail("q2") }
        val e = engine(s)
        e.start()
        assertEquals("q2", (e.step() as PracticeStep.Asking).detail.question.id)
        assertEquals(1, e.state.value.total)
    }

    @Test fun allQuestionsSkippedShowsEmpty() = runBlocking {
        val e = engine(FakeSource().apply { ids = ApiResult.Success(listOf("gone")) })
        e.start()
        assertEquals(PracticeStep.Empty, e.step())
    }

    @Test fun networkFailureOnSubmitKeepsTheAnswerAndAllowsRetry() = runBlocking {
        var offline = true
        val s = FakeSource().apply {
            ids = ApiResult.Success(listOf("q1")); details["q1"] = detail("q1")
            checkResult = { _, _ -> if (offline) failure(FailureKind.NETWORK) else ok(CheckResults.CORRECT) }
        }
        val e = engine(s)
        e.start(); e.select("o2"); e.submit()
        val asking = e.step() as PracticeStep.Asking
        assertEquals(Problem.Network, asking.problem)
        assertEquals(setOf("o2"), asking.selected)
        assertEquals(false, asking.submitting)
        offline = false
        e.submit()
        assertTrue(e.step() is PracticeStep.Feedback)
    }

    @Test fun serverMessageIsShownOnValidationFailure() = runBlocking {
        val s = FakeSource().apply {
            ids = ApiResult.Success(listOf("q1")); details["q1"] = detail("q1")
            checkResult = { _, _ -> failure(FailureKind.RATE_LIMITED, "Slow down") }
        }
        val e = engine(s)
        e.start(); e.select("o1"); e.submit()
        assertEquals(Problem.Message("Slow down"), (e.step() as PracticeStep.Asking).problem)
    }

    @Test fun expiredSessionLeavesPractice() = runBlocking {
        val s = FakeSource().apply {
            ids = ApiResult.Success(listOf("q1")); details["q1"] = detail("q1")
            checkResult = { _, _ -> failure(FailureKind.UNAUTHORIZED) }
        }
        val e = engine(s)
        e.start(); e.select("o1"); e.submit()
        assertEquals(PracticeStep.SignedOut, e.step())
    }

    @Test fun requestsMatchTheQuestionType() = runBlocking {
        val s = FakeSource().apply {
            ids = ApiResult.Success(listOf("q1", "q2")); details["q1"] = detail("q1"); details["q2"] = detail("q2", QuestionTypes.SHORT_ANSWER)
        }
        val e = engine(s)
        e.start(); e.select("o1"); e.submit(); e.next()
        e.onText("  Dhaka "); e.submit()
        assertEquals(listOf("o1"), s.checked[0].second.selectedOptionIds)
        assertEquals("Dhaka", s.checked[1].second.answerText)
        assertTrue(s.checked[1].second.selectedOptionIds.isEmpty())
    }

    @Test fun restartResetsTheRun() = runBlocking {
        val s = FakeSource().apply { ids = ApiResult.Success(listOf("q1")); details["q1"] = detail("q1") }
        val e = engine(s)
        e.start(); e.select("o1"); e.submit(); e.next()
        assertEquals(PracticeStep.Done(Tally(correct = 1)), e.step())
        e.start()
        assertTrue(e.step() is PracticeStep.Asking)
        e.select("o1"); e.submit(); e.next()
        assertEquals(PracticeStep.Done(Tally(correct = 1)), e.step())
    }
}

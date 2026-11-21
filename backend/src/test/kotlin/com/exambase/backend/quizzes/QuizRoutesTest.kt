package com.exambase.backend.quizzes

import com.exambase.backend.AppConfig
import com.exambase.backend.AppServices
import com.exambase.backend.academic.AcademicService
import com.exambase.backend.academic.InMemoryAcademicRepository
import com.exambase.backend.auth.AuthService
import com.exambase.backend.auth.CapturingEmailSender
import com.exambase.backend.auth.InMemoryEmailCodeRepository
import com.exambase.backend.auth.InMemorySessionRepository
import com.exambase.backend.auth.InMemoryUserRepository
import com.exambase.backend.auth.JwtService
import com.exambase.backend.auth.PasswordHasher
import com.exambase.backend.auth.RateLimiter
import com.exambase.backend.module
import com.exambase.backend.questions.InMemoryQuestionRepository
import com.exambase.backend.questions.QuestionService
import com.exambase.backend.questions.QuestionType
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutableClock(private var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun advance(d: Duration) { now = now.plus(d) }
}

class QuizRoutesTest {
    private val academic = InMemoryAcademicRepository()
    private val bank = InMemoryQuestionRepository(academic)
    private val quizRepo = InMemoryQuizRepository(academic, bank)
    private val clock = MutableClock(Instant.parse("2026-01-01T10:00:00Z"))

    private val math = academic.subject("Mathematics")
    private val algebra = academic.category(math, "Algebra")
    private val equations = academic.subcategory(algebra, "Equations")
    private val linear = academic.lesson(equations, "Linear equations")
    private val draftLesson = academic.lesson(equations, "Draft lesson", status = "draft")

    private val q1 = bank.question(linear, "Solve x + 2 = 5", order = 1, marks = 2.0, negative = 0.5, explanation = "SECRET-EXPLANATION-1")
    private val q2 = bank.question(linear, "Select all primes", QuestionType.MULTIPLE_SELECT, order = 2, marks = 2.0)
    private val q3 = bank.question(linear, "Capital of Bangladesh?", QuestionType.SHORT_ANSWER, order = 3, answerKeys = listOf("Dhaka"))
    private val draftQuestion = bank.question(linear, "HIDDEN draft", status = "draft")
    private val hiddenLessonQuestion = bank.question(draftLesson, "HIDDEN in draft lesson")

    private fun correct(q: InMemoryQuestionRepository.Q) = q.options.first { it.correct }.id
    private fun wrong(q: InMemoryQuestionRepository.Q) = q.options.first { !it.correct }.id

    private suspend fun ApplicationTestBuilder.setup(limit: Int = 1000): Pair<String, String> {
        val sessions = InMemorySessionRepository()
        val jwt = JwtService("test-secret-test-secret-test-secret-123", "exambase-test", Duration.ofMinutes(15))
        val auth = AuthService(
            InMemoryUserRepository(), sessions, InMemoryEmailCodeRepository(),
            PasswordHasher(cost = 4), jwt, CapturingEmailSender(), Duration.ofDays(30),
        )
        val services = AppServices(
            auth, jwt, sessions, RateLimiter(1000, Duration.ofMinutes(1)),
            academic = AcademicService(academic), questions = QuestionService(bank),
            quizzes = QuizService(quizRepo, bank, RateLimiter(limit, Duration.ofMinutes(1)), clock, Random(1)),
        )
        application { module(AppConfig.forTests(), null, services) }
        suspend fun register(email: String): String {
            val r = client.post("/api/v1/auth/register") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"email":"$email","password":"study2026","displayName":"Rafi"}""")
            }
            return Json.parseToJsonElement(r.bodyAsText()).jsonObject.getValue("accessToken").jsonPrimitive.content
        }
        return register("a@b.com") to register("c@d.com")
    }

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonElement.o() = jsonObject

    private suspend fun ApplicationTestBuilder.get(path: String, token: String?): HttpResponse =
        client.get("/api/v1$path") { token?.let { header(HttpHeaders.Authorization, "Bearer $it") } }

    private suspend fun ApplicationTestBuilder.send(method: String, path: String, token: String?, body: String? = null): HttpResponse {
        val build: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            if (body != null) { header(HttpHeaders.ContentType, ContentType.Application.Json.toString()); setBody(body) }
        }
        return when (method) {
            "POST" -> client.post("/api/v1$path", build)
            "PUT" -> client.put("/api/v1$path", build)
            "PATCH" -> client.patch("/api/v1$path", build)
            else -> client.delete("/api/v1$path", build)
        }
    }

    private suspend fun ApplicationTestBuilder.createSet(token: String, body: String = """{"title":"Algebra drill","status":"private"}"""): String {
        val r = send("POST", "/quiz-sets", token, body)
        assertEquals(HttpStatusCode.Created, r.status)
        return obj(r.bodyAsText()).str("id")
    }

    private suspend fun ApplicationTestBuilder.fillSet(token: String, setId: String, vararg qs: InMemoryQuestionRepository.Q) {
        val ids = qs.joinToString(",") { "\"${it.id}\"" }
        assertEquals(HttpStatusCode.OK, send("PUT", "/quiz-sets/$setId/questions", token, """{"questionIds":[$ids]}""").status)
    }

    private fun answer(q: InMemoryQuestionRepository.Q, vararg ids: UUID) =
        """{"questionId":"${q.id}","selectedOptionIds":[${ids.joinToString(",") { "\"$it\"" }}]}"""

    @Test fun everyQuizEndpointRequiresSignIn() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, get("/quiz-sets", null).status)
        assertEquals(HttpStatusCode.Unauthorized, send("POST", "/quiz-sets", null, """{"title":"x"}""").status)
        assertEquals(HttpStatusCode.Unauthorized, send("POST", "/quiz-attempts/${UUID.randomUUID()}/submit", null, """{"answers":[]}""").status)
    }

    @Test fun createdSetsStaySeparateAndSearchable() = testApplication {
        val (t, _) = setup()
        val a = createSet(t, """{"title":"Algebra drill","status":"private"}""")
        val b = createSet(t, """{"title":"Geometry","description":"Shapes"}""")
        val list = obj(get("/quiz-sets", t).bodyAsText()).getValue("items").jsonArray
        assertEquals(2, list.size)
        assertEquals(setOf(a, b), list.map { it.o().getValue("quizSet").o().str("id") }.toSet())
        val found = obj(get("/quiz-sets?q=geo", t).bodyAsText()).getValue("items").jsonArray
        assertEquals(1, found.size)
        assertEquals("draft", found[0].o().getValue("quizSet").o().str("status"))
    }

    @Test fun otherUsersCannotSeeChangeOrPlayMySet() = testApplication {
        val (mine, other) = setup()
        val id = createSet(mine)
        fillSet(mine, id, q1)
        assertEquals(0, obj(get("/quiz-sets", other).bodyAsText()).getValue("items").jsonArray.size)
        assertEquals(HttpStatusCode.NotFound, get("/quiz-sets/$id", other).status)
        assertEquals(HttpStatusCode.NotFound, send("PATCH", "/quiz-sets/$id", other, """{"title":"hijack"}""").status)
        assertEquals(HttpStatusCode.NotFound, send("DELETE", "/quiz-sets/$id", other).status)
        assertEquals(HttpStatusCode.NotFound, send("POST", "/quiz-sets/$id/attempts", other).status)
        assertEquals(HttpStatusCode.NotFound, send("POST", "/quiz-sets/$id/duplicate", other).status)
        val attempt = obj(send("POST", "/quiz-sets/$id/attempts", mine).bodyAsText()).str("attemptId")
        assertEquals(HttpStatusCode.NotFound, get("/quiz-attempts/$attempt", other).status)
        assertEquals(HttpStatusCode.NotFound, send("POST", "/quiz-attempts/$attempt/submit", other, """{"answers":[]}""").status)
    }

    @Test fun validationNamesTheField() = testApplication {
        val (t, _) = setup()
        val r = send("POST", "/quiz-sets", t, """{"title":"  "}""")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.bodyAsText().contains("title"))
        assertEquals(HttpStatusCode.BadRequest, send("POST", "/quiz-sets", t, """{"title":"x","settings":{"timeLimitSeconds":5}}""").status)
        assertEquals(HttpStatusCode.BadRequest, send("POST", "/quiz-sets", t, """{"title":"x","status":"published"}""").status)
        assertEquals(HttpStatusCode.BadRequest, send("POST", "/quiz-sets", t, """{"title":"x","lessonId":"${draftLesson.id}"}""").status)
    }

    @Test fun onlyVisibleQuestionsCanBeAdded() = testApplication {
        val (t, _) = setup()
        val id = createSet(t)
        for (q in listOf(draftQuestion, hiddenLessonQuestion)) {
            val r = send("PUT", "/quiz-sets/$id/questions", t, """{"questionIds":["${q.id}"]}""")
            assertEquals(HttpStatusCode.BadRequest, r.status)
        }
        assertEquals(HttpStatusCode.BadRequest, send("PUT", "/quiz-sets/$id/questions", t, """{"questionIds":["${q1.id}","${q1.id}"]}""").status)
        fillSet(t, id, q2, q1)
        val detail = obj(get("/quiz-sets/$id", t).bodyAsText())
        assertEquals(listOf(q2.id.toString(), q1.id.toString()), detail.getValue("questionIds").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun startedAttemptNeverContainsAnswersOrExplanations() = testApplication {
        val (t, _) = setup()
        val id = createSet(t)
        fillSet(t, id, q1, q2, q3)
        val r = send("POST", "/quiz-sets/$id/attempts", t)
        assertEquals(HttpStatusCode.OK, r.status)
        val text = r.bodyAsText()
        assertFalse(text.contains("SECRET-EXPLANATION"))
        assertFalse(text.contains("isCorrect"))
        assertFalse(text.contains("correctOptionIds"))
        assertFalse(text.contains("Dhaka"))
        assertEquals(3, obj(text).getValue("questions").jsonArray.size)
    }

    @Test fun draftOrEmptyOrArchivedSetsCannotBePlayed() = testApplication {
        val (t, _) = setup()
        val draft = createSet(t, """{"title":"Draft"}""")
        fillSet(t, draft, q1)
        assertEquals("not_playable", obj(send("POST", "/quiz-sets/$draft/attempts", t).bodyAsText()).getValue("error").o().str("code"))
        val empty = createSet(t)
        assertEquals("empty_quiz", obj(send("POST", "/quiz-sets/$empty/attempts", t).bodyAsText()).getValue("error").o().str("code"))
        val archived = createSet(t, """{"title":"Old","status":"archived"}""")
        fillSet(t, archived, q1)
        assertEquals(HttpStatusCode.Conflict, send("POST", "/quiz-sets/$archived/attempts", t).status)
    }

    @Test fun serverScoresTheSubmissionAndRevealsReview() = testApplication {
        val (t, _) = setup()
        val id = createSet(t, """{"title":"Mixed","status":"private","settings":{"passingPercent":50}}""")
        fillSet(t, id, q1, q2, q3)
        val attempt = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText()).str("attemptId")
        val both = q2.options.filter { it.correct }.map { it.id }.toTypedArray()
        val body = """{"answers":[${answer(q1, correct(q1))},${answer(q2, *both)},{"questionId":"${q3.id}","answerText":" dhaka "}]}"""
        val r = send("POST", "/quiz-attempts/$attempt/submit", t, body)
        assertEquals(HttpStatusCode.OK, r.status)
        val res = obj(r.bodyAsText())
        assertEquals("submitted", res.str("status"))
        assertEquals(5.0, res.str("score").toDouble())    // 2 + 2 + 1
        assertEquals(5.0, res.str("maxScore").toDouble())
        assertEquals(100.0, res.str("percent").toDouble())
        assertEquals("true", res.str("passed"))
        assertTrue(r.bodyAsText().contains("SECRET-EXPLANATION-1"))
    }

    @Test fun wrongAndBlankAnswersAreMarkedAsPracticeRulesSay() = testApplication {
        val (t, _) = setup()
        val id = createSet(t)
        fillSet(t, id, q1, q2)
        val attempt = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText()).str("attemptId")
        val res = obj(send("POST", "/quiz-attempts/$attempt/submit", t, """{"answers":[${answer(q1, wrong(q1))}]}""").bodyAsText())
        assertEquals(0.0, res.str("score").toDouble())   // -0.5 clamps to 0
        val results = res.getValue("questions").jsonArray.map { it.o().str("result") }
        assertEquals(listOf("incorrect", "unanswered"), results)
    }

    @Test fun submittingTwiceReturnsTheSameStoredResult() = testApplication {
        val (t, _) = setup()
        val id = createSet(t)
        fillSet(t, id, q1)
        val attempt = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText()).str("attemptId")
        val first = send("POST", "/quiz-attempts/$attempt/submit", t, """{"answers":[${answer(q1, correct(q1))}]}""")
        // A retry with different answers must not change anything.
        val second = send("POST", "/quiz-attempts/$attempt/submit", t, """{"answers":[${answer(q1, wrong(q1))}]}""")
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(obj(first.bodyAsText()).str("score"), obj(second.bodyAsText()).str("score"))
        assertEquals(1, quizRepo.attempts.size)
    }

    @Test fun badAnswersAreRejectedAndNothingIsRecorded() = testApplication {
        val (t, _) = setup()
        val id = createSet(t)
        fillSet(t, id, q1)
        val attempt = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText()).str("attemptId")
        val stranger = """{"answers":[${answer(q2, correct(q2))}]}"""
        assertEquals(HttpStatusCode.BadRequest, send("POST", "/quiz-attempts/$attempt/submit", t, stranger).status)
        val twice = """{"answers":[${answer(q1, correct(q1))},${answer(q1, correct(q1))}]}"""
        assertEquals(HttpStatusCode.BadRequest, send("POST", "/quiz-attempts/$attempt/submit", t, twice).status)
        val foreignOption = """{"answers":[${answer(q1, UUID.randomUUID())}]}"""
        val r = send("POST", "/quiz-attempts/$attempt/submit", t, foreignOption)
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.bodyAsText().contains("answers[0].selectedOptionIds"))
        assertEquals(AttemptStatus.IN_PROGRESS, quizRepo.attempts.single().status)
    }

    @Test fun startingAgainResumesTheSameAttempt() = testApplication {
        val (t, _) = setup()
        val id = createSet(t, """{"title":"Timed","status":"private","settings":{"timeLimitSeconds":60}}""")
        fillSet(t, id, q1)
        val first = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText())
        clock.advance(Duration.ofSeconds(20))
        val second = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText())
        assertEquals(first.str("attemptId"), second.str("attemptId"))
        assertEquals(first.str("deadlineAt"), second.str("deadlineAt"))   // the clock does not restart
        assertEquals(1, quizRepo.attempts.size)
    }

    @Test fun lateSubmissionIsRejectedAndClosedWithZero() = testApplication {
        val (t, _) = setup()
        val id = createSet(t, """{"title":"Timed","status":"private","settings":{"timeLimitSeconds":60}}""")
        fillSet(t, id, q1)
        val attempt = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText()).str("attemptId")
        clock.advance(Duration.ofSeconds(125))   // 60 s limit + 10 s grace = 70 s; this is well past it
        val late = send("POST", "/quiz-attempts/$attempt/submit", t, """{"answers":[${answer(q1, correct(q1))}]}""")
        assertEquals(HttpStatusCode.Conflict, late.status)
        assertEquals("attempt_expired", obj(late.bodyAsText()).getValue("error").o().str("code"))
        val view = obj(get("/quiz-attempts/$attempt", t).bodyAsText())
        assertEquals("expired", view.str("status"))
        assertEquals(0.0, view.getValue("result").o().str("score").toDouble())
    }

    @Test fun submissionInsideTheGraceWindowIsAccepted() = testApplication {
        val (t, _) = setup()
        val id = createSet(t, """{"title":"Timed","status":"private","settings":{"timeLimitSeconds":60}}""")
        fillSet(t, id, q1)
        val attempt = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText()).str("attemptId")
        clock.advance(Duration.ofSeconds(65))
        val r = send("POST", "/quiz-attempts/$attempt/submit", t, """{"answers":[${answer(q1, correct(q1))}]}""")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("submitted", obj(r.bodyAsText()).str("status"))
    }

    @Test fun attemptLimitIsEnforcedOnTheServer() = testApplication {
        val (t, _) = setup()
        val id = createSet(t, """{"title":"Once","status":"private","settings":{"maxAttempts":1}}""")
        fillSet(t, id, q1)
        val attempt = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText()).str("attemptId")
        send("POST", "/quiz-attempts/$attempt/submit", t, """{"answers":[]}""")
        val again = send("POST", "/quiz-sets/$id/attempts", t)
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("attempt_limit_reached", obj(again.bodyAsText()).getValue("error").o().str("code"))
    }

    @Test fun hidingExplanationsRemovesTheReview() = testApplication {
        val (t, _) = setup()
        val id = createSet(t, """{"title":"Strict","status":"private","settings":{"showExplanations":false}}""")
        fillSet(t, id, q1)
        val attempt = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText()).str("attemptId")
        val r = send("POST", "/quiz-attempts/$attempt/submit", t, """{"answers":[${answer(q1, wrong(q1))}]}""")
        assertFalse(r.bodyAsText().contains("SECRET-EXPLANATION"))
        assertFalse(r.bodyAsText().contains("correctOptionIds\":[\""))
        assertNull(obj(r.bodyAsText()).getValue("questions").jsonArray[0].o()["review"]?.takeIf { it.toString() != "null" })
    }

    @Test fun duplicateCopiesQuestionsAsADraftWithItsOwnIdentity() = testApplication {
        val (t, _) = setup()
        val id = createSet(t)
        fillSet(t, id, q1, q2)
        val r = send("POST", "/quiz-sets/$id/duplicate", t)
        assertEquals(HttpStatusCode.Created, r.status)
        val copy = obj(r.bodyAsText())
        assertNotEquals(id, copy.str("id"))
        assertEquals("Copy of Algebra drill", copy.str("title"))
        assertEquals("draft", copy.str("status"))
        assertEquals(2, copy.str("questionCount").toInt())
    }

    @Test fun deletedSetsDisappearButHistoryIsKept() = testApplication {
        val (t, _) = setup()
        val id = createSet(t)
        fillSet(t, id, q1)
        val attempt = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText()).str("attemptId")
        send("POST", "/quiz-attempts/$attempt/submit", t, """{"answers":[]}""")
        assertEquals(HttpStatusCode.NoContent, send("DELETE", "/quiz-sets/$id", t).status)
        assertEquals(HttpStatusCode.NotFound, get("/quiz-sets/$id", t).status)
        assertEquals(0, obj(get("/quiz-sets", t).bodyAsText()).getValue("items").jsonArray.size)
        assertEquals(1, quizRepo.attempts.size)   // retained
        assertEquals(HttpStatusCode.NotFound, send("DELETE", "/quiz-sets/$id", t).status)
    }

    @Test fun listShowsAttemptCountAndBestScorePerSet() = testApplication {
        val (t, _) = setup()
        val id = createSet(t)
        fillSet(t, id, q1)
        repeat(2) { n ->
            val attempt = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText()).str("attemptId")
            val pick = if (n == 0) wrong(q1) else correct(q1)
            send("POST", "/quiz-attempts/$attempt/submit", t, """{"answers":[${answer(q1, pick)}]}""")
        }
        val item = obj(get("/quiz-sets", t).bodyAsText()).getValue("items").jsonArray[0].o()
        assertEquals(2, item.str("attemptCount").toInt())
        assertEquals(100.0, item.str("bestPercent").toDouble())
        val history = obj(get("/quiz-sets/$id/attempts", t).bodyAsText()).getValue("items").jsonArray
        assertEquals(2, history.size)
    }

    @Test fun shufflingKeepsEveryQuestionAndOption() = testApplication {
        val (t, _) = setup()
        val id = createSet(t, """{"title":"Shuffled","status":"private","settings":{"shuffleQuestions":true,"shuffleOptions":true}}""")
        fillSet(t, id, q1, q2, q3)
        val view = obj(send("POST", "/quiz-sets/$id/attempts", t).bodyAsText())
        val questions = view.getValue("questions").jsonArray.map { it.o() }
        assertEquals(setOf(q1.id.toString(), q2.id.toString(), q3.id.toString()), questions.map { it.getValue("question").o().str("id") }.toSet())
        val first = questions.first { it.getValue("question").o().str("id") == q1.id.toString() }
        assertEquals(q1.options.map { it.id.toString() }.toSet(), first.getValue("options").jsonArray.map { it.o().str("id") }.toSet())
    }

    @Test fun startingTooOftenIsRateLimited() = testApplication {
        val (t, _) = setup(limit = 2)
        val id = createSet(t)
        fillSet(t, id, q1)
        assertEquals(HttpStatusCode.OK, send("POST", "/quiz-sets/$id/attempts", t).status)
        assertEquals(HttpStatusCode.OK, send("POST", "/quiz-sets/$id/attempts", t).status)
        assertEquals(HttpStatusCode.TooManyRequests, send("POST", "/quiz-sets/$id/attempts", t).status)
    }
}

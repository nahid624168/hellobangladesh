package com.exambase.backend.questions

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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeRoutesTest {
    private val academic = InMemoryAcademicRepository()
    private val bank = InMemoryQuestionRepository(academic)
    private val practice = InMemoryPracticeRepository(bank)

    private val math = academic.subject("Mathematics")
    private val algebra = academic.category(math, "Algebra")
    private val equations = academic.subcategory(algebra, "Equations")
    private val linear = academic.lesson(equations, "Linear equations")
    private val quadratic = academic.lesson(equations, "Quadratic")
    private val draftLesson = academic.lesson(equations, "Draft lesson", status = "draft")

    private val single = bank.question(linear, "Solve x + 2 = 5", order = 1, marks = 2.0, negative = 0.5, explanation = "Subtract 2.")
    private val multi = bank.question(linear, "Select all primes", QuestionType.MULTIPLE_SELECT, order = 2)
    private val short = bank.question(linear, "Capital of Bangladesh?", QuestionType.SHORT_ANSWER, order = 3, answerKeys = listOf("Dhaka"))
    private val written = bank.question(linear, "Explain 0.999... = 1", QuestionType.WRITTEN, order = 4, explanation = "Model answer.")
    private val image = bank.question(
        quadratic, "Which shape?", QuestionType.IMAGE_BASED, order = 1,
        images = listOf(
            InMemoryQuestionRepository.Img(UUID.randomUUID(), "diagram", "question", "https://example.com/q.png"),
            InMemoryQuestionRepository.Img(UUID.randomUUID(), "image", "explanation", "https://example.com/why.png"),
        ),
    )
    private val hiddenDraft = bank.question(linear, "HIDDEN draft", status = "draft")
    private val hiddenLesson = bank.question(draftLesson, "HIDDEN in draft lesson")

    private fun correctId(q: InMemoryQuestionRepository.Q) = q.options.first { it.correct }.id
    private fun wrongId(q: InMemoryQuestionRepository.Q) = q.options.first { !it.correct }.id

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
            practice = PracticeService(practice, RateLimiter(limit, Duration.ofMinutes(1))),
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

    private suspend fun ApplicationTestBuilder.get(path: String, token: String?): HttpResponse =
        client.get("/api/v1$path") { token?.let { header(HttpHeaders.Authorization, "Bearer $it") } }

    private suspend fun ApplicationTestBuilder.check(id: Any, token: String?, body: String): HttpResponse =
        client.post("/api/v1/questions/$id/check") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(body)
        }

    private fun obj(r: String): JsonObject = Json.parseToJsonElement(r).jsonObject
    private fun choice(vararg ids: UUID) = """{"selectedOptionIds":[${ids.joinToString(",") { "\"$it\"" }}]}"""
    private fun strings(body: String, key: String) = obj(body).getValue(key).jsonArray.map { it.jsonPrimitive.content }

    @Test fun practiceEndpointsRequireSignIn() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, check(single.id, null, choice(correctId(single))).status)
        assertEquals(HttpStatusCode.Unauthorized, get("/users/me/practice-history", null).status)
        assertEquals(HttpStatusCode.Unauthorized, get("/users/me/practice-summary?lessonId=${linear.id}", null).status)
    }

    @Test fun correctAnswerRevealsKeyExplanationAndMarks() = testApplication {
        val (t, _) = setup()
        val r = check(single.id, t, choice(correctId(single)))
        assertEquals(HttpStatusCode.OK, r.status)
        val body = obj(r.bodyAsText())
        assertEquals("correct", body.getValue("result").jsonPrimitive.content)
        assertEquals(2.0, body.getValue("awardedMarks").jsonPrimitive.content.toDouble())
        assertEquals(listOf(correctId(single).toString()), strings(r.bodyAsText(), "correctOptionIds"))
        assertEquals("Subtract 2.", body.getValue("explanation").jsonPrimitive.content)
    }

    @Test fun wrongAnswerAppliesNegativeMarkingAndStillReveals() = testApplication {
        val (t, _) = setup()
        val body = obj(check(single.id, t, choice(wrongId(single))).bodyAsText())
        assertEquals("incorrect", body.getValue("result").jsonPrimitive.content)
        assertEquals(-0.5, body.getValue("awardedMarks").jsonPrimitive.content.toDouble())
        assertEquals(listOf(correctId(single).toString()), body.getValue("correctOptionIds").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun multipleSelectNeedsTheExactSet() = testApplication {
        val (t, _) = setup()
        val both = multi.options.filter { it.correct }.map { it.id }.toTypedArray()
        assertEquals("correct", obj(check(multi.id, t, choice(*both)).bodyAsText()).getValue("result").jsonPrimitive.content)
        assertEquals("incorrect", obj(check(multi.id, t, choice(both[0])).bodyAsText()).getValue("result").jsonPrimitive.content)
    }

    @Test fun shortAnswerMatchesLooselyAndRevealsAcceptedAnswers() = testApplication {
        val (t, _) = setup()
        val ok = check(short.id, t, """{"answerText":"  DHAKA "}""")
        assertEquals("correct", obj(ok.bodyAsText()).getValue("result").jsonPrimitive.content)
        assertEquals(listOf("Dhaka"), strings(ok.bodyAsText(), "acceptedAnswers"))
        assertEquals("incorrect", obj(check(short.id, t, """{"answerText":"Sylhet"}""").bodyAsText()).getValue("result").jsonPrimitive.content)
    }

    @Test fun writtenIsUngradedButShowsTheModelAnswer() = testApplication {
        val (t, _) = setup()
        val body = obj(check(written.id, t, """{"answerText":"Because 1/3 = 0.333..."}""").bodyAsText())
        assertEquals("ungraded", body.getValue("result").jsonPrimitive.content)
        assertEquals("Model answer.", body.getValue("explanation").jsonPrimitive.content)
    }

    @Test fun explanationImagesAppearOnlyAfterChecking() = testApplication {
        val (t, _) = setup()
        assertFalse(get("/questions/${image.id}", t).bodyAsText().contains("why.png"))
        val body = check(image.id, t, choice(correctId(image))).bodyAsText()
        assertTrue(body.contains("why.png"))
        assertFalse(body.contains("q.png"), "question-placement images are not repeated")
    }

    @Test fun hiddenAndMalformedQuestionsAreNotFound() = testApplication {
        val (t, _) = setup()
        assertEquals(HttpStatusCode.NotFound, check(hiddenDraft.id, t, choice(UUID.randomUUID())).status)
        assertEquals(HttpStatusCode.NotFound, check(hiddenLesson.id, t, choice(UUID.randomUUID())).status)
        assertEquals(HttpStatusCode.NotFound, check(UUID.randomUUID(), t, choice(UUID.randomUUID())).status)
        assertEquals(HttpStatusCode.NotFound, check("not-a-uuid", t, choice(UUID.randomUUID())).status)
        assertTrue(practice.attempts.isEmpty(), "nothing is recorded for a missing question")
    }

    @Test fun badInputIsA400NamingTheFieldAndRecordsNothing() = testApplication {
        val (t, _) = setup()
        suspend fun fieldOf(id: Any, body: String): String {
            val r = check(id, t, body)
            assertEquals(HttpStatusCode.BadRequest, r.status, body)
            return obj(r.bodyAsText()).getValue("error").jsonObject.getValue("fields").jsonObject.keys.single()
        }
        assertEquals("selectedOptionIds", fieldOf(single.id, """{}"""))
        assertEquals("selectedOptionIds", fieldOf(single.id, choice(correctId(single), wrongId(single))))
        assertEquals("selectedOptionIds", fieldOf(single.id, choice(UUID.randomUUID())))
        assertEquals("answerText", fieldOf(short.id, """{"answerText":""}"""))
        assertEquals(HttpStatusCode.BadRequest, check(single.id, t, "not json").status)
        assertTrue(practice.attempts.isEmpty())
    }

    @Test fun historyIsPerUserNewestFirstAndFilterable() = testApplication {
        val (t, other) = setup()
        check(single.id, t, choice(wrongId(single)))
        check(short.id, t, """{"answerText":"Dhaka"}""")
        check(image.id, t, choice(correctId(image)))
        check(single.id, other, choice(correctId(single)))

        val mine = obj(get("/users/me/practice-history", t).bodyAsText()).getValue("items").jsonArray
        assertEquals(3, mine.size)
        assertEquals("Which shape?", mine.first().jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(2, obj(get("/users/me/practice-history?lessonId=${linear.id}", t).bodyAsText()).getValue("items").jsonArray.size)
        assertEquals(1, obj(get("/users/me/practice-history?questionId=${single.id}", t).bodyAsText()).getValue("items").jsonArray.size)
        assertEquals(1, obj(get("/users/me/practice-history", other).bodyAsText()).getValue("items").jsonArray.size)
        assertEquals(HttpStatusCode.BadRequest, get("/users/me/practice-history?lessonId=zzz", t).status)
    }

    @Test fun historyHidesQuestionsThatLaterBecameHidden() = testApplication {
        val (t, _) = setup()
        check(single.id, t, choice(correctId(single)))
        single.status = "archived"
        assertEquals(0, obj(get("/users/me/practice-history", t).bodyAsText()).getValue("items").jsonArray.size)
        single.status = "published"
    }

    @Test fun summaryUsesTheLatestAttemptPerQuestion() = testApplication {
        val (t, _) = setup()
        check(single.id, t, choice(wrongId(single)))
        check(single.id, t, choice(correctId(single)))
        check(short.id, t, """{"answerText":"Sylhet"}""")
        val s = obj(get("/users/me/practice-summary?lessonId=${linear.id}", t).bodyAsText())
        assertEquals(4, s.getValue("totalQuestions").jsonPrimitive.content.toInt())
        assertEquals(2, s.getValue("attemptedQuestions").jsonPrimitive.content.toInt())
        assertEquals(1, s.getValue("correctQuestions").jsonPrimitive.content.toInt())
        assertEquals(HttpStatusCode.BadRequest, get("/users/me/practice-summary", t).status)
    }

    @Test fun tooManyAnswersTooFastIsRateLimited() = testApplication {
        val (t, _) = setup(limit = 2)
        assertEquals(HttpStatusCode.OK, check(single.id, t, choice(correctId(single))).status)
        assertEquals(HttpStatusCode.OK, check(single.id, t, choice(correctId(single))).status)
        assertEquals(HttpStatusCode.TooManyRequests, check(single.id, t, choice(correctId(single))).status)
    }
}

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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestionRoutesTest {
    private val academic = InMemoryAcademicRepository()
    private val repo = InMemoryQuestionRepository(academic)

    private val math = academic.subject("Mathematics")
    private val bangla = academic.subject("Bangla")
    private val draftSubject = academic.subject("Draft Subject", status = "draft")
    private val algebra = academic.category(math, "Algebra")
    private val grammar = academic.category(bangla, "Grammar")
    private val draftCategory = academic.category(math, "Draft Category", status = "draft")
    private val equations = academic.subcategory(algebra, "Equations")
    private val sandhi = academic.subcategory(grammar, "Sandhi")
    private val hiddenSub = academic.subcategory(draftCategory, "Hidden sub")
    private val linear = academic.lesson(equations, "Linear equations")
    private val draftLesson = academic.lesson(equations, "Draft lesson", status = "draft")
    private val vowel = academic.lesson(sandhi, "Vowel sandhi")
    private val hiddenLesson = academic.lesson(hiddenSub, "Never visible")
    private val orphanLesson = academic.lesson(academic.subcategory(academic.category(draftSubject, "C"), "S"), "In draft subject")

    // Visible questions
    private val q1 = repo.question(linear, "Solve x + 2 = 5", difficulty = Difficulty.EASY, order = 1, tags = listOf("algebra", "basics"))
    private val q2 = repo.question(linear, "Select all primes", QuestionType.MULTIPLE_SELECT, Difficulty.MEDIUM, order = 2, tags = listOf("algebra"))
    private val q3 = repo.question(linear, "Zero is even", QuestionType.TRUE_FALSE, Difficulty.EASY, order = 3)
    private val q4 = repo.question(
        linear, "Capital of Bangladesh?", QuestionType.SHORT_ANSWER, Difficulty.HARD, order = 4,
        answerKeys = listOf("SECRET-KEY-Dhaka"), tags = listOf("basics"),
    )
    private val q5 = repo.question(linear, "Explain why 0.999... = 1", QuestionType.WRITTEN, Difficulty.HARD, order = 5)
    private val q6 = repo.question(
        vowel, "Which shape is shown?", QuestionType.IMAGE_BASED, Difficulty.MEDIUM, order = 1,
        images = listOf(
            InMemoryQuestionRepository.Img(UUID.randomUUID(), "diagram", "question", "https://example.com/q.png"),
            InMemoryQuestionRepository.Img(UUID.randomUUID(), "image", "explanation", "https://example.com/SECRET-EXPLANATION-IMAGE.png"),
        ),
    )

    // Never visible to learners
    private val dDraft = repo.question(linear, "HIDDEN draft question", status = "draft")
    private val dReview = repo.question(linear, "HIDDEN review question", status = "review")
    private val dArchived = repo.question(linear, "HIDDEN archived question", status = "archived")
    private val dDraftLesson = repo.question(draftLesson, "HIDDEN in draft lesson")
    private val dDraftCategory = repo.question(hiddenLesson, "HIDDEN under draft category")
    private val dDraftSubject = repo.question(orphanLesson, "HIDDEN under draft subject")

    private suspend fun ApplicationTestBuilder.setup(): String {
        val sessions = InMemorySessionRepository()
        val jwt = JwtService("test-secret-test-secret-test-secret-123", "exambase-test", Duration.ofMinutes(15))
        val auth = AuthService(
            InMemoryUserRepository(), sessions, InMemoryEmailCodeRepository(),
            PasswordHasher(cost = 4), jwt, CapturingEmailSender(), Duration.ofDays(30),
        )
        val services = AppServices(
            auth, jwt, sessions, RateLimiter(1000, Duration.ofMinutes(1)),
            academic = AcademicService(academic), questions = QuestionService(repo),
        )
        application { module(AppConfig.forTests(), null, services) }
        val r = client.post("/api/v1/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"a@b.com","password":"study2026","displayName":"Rafi"}""")
        }
        return Json.parseToJsonElement(r.bodyAsText()).jsonObject.getValue("accessToken").jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.get(path: String, token: String?): HttpResponse =
        client.get("/api/v1$path") { token?.let { header(HttpHeaders.Authorization, "Bearer $it") } }

    private fun json(body: String) = Json.parseToJsonElement(body).jsonObject
    private fun texts(body: String) = json(body).getValue("items").jsonArray.map { it.jsonObject.getValue("text").jsonPrimitive.content }

    @Test fun questionsRequireSignIn() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, get("/questions", null).status)
        assertEquals(HttpStatusCode.Unauthorized, get("/questions/${q1.id}", null).status)
        assertEquals(HttpStatusCode.Unauthorized, get("/questions/tags", null).status)
    }

    @Test fun onlyPublishedQuestionsUnderPublishedAncestorsAreListed() = testApplication {
        val t = setup()
        val body = get("/questions?limit=50", t).bodyAsText()
        val all = texts(body)
        assertEquals(6, all.size)
        assertFalse(all.any { it.startsWith("HIDDEN") }, "hidden content leaked: $all")
    }

    @Test fun correctAnswersKeysAndExplanationsNeverAppearInListOrDetail() = testApplication {
        val t = setup()
        val forbidden = listOf("SECRET-EXPLANATION", "SECRET-KEY", "isCorrect", "correct", "explanation", "answerKey")
        val list = get("/questions?limit=50", t).bodyAsText()
        forbidden.forEach { assertFalse(list.contains(it, ignoreCase = true), "list leaked '$it'") }
        listOf(q1, q2, q3, q4, q5, q6).forEach { q ->
            val detail = get("/questions/${q.id}", t)
            assertEquals(HttpStatusCode.OK, detail.status)
            val text = detail.bodyAsText()
            forbidden.forEach { assertFalse(text.contains(it, ignoreCase = true), "detail of '${q.text}' leaked '$it'") }
        }
    }

    @Test fun defaultOrderIsStable() = testApplication {
        val t = setup()
        val body = get("/questions?lessonId=${linear.id}", t).bodyAsText()
        assertEquals(listOf("Solve x + 2 = 5", "Select all primes", "Zero is even", "Capital of Bangladesh?", "Explain why 0.999... = 1"), texts(body))
    }

    @Test fun filtersByHierarchyLevel() = testApplication {
        val t = setup()
        assertEquals(5, texts(get("/questions?subjectId=${math.id}", t).bodyAsText()).size)
        assertEquals(listOf("Which shape is shown?"), texts(get("/questions?subjectId=${bangla.id}", t).bodyAsText()))
        assertEquals(listOf("Which shape is shown?"), texts(get("/questions?categoryId=${grammar.id}", t).bodyAsText()))
        assertEquals(5, texts(get("/questions?subcategoryId=${equations.id}", t).bodyAsText()).size)
        assertEquals(listOf("Which shape is shown?"), texts(get("/questions?lessonId=${vowel.id}", t).bodyAsText()))
        // Asking for a hidden branch returns an empty page, not the hidden content.
        assertEquals(emptyList(), texts(get("/questions?lessonId=${hiddenLesson.id}", t).bodyAsText()))
        assertEquals(emptyList(), texts(get("/questions?subjectId=${draftSubject.id}", t).bodyAsText()))
    }

    @Test fun filtersByTypeDifficultyTagAndSearchAndCombinations() = testApplication {
        val t = setup()
        assertEquals(listOf("Select all primes"), texts(get("/questions?type=multiple_select", t).bodyAsText()))
        assertEquals(listOf("Which shape is shown?"), texts(get("/questions?type=image_based", t).bodyAsText()))
        assertEquals(setOf("Solve x + 2 = 5", "Zero is even"), texts(get("/questions?difficulty=easy", t).bodyAsText()).toSet())
        assertEquals(setOf("Solve x + 2 = 5", "Select all primes"), texts(get("/questions?tag=algebra", t).bodyAsText()).toSet())
        assertEquals(listOf("Capital of Bangladesh?"), texts(get("/questions?q=capital", t).bodyAsText()))
        assertEquals(listOf("Solve x + 2 = 5"), texts(get("/questions?tag=algebra&difficulty=easy&subjectId=${math.id}", t).bodyAsText()))
        assertEquals(emptyList(), texts(get("/questions?tag=algebra&subjectId=${bangla.id}", t).bodyAsText()))
    }

    @Test fun sortByDifficultyAndNewest() = testApplication {
        val t = setup()
        val byDifficulty = json(get("/questions?sort=difficulty&limit=50", t).bodyAsText()).getValue("items").jsonArray
            .map { it.jsonObject.getValue("difficulty").jsonPrimitive.content }
        assertEquals(byDifficulty.sortedBy { listOf("easy", "medium", "hard").indexOf(it) }, byDifficulty)
        assertEquals("Which shape is shown?", texts(get("/questions?sort=newest", t).bodyAsText()).first())
    }

    @Test fun badFiltersAreRejectedWithFieldNames() = testApplication {
        val t = setup()
        listOf(
            "sort=random" to "sort", "type=essay" to "type", "difficulty=extreme" to "difficulty",
            "lessonId=not-a-uuid" to "lessonId", "subjectId=123" to "subjectId", "tag=Bad%20Tag!" to "tag",
            "q=" + "x".repeat(101) to "q",
        ).forEach { (query, field) ->
            val r = get("/questions?$query", t)
            assertEquals(HttpStatusCode.BadRequest, r.status, query)
            val fields = json(r.bodyAsText()).getValue("error").jsonObject.getValue("fields").jsonObject
            assertTrue(field in fields.keys, "expected field $field in $fields")
        }
        assertEquals(HttpStatusCode.BadRequest, get("/questions?limit=0", t).status)
        assertEquals(HttpStatusCode.BadRequest, get("/questions?limit=51", t).status)
        assertEquals(HttpStatusCode.BadRequest, get("/questions?cursor=garbage", t).status)
    }

    @Test fun cursorPagingWalksEveryQuestionOnce() = testApplication {
        val t = setup()
        val seen = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val url = "/questions?limit=2" + (cursor?.let { "&cursor=$it" } ?: "")
            val body = json(get(url, t).bodyAsText())
            seen += body.getValue("items").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            val next = body["nextCursor"]
            cursor = if (next == null || next is JsonNull) null else next.jsonPrimitive.content
            pages++
        } while (cursor != null && pages < 10)
        assertEquals(6, seen.size)
        assertEquals(seen.size, seen.toSet().size)
        assertEquals(3, pages)
    }

    @Test fun detailHasOptionsInOrderImagesAndBreadcrumb() = testApplication {
        val t = setup()
        val choice = json(get("/questions/${q1.id}", t).bodyAsText())
        assertEquals(listOf("A", "B", "C", "D"), choice.getValue("options").jsonArray.map { it.jsonObject.getValue("text").jsonPrimitive.content })
        assertEquals(listOf("subject", "category", "subcategory", "lesson"), choice.getValue("breadcrumb").jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content })
        assertEquals("Linear equations", choice.getValue("breadcrumb").jsonArray.last().jsonObject.getValue("title").jsonPrimitive.content)

        val written = json(get("/questions/${q5.id}", t).bodyAsText())
        assertEquals(0, written.getValue("options").jsonArray.size)
        val shortAnswer = json(get("/questions/${q4.id}", t).bodyAsText())
        assertEquals(0, shortAnswer.getValue("options").jsonArray.size)

        val image = json(get("/questions/${q6.id}", t).bodyAsText())
        val images = image.getValue("images").jsonArray
        assertEquals(1, images.size)                                            // explanation-placement image withheld
        assertEquals("https://example.com/q.png", images[0].jsonObject.getValue("url").jsonPrimitive.content)
        assertTrue(image.getValue("question").jsonObject.getValue("hasImage").jsonPrimitive.content.toBoolean())
    }

    @Test fun trueFalseHasTwoOptions() = testApplication {
        val t = setup()
        val d = json(get("/questions/${q3.id}", t).bodyAsText())
        assertEquals(listOf("True", "False"), d.getValue("options").jsonArray.map { it.jsonObject.getValue("text").jsonPrimitive.content })
    }

    @Test fun hiddenAndMalformedQuestionIdsAreNotFound() = testApplication {
        val t = setup()
        listOf(dDraft, dReview, dArchived, dDraftLesson, dDraftCategory, dDraftSubject).forEach {
            assertEquals(HttpStatusCode.NotFound, get("/questions/${it.id}", t).status, it.text)
        }
        assertEquals(HttpStatusCode.NotFound, get("/questions/not-a-uuid", t).status)
        assertEquals(HttpStatusCode.NotFound, get("/questions/${UUID.randomUUID()}", t).status)
    }

    @Test fun tagsCountOnlyVisibleQuestions() = testApplication {
        val t = setup()
        repo.question(linear, "HIDDEN tagged draft", status = "draft", tags = listOf("algebra", "secret-only"))
        val body = Json.parseToJsonElement(get("/questions/tags", t).bodyAsText()).jsonArray
        val counts = body.associate { it.jsonObject.getValue("slug").jsonPrimitive.content to it.jsonObject.getValue("questionCount").jsonPrimitive.content.toInt() }
        assertEquals(mapOf("algebra" to 2, "basics" to 2), counts)             // "secret-only" has no visible question
    }
}

package com.exambase.backend.academic

import com.exambase.backend.AppConfig
import com.exambase.backend.AppServices
import com.exambase.backend.auth.AuthService
import com.exambase.backend.auth.CapturingEmailSender
import com.exambase.backend.auth.InMemoryEmailCodeRepository
import com.exambase.backend.auth.InMemorySessionRepository
import com.exambase.backend.auth.InMemoryUserRepository
import com.exambase.backend.auth.JwtService
import com.exambase.backend.auth.PasswordHasher
import com.exambase.backend.auth.RateLimiter
import com.exambase.backend.module
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcademicRoutesTest {
    private val repo = InMemoryAcademicRepository()
    private val math = repo.subject("Mathematics", featured = true, order = 2, description = "Numbers")
    private val bangla = repo.subject("Bangla", order = 1)
    private val hidden = repo.subject("Secret Draft", status = "draft")
    private val algebra = repo.category(math, "Algebra")
    private val draftCategory = repo.category(math, "Hidden Category", status = "draft")
    private val equations = repo.subcategory(algebra, "Equations")
    private val l1 = repo.lesson(equations, "Linear equations", order = 1)
    private val l2 = repo.lesson(equations, "Quadratic equations", order = 2)
    private val l3 = repo.lesson(equations, "Unpublished lesson", status = "draft", order = 3)
    private val hiddenLesson = repo.lesson(repo.subcategory(draftCategory, "Inside draft category"), "Never visible")

    private suspend fun ApplicationTestBuilder.setup(): String {
        val sessions = InMemorySessionRepository()
        val jwt = JwtService("test-secret-test-secret-test-secret-123", "exambase-test", Duration.ofMinutes(15))
        val auth = AuthService(
            InMemoryUserRepository(), sessions, InMemoryEmailCodeRepository(),
            PasswordHasher(cost = 4), jwt, CapturingEmailSender(), Duration.ofDays(30),
        )
        val services = AppServices(auth, jwt, sessions, RateLimiter(1000, Duration.ofMinutes(1)), academic = AcademicService(repo))
        application { module(AppConfig.forTests(), null, services) }
        return registerToken("a@b.com")
    }

    private suspend fun ApplicationTestBuilder.registerToken(email: String): String {
        val r = client.post("/api/v1/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"$email","password":"study2026","displayName":"Rafi"}""")
        }
        return Json.parseToJsonElement(r.bodyAsText()).jsonObject.getValue("accessToken").jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.get(path: String, token: String?): HttpResponse =
        client.get("/api/v1$path") { token?.let { header(HttpHeaders.Authorization, "Bearer $it") } }

    private fun json(r: String) = Json.parseToJsonElement(r).jsonObject
    private fun titles(body: String, key: String = "items") = json(body).getValue(key).jsonArray.map { it.jsonObject.getValue("title").jsonPrimitive.content }
    private fun titlesOfArray(body: String) = Json.parseToJsonElement(body).jsonArray.map { it.jsonObject.getValue("title").jsonPrimitive.content }

    /** The server may omit null fields or send explicit nulls; both mean "absent". */
    private fun assertAbsent(value: JsonElement?) = assertTrue(value == null || value is JsonNull, "expected absent but was $value")

    @Test fun contentRequiresSignIn() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, get("/academic/subjects", null).status)
        assertEquals(HttpStatusCode.Unauthorized, get("/users/me/bookmarks", null).status)
    }

    @Test fun subjectListHidesDraftsAndShowsFeaturedFirst() = testApplication {
        val t = setup()
        val r = get("/academic/subjects", t)
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(listOf("Mathematics", "Bangla"), titles(r.bodyAsText()))
        val math = json(r.bodyAsText()).getValue("items").jsonArray[0].jsonObject
        assertEquals("1", math.getValue("categoryCount").jsonPrimitive.content)   // draft category not counted
        assertEquals("2", math.getValue("lessonCount").jsonPrimitive.content)     // draft lesson not counted
    }

    @Test fun subjectSearchSortAndFeaturedFilter() = testApplication {
        val t = setup()
        assertEquals(listOf("Bangla"), titles(get("/academic/subjects?q=bang", t).bodyAsText()))
        assertEquals(listOf("Bangla", "Mathematics"), titles(get("/academic/subjects?sort=title", t).bodyAsText()))
        assertEquals(listOf("Mathematics"), titles(get("/academic/subjects?featured=true", t).bodyAsText()))
        assertEquals(HttpStatusCode.BadRequest, get("/academic/subjects?sort=random", t).status)
    }

    @Test fun cursorPaginationWalksAllSubjects() = testApplication {
        val t = setup()
        val first = json(get("/academic/subjects?limit=1", t).bodyAsText())
        val cursor = first.getValue("nextCursor").jsonPrimitive.content
        val second = json(get("/academic/subjects?limit=1&cursor=$cursor", t).bodyAsText())
        assertEquals("Bangla", second.getValue("items").jsonArray[0].jsonObject.getValue("title").jsonPrimitive.content)
        assertAbsent(second["nextCursor"])
    }

    @Test fun badPagingInputIsRejected() = testApplication {
        val t = setup()
        assertEquals(HttpStatusCode.BadRequest, get("/academic/subjects?limit=0", t).status)
        assertEquals(HttpStatusCode.BadRequest, get("/academic/subjects?limit=999", t).status)
        assertEquals(HttpStatusCode.BadRequest, get("/academic/subjects?cursor=garbage", t).status)
    }

    @Test fun draftSubjectAndCategoryAreNotFound() = testApplication {
        val t = setup()
        assertEquals(HttpStatusCode.NotFound, get("/academic/subjects/${hidden.id}", t).status)
        assertEquals(HttpStatusCode.NotFound, get("/academic/categories/${draftCategory.id}", t).status)
        assertEquals(HttpStatusCode.NotFound, get("/academic/subjects/not-a-uuid", t).status)
    }

    @Test fun drillDownFromSubjectToLessonWithBreadcrumb() = testApplication {
        val t = setup()
        val subject = json(get("/academic/subjects/${math.id}", t).bodyAsText())
        assertEquals(listOf("Algebra"), titles(subject.toString(), "categories"))
        val category = json(get("/academic/categories/${algebra.id}", t).bodyAsText())
        assertEquals(listOf("Equations"), titles(category.toString(), "subcategories"))
        val sub = get("/academic/subcategories/${equations.id}", t)
        assertEquals(listOf("Linear equations", "Quadratic equations"), titles(sub.bodyAsText(), "lessons"))
        val lesson = json(get("/academic/lessons/${l1.id}", t).bodyAsText())
        assertEquals(listOf("Mathematics", "Algebra", "Equations"), titles(lesson.toString(), "breadcrumb"))
        assertEquals(l2.id.toString(), lesson.getValue("nextLessonId").jsonPrimitive.content)
        assertAbsent(lesson["previousLessonId"])
    }

    @Test fun lessonInsideHiddenBranchIsNotFoundEvenWithItsOwnPublishedStatus() = testApplication {
        val t = setup()
        assertEquals(HttpStatusCode.NotFound, get("/academic/lessons/${hiddenLesson.id}", t).status)
        assertEquals(HttpStatusCode.NotFound, get("/academic/lessons/${l3.id}", t).status)
    }

    @Test fun bookmarkAddRemoveAndList() = testApplication {
        val t = setup()
        val put = client.put("/api/v1/academic/lessons/${l1.id}/bookmark") { header(HttpHeaders.Authorization, "Bearer $t") }
        assertEquals(HttpStatusCode.OK, put.status)
        assertEquals(listOf("Linear equations"), titles(get("/users/me/bookmarks", t).bodyAsText()))
        assertTrue(get("/academic/lessons/${l1.id}", t).bodyAsText().contains("\"bookmarked\":true"))
        client.delete("/api/v1/academic/lessons/${l1.id}/bookmark") { header(HttpHeaders.Authorization, "Bearer $t") }
        assertEquals(emptyList(), titles(get("/users/me/bookmarks", t).bodyAsText()))
    }

    @Test fun bookmarksAreNotSharedBetweenUsers() = testApplication {
        val a = setup()
        val b = registerToken("other@b.com")
        client.put("/api/v1/academic/lessons/${l1.id}/bookmark") { header(HttpHeaders.Authorization, "Bearer $a") }
        assertEquals(emptyList(), titles(get("/users/me/bookmarks", b).bodyAsText()))
    }

    @Test fun cannotBookmarkHiddenLesson() = testApplication {
        val t = setup()
        val r = client.put("/api/v1/academic/lessons/${hiddenLesson.id}/bookmark") { header(HttpHeaders.Authorization, "Bearer $t") }
        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test fun viewRecordsRecentAndCompletionSticks() = testApplication {
        val t = setup()
        suspend fun view(id: Any) = client.post("/api/v1/academic/lessons/$id/view") { header(HttpHeaders.Authorization, "Bearer $t") }
        assertEquals(HttpStatusCode.OK, view(l1.id).status)
        assertEquals(HttpStatusCode.OK, view(l2.id).status)
        assertEquals(listOf("Quadratic equations", "Linear equations"), titlesOfArray(get("/users/me/recent-lessons", t).bodyAsText()))
        val done = client.put("/api/v1/academic/lessons/${l1.id}/progress") {
            header(HttpHeaders.Authorization, "Bearer $t")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"status":"completed"}""")
        }
        assertEquals(HttpStatusCode.OK, done.status)
        view(l1.id)   // viewing again must not downgrade completed
        assertTrue(get("/academic/lessons/${l1.id}", t).bodyAsText().contains("\"progress\":\"completed\""))
    }

    @Test fun progressRejectsUnknownStatus() = testApplication {
        val t = setup()
        val r = client.put("/api/v1/academic/lessons/${l1.id}/progress") {
            header(HttpHeaders.Authorization, "Bearer $t")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"status":"not_started"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}

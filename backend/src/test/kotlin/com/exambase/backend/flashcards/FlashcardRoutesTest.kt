package com.exambase.backend.flashcards

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
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlashcardRoutesTest {
    private val clock = object : Clock() {
        private var now = Instant.parse("2026-11-21T12:00:00Z")
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
        fun advance(d: Duration) { now = now.plus(d) }
    }
    private val repo = InMemoryFlashcardRepository()
    private val json = Json { ignoreUnknownKeys = true }

    private fun app(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        val users = InMemoryUserRepository()
        val sessions = InMemorySessionRepository()
        val codes = InMemoryEmailCodeRepository()
        val jwt = JwtService("test-secret-at-least-32-characters-long!!", Duration.ofMinutes(15), Duration.ofDays(30))
        val auth = AuthService(users, sessions, codes, jwt, PasswordHasher(), CapturingEmailSender(), RateLimiter(100, Duration.ofMinutes(1)), clock)
        val services = AppServices(
            auth = auth, jwt = jwt, sessions = sessions, authLimiter = RateLimiter(100, Duration.ofMinutes(1)), clock = clock,
            flashcards = FlashcardService(repo, clock),
        )
        application { module(AppConfig.forTests(), null, services) }
        block()
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.token(): String {
        client.post("/api/v1/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"fc@example.com","password":"Password1!","displayName":"FC"}""")
        }
        // verify may be auto in test - login
        val login = client.post("/api/v1/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"fc@example.com","password":"Password1!"}""")
        }
        return json.parseToJsonElement(login.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content
    }

    @Test fun ownershipAndReviewFlow() = app {
        val t = token()
        val create = client.post("/api/v1/flashcard-sets") {
            header(HttpHeaders.Authorization, "Bearer $t")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"title":"Biology","status":"private"}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        val setId = json.parseToJsonElement(create.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content

        val cardRes = client.post("/api/v1/flashcard-sets/$setId/cards") {
            header(HttpHeaders.Authorization, "Bearer $t")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"frontText":"Mitochondria","backText":"Powerhouse of the cell"}""")
        }
        assertEquals(HttpStatusCode.Created, cardRes.status)
        val cardId = json.parseToJsonElement(cardRes.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content

        val due = client.get("/api/v1/flashcard-sets/$setId/due") {
            header(HttpHeaders.Authorization, "Bearer $t")
        }
        assertEquals(HttpStatusCode.OK, due.status)
        assertEquals(1, json.parseToJsonElement(due.bodyAsText()).jsonArray.size)

        val review = client.post("/api/v1/flashcards/$cardId/review") {
            header(HttpHeaders.Authorization, "Bearer $t")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"rating":"good"}""")
        }
        assertEquals(HttpStatusCode.OK, review.status)
        val srs = json.parseToJsonElement(review.bodyAsText()).jsonObject["srs"]!!.jsonObject
        assertEquals(1, srs["intervalDays"]!!.jsonPrimitive.content.toInt())

        // other user cannot see
        client.post("/api/v1/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"other@example.com","password":"Password1!","displayName":"O"}""")
        }
        val otherLogin = client.post("/api/v1/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"other@example.com","password":"Password1!"}""")
        }
        val other = json.parseToJsonElement(otherLogin.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content
        val denied = client.get("/api/v1/flashcard-sets/$setId") {
            header(HttpHeaders.Authorization, "Bearer $other")
        }
        assertEquals(HttpStatusCode.NotFound, denied.status)

        val del = client.delete("/api/v1/flashcard-sets/$setId") {
            header(HttpHeaders.Authorization, "Bearer $t")
        }
        assertEquals(HttpStatusCode.NoContent, del.status)
    }
}

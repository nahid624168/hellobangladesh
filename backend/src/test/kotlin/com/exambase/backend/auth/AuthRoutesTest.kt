package com.exambase.backend.auth

import com.exambase.backend.AppConfig
import com.exambase.backend.AppServices
import com.exambase.backend.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthRoutesTest {
    private fun ApplicationTestBuilder.setup(limit: Int = 1000) {
        val sessions = InMemorySessionRepository()
        val jwt = JwtService("test-secret-test-secret-test-secret-123", "exambase-test", Duration.ofMinutes(15))
        val auth = AuthService(
            InMemoryUserRepository(), sessions, InMemoryEmailCodeRepository(),
            PasswordHasher(cost = 4), jwt, CapturingEmailSender(), Duration.ofDays(30),
        )
        val services = AppServices(auth, jwt, sessions, RateLimiter(limit, Duration.ofMinutes(1)))
        application { module(AppConfig.forTests(), null, services) }
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null) =
        client.post(path) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(body)
        }

    private fun field(json: String, vararg path: String): String {
        var el = Json.parseToJsonElement(json)
        for (p in path.dropLast(1)) el = el.jsonObject.getValue(p)
        return el.jsonObject.getValue(path.last()).jsonPrimitive.content
    }

    private val registerBody = """{"email":"a@b.com","password":"study2026","displayName":"Rafi"}"""

    @Test fun registerThenFetchProfile() = testApplication {
        setup()
        val reg = postJson("/api/v1/auth/register", registerBody)
        assertEquals(HttpStatusCode.Created, reg.status)
        val token = field(reg.bodyAsText(), "accessToken")
        val me = client.get("/api/v1/users/me") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals("a@b.com", field(me.bodyAsText(), "email"))
    }

    @Test fun profileRequiresToken() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/users/me").status)
    }

    @Test fun logoutInvalidatesAccessTokenImmediately() = testApplication {
        setup()
        val token = field(postJson("/api/v1/auth/register", registerBody).bodyAsText(), "accessToken")
        assertEquals(HttpStatusCode.OK, postJson("/api/v1/auth/logout", "{}", token).status)
        val me = client.get("/api/v1/users/me") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.Unauthorized, me.status)
    }

    @Test fun invalidJsonReturns400Envelope() = testApplication {
        setup()
        val res = postJson("/api/v1/auth/login", "not json")
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals("invalid_request", field(res.bodyAsText(), "error", "code"))
    }

    @Test fun rateLimitReturns429() = testApplication {
        setup(limit = 2)
        repeat(2) { postJson("/api/v1/auth/login", """{"email":"x@y.com","password":"whatever1"}""") }
        val third = postJson("/api/v1/auth/login", """{"email":"x@y.com","password":"whatever1"}""")
        assertEquals(HttpStatusCode.TooManyRequests, third.status)
    }
}

package com.exambase.backend

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun healthReturnsOk() = testApplication {
        application { module(AppConfig.forTests(), null) }
        val response = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun readyIsUnavailableWithoutDatabase() = testApplication {
        application { module(AppConfig.forTests(), null) }
        val response = client.get("/api/v1/health/ready")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("not_ready"))
    }
}

package com.exambase.backend

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminPanelStaticTest {
    @Test
    fun panelIsServedWithContentSecurityPolicy() = testApplication {
        application { module(AppConfig.forTests(), null) }
        val response = client.get("/admin-panel/index.html")
        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertTrue(html.contains("Content-Security-Policy"))
        assertTrue(html.contains("admin.js"))
    }

    @Test
    fun panelScriptNeverUsesInnerHtml() = testApplication {
        application { module(AppConfig.forTests(), null) }
        val js = client.get("/admin-panel/admin.js").bodyAsText()
        assertFalse(js.contains("innerHTML"), "panel must build the DOM with text nodes only")
        assertFalse(js.contains("eval("))
    }

    @Test
    fun apiHealthStillWorksBesidePanel() = testApplication {
        application { module(AppConfig.forTests(), null) }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/health").status)
    }
}

package com.exambase.backend.dashboard

import com.exambase.backend.ApiException
import com.exambase.backend.auth.AuthPrincipal
import com.exambase.backend.auth.AuthService
import com.exambase.backend.auth.JWT_AUTH
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private fun ApplicationCall.user() =
    principal<AuthPrincipal>()?.userId
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

fun Route.dashboardRoutes(dashboard: DashboardService, auth: AuthService) {
    authenticate(JWT_AUTH) {
        get("/dashboard") {
            val userId = call.user()
            val me = auth.me(userId)
            call.respond(
                dashboard.load(
                    userId = userId,
                    displayName = me.displayName,
                    emailVerified = me.emailVerified,
                ),
            )
        }
    }
}

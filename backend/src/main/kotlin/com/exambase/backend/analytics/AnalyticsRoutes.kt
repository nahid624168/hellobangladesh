package com.exambase.backend.analytics

import com.exambase.backend.ApiException
import com.exambase.backend.auth.AuthPrincipal
import com.exambase.backend.auth.JWT_AUTH
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route

private fun ApplicationCall.user() =
    principal<AuthPrincipal>()?.userId
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

fun Route.analyticsRoutes(service: AnalyticsService) {
    authenticate(JWT_AUTH) {
        route("/analytics") {
            get("/overview") { call.respond(service.overview(call.user())) }
            get("/trends") {
                val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
                call.respond(service.trends(call.user(), days))
            }
            get("/subjects") { call.respond(service.subjects(call.user())) }
            get("/achievements") { call.respond(service.achievements(call.user())) }
            patch("/daily-goal") {
                val body = call.receive<UpdateDailyGoalRequest>()
                service.updateDailyGoal(call.user(), body.target)
                call.respond(service.overview(call.user()))
            }
            get("/leaderboard-visibility") { call.respond(service.leaderboardVisibility(call.user())) }
            patch("/leaderboard-visibility") {
                val body = call.receive<UpdateLeaderboardVisibilityRequest>()
                service.setLeaderboardVisibility(call.user(), body.showOnPublicLeaderboards)
                call.respond(service.leaderboardVisibility(call.user()))
            }
            // Intentionally no POST /points — clients cannot grant points.
        }
    }
}

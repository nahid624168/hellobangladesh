package com.exambase.backend.search

import com.exambase.backend.ApiException
import com.exambase.backend.auth.AuthPrincipal
import com.exambase.backend.auth.JWT_AUTH
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private fun ApplicationCall.user() =
    principal<AuthPrincipal>()?.userId
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

fun Route.searchRoutes(service: SearchService) {
    authenticate(JWT_AUTH) {
        route("/search") {
            get {
                val q = call.request.queryParameters
                call.respond(
                    service.search(
                        call.user(),
                        q["q"],
                        q["types"],
                        q["limit"],
                        q["cursor"],
                    ),
                )
            }
            get("/recent") { call.respond(service.recent(call.user())) }
            get("/suggested") { call.respond(service.suggested()) }
        }
    }
}

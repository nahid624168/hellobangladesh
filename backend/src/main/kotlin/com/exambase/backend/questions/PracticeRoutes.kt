package com.exambase.backend.questions

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
import io.ktor.server.routing.post

private fun ApplicationCall.user() =
    principal<AuthPrincipal>()?.userId ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

fun Route.practiceRoutes(service: PracticeService) {
    authenticate(JWT_AUTH) {
        post("/questions/{id}/check") {
            val id = call.parameters["id"] ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not found.")
            call.respond(service.check(id, call.user(), call.receive<CheckAnswerRequest>()))
        }
        get("/users/me/practice-history") {
            val q = call.request.queryParameters
            call.respond(service.history(call.user(), q["lessonId"], q["questionId"], q["limit"], q["cursor"]))
        }
        get("/users/me/practice-summary") {
            call.respond(service.summary(call.user(), call.request.queryParameters["lessonId"]))
        }
    }
}

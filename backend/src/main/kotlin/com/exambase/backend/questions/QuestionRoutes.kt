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
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private fun ApplicationCall.user() =
    principal<AuthPrincipal>()?.userId ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

/** Read-only bank plus Step 14b bookmarks and reports. All require sign-in. */
fun Route.questionRoutes(service: QuestionService) {
    authenticate(JWT_AUTH) {
        route("/questions") {
            get {
                val q = call.request.queryParameters
                call.respond(
                    service.list(
                        subjectId = q["subjectId"], categoryId = q["categoryId"], subcategoryId = q["subcategoryId"],
                        lessonId = q["lessonId"], type = q["type"], difficulty = q["difficulty"], tag = q["tag"],
                        search = q["q"], sort = q["sort"], limitRaw = q["limit"], cursor = q["cursor"],
                    ),
                )
            }
            // Declared before "/{id}" so the literal path wins.
            get("/tags") { call.respond(service.tags()) }
            get("/{id}") {
                val id = call.parameters["id"]
                    ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not found.")
                call.respond(service.detail(id))
            }
            put("/{id}/bookmark") {
                val id = call.parameters["id"]
                    ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not found.")
                service.setBookmark(call.user(), id, true)
                call.respond(mapOf("bookmarked" to true))
            }
            delete("/{id}/bookmark") {
                val id = call.parameters["id"]
                    ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not found.")
                service.setBookmark(call.user(), id, false)
                call.respond(mapOf("bookmarked" to false))
            }
            post("/{id}/report") {
                val id = call.parameters["id"]
                    ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not found.")
                call.respond(HttpStatusCode.Created, service.report(call.user(), id, call.receive()))
            }
        }
        get("/users/me/question-bookmarks") {
            val q = call.request.queryParameters
            call.respond(service.bookmarks(call.user(), q["limit"], q["cursor"]))
        }
    }
}

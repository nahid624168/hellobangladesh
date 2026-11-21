package com.exambase.backend.academic

import com.exambase.backend.ApiException
import com.exambase.backend.MessageResponse
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

private fun ApplicationCall.pathId() = parameters["id"] ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Content not found.")

/** All content requires sign-in (guest browsing is off; see docs/STEP-06-08-auth-profile.md assumptions). */
fun Route.academicRoutes(service: AcademicService) {
    authenticate(JWT_AUTH) {
        route("/academic") {
            get("/subjects") {
                val q = call.request.queryParameters
                call.respond(service.subjects(q["q"], q["sort"], q["featured"], q["limit"], q["cursor"]))
            }
            get("/subjects/{id}") { call.respond(service.subject(call.pathId())) }
            get("/categories/{id}") { call.respond(service.category(call.pathId())) }
            get("/subcategories/{id}") { call.respond(service.subcategory(call.pathId(), call.user())) }
            route("/lessons/{id}") {
                get { call.respond(service.lesson(call.pathId(), call.user())) }
                /** Explicit call from the client when a lesson is opened (a GET must not change data). */
                post("/view") {
                    service.recordView(call.pathId(), call.user())
                    call.respond(MessageResponse("Recorded."))
                }
                put("/bookmark") {
                    service.setBookmark(call.pathId(), call.user(), true)
                    call.respond(MessageResponse("Bookmarked."))
                }
                delete("/bookmark") {
                    service.setBookmark(call.pathId(), call.user(), false)
                    call.respond(MessageResponse("Bookmark removed."))
                }
                put("/progress") {
                    service.setProgress(call.pathId(), call.user(), call.receive<SetProgressRequest>().status)
                    call.respond(MessageResponse("Saved."))
                }
            }
        }
        route("/users/me") {
            get("/bookmarks") {
                val q = call.request.queryParameters
                call.respond(service.bookmarks(call.user(), q["limit"], q["cursor"]))
            }
            get("/recent-lessons") {
                call.respond(service.recent(call.user(), call.request.queryParameters["limit"]))
            }
        }
    }
}

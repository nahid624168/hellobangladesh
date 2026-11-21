package com.exambase.backend.ai

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
import io.ktor.server.routing.route
import java.util.UUID

private fun ApplicationCall.user(): UUID {
    val p = principal<AuthPrincipal>()
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")
    // AI costs money per user, so the temporary guest preview cannot use it.
    if (p.guest) throw ApiException(HttpStatusCode.Forbidden, "guest_not_allowed", "Create a free account to use AI features.")
    return p.userId
}

fun Route.aiRoutes(service: AiService, generatedQuizzes: AiGeneratedQuizStore) {
    authenticate(JWT_AUTH) {
        route("/ai") {
            get("/consent") { call.respond(service.consent(call.user())) }
            post("/consent") { call.respond(service.accept(call.user(), call.receive<AcceptConsentRequest>())) }
            delete("/consent") { call.respond(service.revoke(call.user())) }
            get("/quota") { call.respond(service.quota(call.user())) }
            post("/run") { call.respond(service.run(call.user(), call.receive<AiRunRequest>())) }
            route("/quiz-sets") {
                get { call.respond(generatedQuizzes.list(call.user())) }
                post { call.respond(HttpStatusCode.Created, generatedQuizzes.create(call.user(), call.receive<AiGeneratedQuizCreateRequest>())) }
                get("/{id}") { call.respond(generatedQuizzes.get(call.user(), call.parameters["id"].orEmpty())) }
                delete("/{id}") { generatedQuizzes.delete(call.user(), call.parameters["id"].orEmpty()); call.respond(mapOf("message" to "AI quiz set deleted.")) }
            }
            route("/conversations") {
                get {
                    val q = call.request.queryParameters
                    call.respond(service.conversations(call.user(), q["limit"], q["cursor"]))
                }
                delete { call.respond(service.deleteAllConversations(call.user())) }
                get("/{id}") { call.respond(service.conversation(call.user(), call.parameters["id"].orEmpty())) }
                delete("/{id}") { call.respond(service.deleteConversation(call.user(), call.parameters["id"].orEmpty())) }
            }
        }
    }
}

package com.exambase.backend.flashcards

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
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private fun ApplicationCall.user() =
    principal<AuthPrincipal>()?.userId
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

fun Route.flashcardRoutes(service: FlashcardService) {
    authenticate(JWT_AUTH) {
        route("/flashcard-sets") {
            post { call.respond(HttpStatusCode.Created, service.create(call.user(), call.receive())) }
            get {
                val q = call.request.queryParameters
                call.respond(service.list(call.user(), q["q"], q["status"], q["sort"], q["limit"], q["cursor"]))
            }
            route("/{id}") {
                get { call.respond(service.get(call.user(), call.parameters["id"]!!)) }
                patch { call.respond(service.update(call.user(), call.parameters["id"]!!, call.receive())) }
                delete {
                    service.delete(call.user(), call.parameters["id"]!!)
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/duplicate") { call.respond(HttpStatusCode.Created, service.duplicate(call.user(), call.parameters["id"]!!)) }
                post("/cards") { call.respond(HttpStatusCode.Created, service.addCard(call.user(), call.parameters["id"]!!, call.receive())) }
                put("/cards/order") {
                    service.reorder(call.user(), call.parameters["id"]!!, call.receive())
                    call.respond(mapOf("ok" to true))
                }
                get("/due") { call.respond(service.due(call.user(), call.parameters["id"]!!, call.request.queryParameters["limit"])) }
                get("/analytics") { call.respond(service.analytics(call.user(), call.parameters["id"]!!)) }
            }
        }
        route("/flashcards/{id}") {
            patch { call.respond(service.updateCard(call.user(), call.parameters["id"]!!, call.receive())) }
            delete {
                service.deleteCard(call.user(), call.parameters["id"]!!)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/review") { call.respond(service.review(call.user(), call.parameters["id"]!!, call.receive())) }
        }
    }
}

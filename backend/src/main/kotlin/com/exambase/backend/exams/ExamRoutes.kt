package com.exambase.backend.exams

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
import io.ktor.server.routing.route

private fun ApplicationCall.user() =
    principal<AuthPrincipal>()?.userId
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

fun Route.examRoutes(service: ExamService) {
    authenticate(JWT_AUTH) {
        route("/exams") {
            get {
                val q = call.request.queryParameters
                call.respond(service.list(call.user(), q["limit"], q["cursor"]))
            }
            post {
                call.respond(HttpStatusCode.Created, service.create(call.user(), call.receive<CreateExamRequest>()))
            }
            route("/{id}") {
                get { call.respond(service.get(call.user(), call.parameters["id"])) }
                post("/start") { call.respond(service.start(call.user(), call.parameters["id"])) }
            }
        }
        route("/exam-attempts/{id}") {
            post("/answers") {
                call.respond(service.saveAnswer(call.user(), call.parameters["id"], call.receive<SaveExamAnswerRequest>()))
            }
            post("/submit") { call.respond(service.submit(call.user(), call.parameters["id"])) }
            get("/result") { call.respond(service.result(call.user(), call.parameters["id"])) }
        }
    }
}

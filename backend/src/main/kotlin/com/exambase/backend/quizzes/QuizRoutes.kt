package com.exambase.backend.quizzes

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
    principal<AuthPrincipal>()?.userId ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

fun Route.quizRoutes(service: QuizService) {
    authenticate(JWT_AUTH) {
        route("/quiz-sets") {
            post {
                call.respond(HttpStatusCode.Created, service.create(call.user(), call.receive<CreateQuizSetRequest>()))
            }
            get {
                val q = call.request.queryParameters
                call.respond(service.list(call.user(), q["q"], q["status"], q["lessonId"], q["sort"], q["limit"], q["cursor"]))
            }
            route("/{id}") {
                get { call.respond(service.get(call.user(), call.parameters["id"])) }
                patch { call.respond(service.update(call.user(), call.parameters["id"], call.receive<UpdateQuizSetRequest>())) }
                delete {
                    service.delete(call.user(), call.parameters["id"])
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/duplicate") { call.respond(HttpStatusCode.Created, service.duplicate(call.user(), call.parameters["id"])) }
                put("/questions") {
                    call.respond(service.setQuestions(call.user(), call.parameters["id"], call.receive<SetQuestionsRequest>()))
                }
                post("/attempts") { call.respond(service.start(call.user(), call.parameters["id"])) }
                get("/attempts") {
                    val q = call.request.queryParameters
                    call.respond(service.attempts(call.user(), call.parameters["id"], q["limit"], q["cursor"]))
                }
            }
        }
        route("/quiz-attempts/{id}") {
            get { call.respond(service.getAttempt(call.user(), call.parameters["id"])) }
            post("/submit") { call.respond(service.submit(call.user(), call.parameters["id"], call.receive<SubmitAttemptRequest>())) }
        }
    }
}

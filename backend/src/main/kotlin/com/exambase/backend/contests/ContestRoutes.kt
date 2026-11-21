package com.exambase.backend.contests

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
import io.ktor.server.routing.route

private fun ApplicationCall.user() =
    principal<AuthPrincipal>()?.userId
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

fun Route.contestRoutes(service: ContestService) {
    authenticate(JWT_AUTH) {
        route("/contests") {
            get {
                val q = call.request.queryParameters
                call.respond(service.list(call.user(), q["status"], q["limit"], q["cursor"]))
            }
            post {
                // Learner-facing create is limited; full admin authoring arrives in Step 30.
                call.respond(HttpStatusCode.Created, service.create(call.user(), call.receive<CreateContestRequest>()))
            }
            route("/{id}") {
                get { call.respond(service.get(call.user(), call.parameters["id"])) }
                post("/join") { call.respond(service.join(call.user(), call.parameters["id"])) }
                post("/leave") {
                    service.leave(call.user(), call.parameters["id"])
                    call.respond(MessageResponse("Left contest."))
                }
                post("/start") { call.respond(service.start(call.user(), call.parameters["id"])) }
                post("/answers") {
                    call.respond(service.saveAnswer(call.user(), call.parameters["id"], call.receive<SaveAnswerRequest>()))
                }
                post("/submit") { call.respond(service.submit(call.user(), call.parameters["id"])) }
                get("/leaderboard") {
                    val q = call.request.queryParameters
                    call.respond(service.leaderboard(call.user(), call.parameters["id"], q["limit"], q["cursor"]))
                }
            }
        }
    }
}

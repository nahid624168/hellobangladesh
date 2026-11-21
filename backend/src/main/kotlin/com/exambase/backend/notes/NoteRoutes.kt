package com.exambase.backend.notes

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
import io.ktor.server.routing.route

private fun ApplicationCall.user() =
    principal<AuthPrincipal>()?.userId
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

fun Route.noteRoutes(service: NoteService) {
    authenticate(JWT_AUTH) {
        route("/note-folders") {
            post { call.respond(HttpStatusCode.Created, service.createFolder(call.user(), call.receive())) }
            get { call.respond(service.listFolders(call.user())) }
            route("/{id}") {
                patch { call.respond(service.updateFolder(call.user(), call.parameters["id"]!!, call.receive())) }
                delete {
                    service.deleteFolder(call.user(), call.parameters["id"]!!)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
        route("/notes") {
            post { call.respond(HttpStatusCode.Created, service.createNote(call.user(), call.receive())) }
            get {
                val q = call.request.queryParameters
                call.respond(
                    service.listNotes(
                        call.user(), q["folderId"], q["q"], q["tag"], q["pinned"], q["favorite"], q["limit"], q["cursor"],
                    ),
                )
            }
            get("/sync") {
                val q = call.request.queryParameters
                call.respond(service.sync(call.user(), q["since"], q["limit"]))
            }
            route("/{id}") {
                get { call.respond(service.getNote(call.user(), call.parameters["id"]!!)) }
                patch { call.respond(service.updateNote(call.user(), call.parameters["id"]!!, call.receive())) }
                delete {
                    service.deleteNote(call.user(), call.parameters["id"]!!)
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/versions") { call.respond(service.versions(call.user(), call.parameters["id"]!!)) }
                post("/versions/{version}/restore") {
                    val v = call.parameters["version"]!!.toIntOrNull()
                        ?: throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid version.")
                    call.respond(service.restore(call.user(), call.parameters["id"]!!, v))
                }
                post("/attachments") {
                    call.respond(HttpStatusCode.Created, service.addAttachment(call.user(), call.parameters["id"]!!, call.receive()))
                }
                delete("/attachments/{attachmentId}") {
                    service.removeAttachment(call.user(), call.parameters["id"]!!, call.parameters["attachmentId"]!!)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

package com.exambase.backend.admin

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
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

private fun ApplicationCall.principalUser(): AuthPrincipal =
    principal<AuthPrincipal>()
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

private fun ApplicationCall.clientIp(): String? =
    request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
        ?: request.local.remoteAddress

fun Route.adminRoutes(service: AdminService) {
    // Public-ish: feature flags for any signed-in user (UI gating)
    authenticate(JWT_AUTH) {
        get("/feature-flags") {
            call.respond(service.publicFlags())
        }

        // Authenticated users can file abuse reports
        post("/abuse-reports") {
            val body = call.receive<CreateAbuseReportRequest>()
            val userId = call.principalUser().userId
            call.respond(HttpStatusCode.Created, service.createReport(userId, body.targetType, body.targetId, body.reason, body.details))
        }

        route("/admin") {
            // ---- Users ----
            get("/users") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val q = call.request.queryParameters["q"]
                val status = call.request.queryParameters["status"]
                val role = call.request.queryParameters["role"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val cursor = call.request.queryParameters["cursor"]
                call.respond(service.searchUsers(p.userId, roles, q, status, role, limit, cursor))
            }
            get("/users/{id}") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val id = UUID.fromString(call.parameters["id"])
                call.respond(service.getUser(roles, id))
            }
            patch("/users/{id}/status") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val id = UUID.fromString(call.parameters["id"])
                val body = call.receive<UpdateUserStatusRequest>()
                call.respond(service.updateUserStatus(p.userId, roles, id, body.status, body.reason, call.clientIp()))
            }
            put("/users/{id}/roles") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val id = UUID.fromString(call.parameters["id"])
                val body = call.receive<SetRolesRequest>()
                call.respond(service.setRoles(p.userId, roles, id, body.roles, call.clientIp()))
            }

            // ---- Feature flags (admin list / super_admin write) ----
            get("/feature-flags") {
                val roles = service.rolesOf(call.principalUser().userId)
                call.respond(service.listFlags(roles))
            }
            put("/feature-flags/{key}") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val key = call.parameters["key"]!!
                val body = call.receive<UpsertFeatureFlagRequest>()
                call.respond(service.upsertFlag(p.userId, roles, key, body.enabled, body.description, call.clientIp()))
            }

            // ---- App config ----
            get("/config") {
                val roles = service.rolesOf(call.principalUser().userId)
                call.respond(service.listConfig(roles))
            }
            put("/config/{key}") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val key = call.parameters["key"]!!
                val body = call.receive<UpsertAppConfigRequest>()
                call.respond(service.upsertConfig(p.userId, roles, key, body.value, body.description, call.clientIp()))
            }

            // ---- Audit logs ----
            get("/audit-logs") {
                val roles = service.rolesOf(call.principalUser().userId)
                val actor = call.request.queryParameters["actorId"]?.let { UUID.fromString(it) }
                val resourceType = call.request.queryParameters["resourceType"]
                val action = call.request.queryParameters["action"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val cursor = call.request.queryParameters["cursor"]
                call.respond(service.listAudit(roles, actor, resourceType, action, limit, cursor))
            }

            // ---- Abuse reports (moderator+) ----
            get("/abuse-reports") {
                val roles = service.rolesOf(call.principalUser().userId)
                val status = call.request.queryParameters["status"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val cursor = call.request.queryParameters["cursor"]
                call.respond(service.listReports(roles, status, limit, cursor))
            }
            patch("/abuse-reports/{id}") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val id = UUID.fromString(call.parameters["id"])
                val body = call.receive<ResolveAbuseReportRequest>()
                call.respond(service.resolveReport(p.userId, roles, id, body.status, body.resolutionNote, call.clientIp()))
            }

            // ---- Content (editor+) ----
            get("/subjects") {
                val roles = service.rolesOf(call.principalUser().userId)
                val status = call.request.queryParameters["status"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                call.respond(service.listSubjects(roles, status, limit))
            }
            post("/subjects") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val body = call.receive<UpsertSubjectRequest>()
                call.respond(HttpStatusCode.Created, service.createSubject(p.userId, roles, body, call.clientIp()))
            }
            put("/subjects/{id}") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val id = UUID.fromString(call.parameters["id"])
                val body = call.receive<UpsertSubjectRequest>()
                call.respond(service.updateSubject(p.userId, roles, id, body, call.clientIp()))
            }
            patch("/content/{kind}/{id}/status") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val kind = call.parameters["kind"]!!
                val id = UUID.fromString(call.parameters["id"])
                val body = call.receive<SetContentStatusRequest>()
                call.respond(service.setContentStatus(p.userId, roles, kind, id, body.status, call.clientIp()))
            }
            get("/content/{kind}/{id}/children") {
                val roles = service.rolesOf(call.principalUser().userId)
                val kind = call.parameters["kind"]!!
                val id = UUID.fromString(call.parameters["id"])
                val status = call.request.queryParameters["status"]
                call.respond(service.listChildren(roles, kind, id, status))
            }
            post("/categories") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val body = call.receive<UpsertCategoryRequest>()
                call.respond(HttpStatusCode.Created, service.createCategory(p.userId, roles, body, call.clientIp()))
            }
            post("/subcategories") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val body = call.receive<UpsertSubcategoryRequest>()
                call.respond(HttpStatusCode.Created, service.createSubcategory(p.userId, roles, body, call.clientIp()))
            }
            post("/lessons") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val body = call.receive<UpsertLessonRequest>()
                call.respond(HttpStatusCode.Created, service.createLesson(p.userId, roles, body, call.clientIp()))
            }
            put("/lessons/{id}") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val id = UUID.fromString(call.parameters["id"])
                val body = call.receive<UpsertLessonRequest>()
                call.respond(service.updateLesson(p.userId, roles, id, body, call.clientIp()))
            }

            // ---- Questions ----
            get("/questions") {
                val roles = service.rolesOf(call.principalUser().userId)
                val lessonId = call.request.queryParameters["lessonId"]
                val status = call.request.queryParameters["status"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                call.respond(service.listQuestions(roles, lessonId, status, limit))
            }
            get("/questions/{id}") {
                val roles = service.rolesOf(call.principalUser().userId)
                val id = UUID.fromString(call.parameters["id"])
                call.respond(service.getQuestion(roles, id))
            }
            post("/questions") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val body = call.receive<AdminQuestionUpsertRequest>()
                call.respond(HttpStatusCode.Created, service.createQuestion(p.userId, roles, body, call.clientIp()))
            }
            put("/questions/{id}") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val id = UUID.fromString(call.parameters["id"])
                val body = call.receive<AdminQuestionUpsertRequest>()
                call.respond(service.updateQuestion(p.userId, roles, id, body, call.clientIp()))
            }
            patch("/questions/{id}/status") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val id = UUID.fromString(call.parameters["id"])
                val body = call.receive<SetQuestionStatusRequest>()
                call.respond(service.setQuestionStatus(p.userId, roles, id, body.status, body.reviewNote, call.clientIp()))
            }
            post("/questions/import") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val body = call.receive<BulkImportRequest>()
                call.respond(service.bulkImport(p.userId, roles, body, call.clientIp()))
            }

            // ---- Contests / Exams ----
            get("/contests") {
                val roles = service.rolesOf(call.principalUser().userId)
                val status = call.request.queryParameters["status"]
                call.respond(service.listContests(roles, status))
            }
            patch("/contests/{id}/status") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val id = UUID.fromString(call.parameters["id"])
                val body = call.receive<SetContestStatusRequest>()
                call.respond(service.setContestStatus(p.userId, roles, id, body.status, call.clientIp()))
            }
            get("/exams") {
                val roles = service.rolesOf(call.principalUser().userId)
                val status = call.request.queryParameters["status"]
                call.respond(service.listExams(roles, status))
            }
            patch("/exams/{id}/status") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val id = UUID.fromString(call.parameters["id"])
                val body = call.receive<SetExamStatusRequest>()
                call.respond(service.setExamStatus(p.userId, roles, id, body.status, call.clientIp()))
            }

            // ---- Destructive confirmation ----
            post("/destructive-tokens") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val body = call.receive<RequestDestructiveTokenRequest>()
                call.respond(service.requestDestructiveToken(p.userId, roles, body.action, body.resourceType, body.resourceId))
            }
            post("/destructive-confirm") {
                val p = call.principalUser()
                val roles = service.rolesOf(p.userId)
                val body = call.receive<ConfirmDestructiveRequest>()
                call.respond(service.confirmDestructive(p.userId, roles, body.token, call.clientIp()))
            }
        }
    }
}

private fun AdminService.rolesOf(userId: UUID): List<String> = loadRoles(userId)



package com.exambase.backend.auth

import com.exambase.backend.ApiException
import com.exambase.backend.DeleteAccountRequest
import com.exambase.backend.GuestRequest
import com.exambase.backend.LoginRequest
import com.exambase.backend.MessageResponse
import com.exambase.backend.PasswordResetConfirmRequest
import com.exambase.backend.PasswordResetRequest
import com.exambase.backend.RefreshRequest
import com.exambase.backend.RegisterRequest
import com.exambase.backend.UpdateProfileRequest
import com.exambase.backend.UploadAvatarRequest
import com.exambase.backend.VerifyEmailRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

const val JWT_AUTH = "auth-jwt"

/** Behind a reverse proxy, remoteHost is the proxy address; configure forwarded headers before production. */
private fun ApplicationCall.enforceRateLimit(limiter: RateLimiter) {
    if (!limiter.tryAcquire("${request.origin.remoteHost}:${request.path()}")) {
        throw ApiException(HttpStatusCode.TooManyRequests, "rate_limited", "Too many requests. Please wait a moment.")
    }
}

private fun ApplicationCall.principalOrFail(): AuthPrincipal =
    principal<AuthPrincipal>() ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Please sign in again.")

fun Route.authRoutes(auth: AuthService, limiter: RateLimiter) {
    route("/auth") {
        post("/register") {
            call.enforceRateLimit(limiter)
            call.respond(HttpStatusCode.Created, auth.register(call.receive<RegisterRequest>()))
        }
        post("/login") {
            call.enforceRateLimit(limiter)
            call.respond(auth.login(call.receive<LoginRequest>()))
        }
        post("/guest") {
            call.enforceRateLimit(limiter)
            call.respond(HttpStatusCode.Created, auth.guestLogin(call.receive<GuestRequest>().deviceName))
        }
        post("/refresh") {
            call.enforceRateLimit(limiter)
            call.respond(auth.refresh(call.receive<RefreshRequest>().refreshToken))
        }
        post("/verify-email") {
            call.enforceRateLimit(limiter)
            val body = call.receive<VerifyEmailRequest>()
            call.respond(auth.verifyEmail(body.email, body.code))
        }
        post("/password-reset/request") {
            call.enforceRateLimit(limiter)
            call.respond(auth.requestPasswordReset(call.receive<PasswordResetRequest>().email))
        }
        post("/password-reset/confirm") {
            call.enforceRateLimit(limiter)
            call.respond(auth.confirmPasswordReset(call.receive<PasswordResetConfirmRequest>()))
        }
        authenticate(JWT_AUTH) {
            post("/logout") {
                auth.logout(call.principalOrFail().sessionId)
                call.respond(MessageResponse("Signed out."))
            }
            post("/resend-verification") {
                call.enforceRateLimit(limiter)
                call.respond(auth.resendVerification(call.principalOrFail().userId))
            }
        }
    }
}

fun Route.userRoutes(auth: AuthService, limiter: RateLimiter) {
    authenticate(JWT_AUTH) {
        route("/users/me") {
            get { call.respond(auth.me(call.principalOrFail().userId)) }
            patch { call.respond(auth.updateProfile(call.principalOrFail().userId, call.receive<UpdateProfileRequest>())) }
            get("/sessions") {
                val p = call.principalOrFail()
                call.respond(auth.listSessions(p.userId, p.sessionId))
            }
            delete("/sessions/{id}") {
                val p = call.principalOrFail()
                val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                    ?: throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid session id.")
                auth.revokeSession(p.userId, id)
                call.respond(MessageResponse("Session revoked."))
            }
            post("/delete") {
                call.enforceRateLimit(limiter)
                auth.deleteAccount(call.principalOrFail().userId, call.receive<DeleteAccountRequest>().password)
                call.respond(MessageResponse("Account deleted."))
            }
            post("/avatar") {
                call.enforceRateLimit(limiter)
                call.respond(auth.uploadAvatar(call.principalOrFail().userId, call.receive<UploadAvatarRequest>()))
            }
            delete("/avatar") {
                call.respond(auth.clearAvatar(call.principalOrFail().userId))
            }
            get("/export") {
                val p = call.principalOrFail()
                call.respond(auth.exportData(p.userId, p.sessionId))
            }
        }
    }
}

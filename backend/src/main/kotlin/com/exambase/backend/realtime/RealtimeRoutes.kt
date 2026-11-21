package com.exambase.backend.realtime

import com.exambase.backend.auth.JwtService
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Contest WebSocket endpoint.
 *
 * Connect: `GET /api/v1/ws/contests/{contestId}?token=<accessToken>&resume=<resumeToken>`
 *
 * Client frames (JSON text):
 * - `{ "type": "ping", "ts": 123 }` → `{ "type": "pong", "ts": 123 }`
 * - `{ "type": "ack", "eventId": "gen-42" }` — advances resume cursor
 *
 * Server pushes `{ "type": "event", "eventId", "name", "payload", "seq" }` (at-least-once).
 */
fun Route.realtimeRoutes(hub: ContestRealtimeHub, jwt: JwtService) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    route("/ws/contests/{contestId}") {
        webSocket {
            val contestId = call.parameters["contestId"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }
            if (contestId == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid contest id"))
                return@webSocket
            }

            val token = call.request.queryParameters["token"]
                ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
            if (token.isNullOrBlank()) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing token"))
                return@webSocket
            }
            val principal = jwt.verifyAccess(token)
            if (principal == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Unauthorized"))
                return@webSocket
            }

            val resume = call.request.queryParameters["resume"]
            val session = hub.register(principal.userId, contestId, resume) { msg ->
                try {
                    runBlocking {
                        send(Frame.Text(json.encodeToString(ServerMessage.serializer(), msg)))
                    }
                } catch (_: Exception) {
                    // socket closed
                }
            }

            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val reply = hub.handleClientMessage(session.connectionId, frame.readText())
                    if (reply != null) {
                        send(Frame.Text(json.encodeToString(ServerMessage.serializer(), reply)))
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
                // client gone
            } finally {
                hub.unregister(session.connectionId)
            }
        }
    }
}

package com.exambase.backend.realtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * WebSocket JSON protocol for contest realtime (Step 25).
 *
 * Client → server:
 *   { "type": "hello", "contestId": "...", "resumeToken": "..."? }
 *   { "type": "ping", "ts": 123 }
 *   { "type": "ack", "eventId": "..." }
 *
 * Server → client:
 *   { "type": "welcome", "connectionId": "...", "resumeToken": "...", "serverTime": "..." }
 *   { "type": "pong", "ts": 123 }
 *   { "type": "event", "eventId": "...", "contestId": "...", "name": "...", "payload": {...}, "seq": 1 }
 *   { "type": "error", "code": "...", "message": "..." }
 *
 * Delivery is at-least-once. Clients dedupe by eventId. Resume replays from last acked seq when possible.
 */
object RealtimeMsgType {
    const val HELLO = "hello"
    const val WELCOME = "welcome"
    const val PING = "ping"
    const val PONG = "pong"
    const val EVENT = "event"
    const val ACK = "ack"
    const val ERROR = "error"
}

object RealtimeEventName {
    const val CONTEST_STATE = "contest.state"
    const val PARTICIPANT_UPDATE = "participant.update"
    const val QUESTION_BUNDLE = "question.bundle"
    const val ANSWER_ACK = "answer.ack"
    const val LEADERBOARD_TICK = "leaderboard.tick"
    const val CONTEST_ENDED = "contest.ended"
    const val HEARTBEAT = "heartbeat"
}

@Serializable
data class ClientMessage(
    val type: String,
    val contestId: String? = null,
    val resumeToken: String? = null,
    val ts: Long? = null,
    val eventId: String? = null,
)

@Serializable
data class ServerMessage(
    val type: String,
    val connectionId: String? = null,
    val resumeToken: String? = null,
    val serverTime: String? = null,
    val ts: Long? = null,
    val eventId: String? = null,
    val contestId: String? = null,
    val name: String? = null,
    val payload: JsonElement? = null,
    val seq: Long? = null,
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class RealtimeEnvelope(
    val eventId: String,
    val contestId: String,
    val name: String,
    val payloadJson: String,
    val seq: Long,
    val createdAt: String,
)

package com.exambase.backend.realtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-process registry of contest WebSocket sessions + fan-out via [EventBus].
 *
 * Design notes:
 * - Publish path writes outbox first (when using JDBC), then bus — at-least-once.
 * - Consumers (local sockets) are idempotent by eventId.
 * - Resume token is `contestId:userId:lastSeq`; on hello we replay buffered events after lastSeq
 *   from an in-memory ring (last N per contest). Full durable replay is outbox-based later.
 */
class ContestRealtimeHub(
    private val bus: EventBus = InMemoryEventBus(),
    private val outbox: Outbox = InMemoryOutbox(),
    private val clock: Clock = Clock.systemUTC(),
    private val ringCapacity: Int = 200,
) {
    data class Session(
        val connectionId: String,
        val userId: UUID,
        val contestId: UUID,
        val resumeToken: String,
        @Volatile var lastSeq: Long = 0,
        @Volatile var lastSeenAt: Instant = Instant.now(),
        val send: (ServerMessage) -> Unit,
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val byContest = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Session>>()
    private val seq = AtomicLong(0)
    private val rings = ConcurrentHashMap<UUID, ArrayDeque<RealtimeEnvelope>>()
    private val subscriptions = ConcurrentHashMap<UUID, EventBusSubscription>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun channel(contestId: UUID) = "contest:$contestId"

    fun register(userId: UUID, contestId: UUID, resumeToken: String?, send: (ServerMessage) -> Unit): Session {
        val connectionId = UUID.randomUUID().toString()
        val token = resumeToken?.takeIf { it.startsWith("${contestId}:$userId:") }
            ?: "$contestId:$userId:0"
        val lastSeq = token.substringAfterLast(":").toLongOrNull() ?: 0L
        val session = Session(connectionId, userId, contestId, token, lastSeq, clock.instant(), send)
        sessions[connectionId] = session
        byContest.getOrPut(contestId) { ConcurrentHashMap() }[connectionId] = session
        ensureSubscribed(contestId)

        // Replay ring after lastSeq
        synchronized(ringLock(contestId)) {
            rings[contestId]?.filter { it.seq > lastSeq }?.forEach { env ->
                deliverToSession(session, env)
            }
        }

        send(
            ServerMessage(
                type = RealtimeMsgType.WELCOME,
                connectionId = connectionId,
                resumeToken = "$contestId:$userId:${session.lastSeq}",
                serverTime = clock.instant().toString(),
            ),
        )
        return session
    }

    fun unregister(connectionId: String) {
        val session = sessions.remove(connectionId) ?: return
        byContest[session.contestId]?.remove(connectionId)
        if (byContest[session.contestId].isNullOrEmpty()) {
            byContest.remove(session.contestId)
            subscriptions.remove(session.contestId)?.cancel()
        }
    }

    fun touch(connectionId: String) {
        sessions[connectionId]?.lastSeenAt = clock.instant()
    }

    fun ack(connectionId: String, eventId: String) {
        val session = sessions[connectionId] ?: return
        // eventId format outbox-N or gen-N
        val n = eventId.substringAfterLast("-").toLongOrNull() ?: return
        if (n > session.lastSeq) session.lastSeq = n
    }

    fun connectionCount(contestId: UUID): Int = byContest[contestId]?.size ?: 0

    fun totalConnections(): Int = sessions.size

    /**
     * Publish a contest event. Writes outbox, then bus. Local subscribers deliver to sockets.
     * Payload must be a JSON object string.
     */
    fun publish(contestId: UUID, name: String, payload: Map<String, String> = emptyMap()) {
        val next = seq.incrementAndGet()
        val payloadJson = JsonObject(payload.mapValues { JsonPrimitive(it.value) }).toString()
        val envelope = RealtimeEnvelope(
            eventId = "gen-$next",
            contestId = contestId.toString(),
            name = name,
            payloadJson = payloadJson,
            seq = next,
            createdAt = clock.instant().toString(),
        )
        outbox.enqueue(channel(contestId), name, payloadJson)
        remember(contestId, envelope)
        bus.publish(channel(contestId), envelope)
    }

    /** Drain outbox and re-publish (worker / recovery). Idempotent consumers. */
    fun flushOutbox(limit: Int = 100): Int {
        val batch = outbox.drain(limit)
        if (batch.isEmpty()) return 0
        batch.forEach { (_, env) ->
            val contestId = runCatching { UUID.fromString(env.contestId) }.getOrNull() ?: return@forEach
            remember(contestId, env)
            bus.publish(channel(contestId), env)
        }
        outbox.markPublished(batch.map { it.first })
        return batch.size
    }

    fun handleClientMessage(connectionId: String, raw: String): ServerMessage? {
        val msg = runCatching { json.decodeFromString(ClientMessage.serializer(), raw) }.getOrElse {
            return ServerMessage(type = RealtimeMsgType.ERROR, code = "invalid_json", message = "Invalid message.")
        }
        val session = sessions[connectionId]
        when (msg.type) {
            RealtimeMsgType.PING -> {
                touch(connectionId)
                return ServerMessage(type = RealtimeMsgType.PONG, ts = msg.ts ?: clock.millis())
            }
            RealtimeMsgType.ACK -> {
                if (msg.eventId != null) ack(connectionId, msg.eventId)
                touch(connectionId)
                return null
            }
            RealtimeMsgType.HELLO -> {
                // Re-hello on same connection is ignored; use register for new sockets.
                touch(connectionId)
                return null
            }
            else -> {
                return ServerMessage(type = RealtimeMsgType.ERROR, code = "unknown_type", message = "Unknown message type.")
            }
        }
    }

    private fun ensureSubscribed(contestId: UUID) {
        subscriptions.computeIfAbsent(contestId) {
            bus.subscribe(channel(contestId)) { env ->
                byContest[contestId]?.values?.forEach { session ->
                    deliverToSession(session, env)
                }
            }
        }
    }

    private fun deliverToSession(session: Session, env: RealtimeEnvelope) {
        if (env.seq <= session.lastSeq) return // already seen
        val payload: JsonElement = runCatching {
            json.parseToJsonElement(env.payloadJson)
        }.getOrElse { JsonObject(emptyMap()) }
        runCatching {
            session.send(
                ServerMessage(
                    type = RealtimeMsgType.EVENT,
                    eventId = env.eventId,
                    contestId = env.contestId,
                    name = env.name,
                    payload = payload,
                    seq = env.seq,
                    serverTime = env.createdAt,
                ),
            )
            session.lastSeq = env.seq
        }
    }

    private fun remember(contestId: UUID, env: RealtimeEnvelope) {
        synchronized(ringLock(contestId)) {
            val ring = rings.getOrPut(contestId) { ArrayDeque() }
            ring.addLast(env)
            while (ring.size > ringCapacity) ring.removeFirst()
        }
    }

    private fun ringLock(contestId: UUID) = rings.getOrPut(contestId) { ArrayDeque() }
}

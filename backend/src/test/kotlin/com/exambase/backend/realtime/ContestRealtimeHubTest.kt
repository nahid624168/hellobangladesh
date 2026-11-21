package com.exambase.backend.realtime

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContestRealtimeHubTest {
    @Test
    fun welcomeAndPingPong() {
        val hub = ContestRealtimeHub()
        val contestId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val out = CopyOnWriteArrayList<ServerMessage>()
        val session = hub.register(userId, contestId, null) { out += it }
        assertEquals(1, out.count { it.type == RealtimeMsgType.WELCOME })
        assertNotNull(out.first().connectionId)
        assertNotNull(out.first().resumeToken)

        val pong = hub.handleClientMessage(session.connectionId, """{"type":"ping","ts":42}""")
        assertEquals(RealtimeMsgType.PONG, pong?.type)
        assertEquals(42L, pong?.ts)
    }

    @Test
    fun publishDeliversToSubscribers() {
        val hub = ContestRealtimeHub()
        val contestId = UUID.randomUUID()
        val out = CopyOnWriteArrayList<ServerMessage>()
        hub.register(UUID.randomUUID(), contestId, null) { out += it }
        out.clear()
        hub.publish(contestId, RealtimeEventName.LEADERBOARD_TICK, mapOf("score" to "10"))
        val events = out.filter { it.type == RealtimeMsgType.EVENT }
        assertEquals(1, events.size)
        assertEquals(RealtimeEventName.LEADERBOARD_TICK, events[0].name)
        assertTrue(events[0].eventId!!.startsWith("gen-"))
    }

    @Test
    fun resumeReplaysMissedEvents() {
        val hub = ContestRealtimeHub()
        val contestId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val firstOut = CopyOnWriteArrayList<ServerMessage>()
        val s1 = hub.register(userId, contestId, null) { firstOut += it }
        hub.publish(contestId, RealtimeEventName.PARTICIPANT_UPDATE, mapOf("status" to "joined"))
        hub.publish(contestId, RealtimeEventName.LEADERBOARD_TICK, mapOf("n" to "1"))
        val lastSeq = firstOut.filter { it.type == RealtimeMsgType.EVENT }.maxOf { it.seq ?: 0 }
        hub.unregister(s1.connectionId)

        val resumeOut = CopyOnWriteArrayList<ServerMessage>()
        val token = "$contestId:$userId:${lastSeq - 1}"
        hub.register(userId, contestId, token) { resumeOut += it }
        val replayed = resumeOut.filter { it.type == RealtimeMsgType.EVENT }
        assertTrue(replayed.isNotEmpty(), "expected replay of events after lastSeq-1")
        assertTrue(replayed.all { (it.seq ?: 0) > lastSeq - 1 })
    }

    @Test
    fun ackAdvancesResumeCursor() {
        val hub = ContestRealtimeHub()
        val contestId = UUID.randomUUID()
        val out = CopyOnWriteArrayList<ServerMessage>()
        val s = hub.register(UUID.randomUUID(), contestId, null) { out += it }
        hub.publish(contestId, RealtimeEventName.HEARTBEAT, emptyMap())
        val eventId = out.last { it.type == RealtimeMsgType.EVENT }.eventId!!
        hub.handleClientMessage(s.connectionId, """{"type":"ack","eventId":"$eventId"}""")
        // next welcome on new connection with token should not replay that event if seq matches
        val seq = eventId.substringAfterLast("-").toLong()
        val out2 = CopyOnWriteArrayList<ServerMessage>()
        hub.unregister(s.connectionId)
        hub.register(s.userId, contestId, "$contestId:${s.userId}:$seq") { out2 += it }
        assertTrue(out2.none { it.type == RealtimeMsgType.EVENT && it.eventId == eventId })
    }

    @Test
    fun massJoinDoesNotThrow() {
        val hub = ContestRealtimeHub()
        val contestId = UUID.randomUUID()
        val sessions = (1..50).map {
            hub.register(UUID.randomUUID(), contestId, null) { }
        }
        assertEquals(50, hub.connectionCount(contestId))
        hub.publish(contestId, RealtimeEventName.CONTEST_STATE, mapOf("status" to "live"))
        sessions.forEach { hub.unregister(it.connectionId) }
        assertEquals(0, hub.connectionCount(contestId))
    }

    @Test
    fun flushOutboxRepublishes() {
        val outbox = InMemoryOutbox()
        val bus = InMemoryEventBus()
        val received = CopyOnWriteArrayList<RealtimeEnvelope>()
        val contestId = UUID.randomUUID()
        bus.subscribe("contest:$contestId") { received += it }
        outbox.enqueue("contest:$contestId", RealtimeEventName.CONTEST_ENDED, """{"ok":true}""")
        val hub = ContestRealtimeHub(bus = bus, outbox = outbox)
        val n = hub.flushOutbox()
        assertEquals(1, n)
        assertEquals(1, received.size)
        assertEquals(RealtimeEventName.CONTEST_ENDED, received[0].name)
    }
}

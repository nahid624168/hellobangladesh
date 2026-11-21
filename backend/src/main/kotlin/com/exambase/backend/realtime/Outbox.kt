package com.exambase.backend.realtime

import com.exambase.backend.Database
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

interface Outbox {
    fun enqueue(channel: String, eventType: String, payloadJson: String)
    /** Drain pending rows and mark published. Returns envelopes in order. */
    fun drain(limit: Int = 100): List<Pair<Long, RealtimeEnvelope>>
    fun markPublished(ids: List<Long>)
}

/** Process-local outbox for tests and single-node without DB. */
class InMemoryOutbox : Outbox {
    private data class Row(val id: Long, val channel: String, val eventType: String, val payload: String, var published: Boolean = false)
    private val rows = ConcurrentLinkedQueue<Row>()
    private var seq = 0L

    override fun enqueue(channel: String, eventType: String, payloadJson: String) {
        val id = synchronized(this) { ++seq }
        rows.add(Row(id, channel, eventType, payloadJson))
    }

    override fun drain(limit: Int): List<Pair<Long, RealtimeEnvelope>> {
        val out = ArrayList<Pair<Long, RealtimeEnvelope>>()
        for (row in rows) {
            if (out.size >= limit) break
            if (row.published) continue
            out += row.id to RealtimeEnvelope(
                eventId = "outbox-${row.id}",
                contestId = row.channel.removePrefix("contest:"),
                name = row.eventType,
                payloadJson = row.payload,
                seq = row.id,
                createdAt = Instant.now().toString(),
            )
        }
        return out
    }

    override fun markPublished(ids: List<Long>) {
        val set = ids.toSet()
        rows.forEach { if (it.id in set) it.published = true }
    }
}

class JdbcOutbox(private val db: Database) : Outbox {
    override fun enqueue(channel: String, eventType: String, payloadJson: String) {
        db.withConnection { c ->
            c.prepareStatement(
                "INSERT INTO realtime_outbox (channel, event_type, payload) VALUES (?, ?, ?::jsonb)",
            ).use { ps ->
                ps.setString(1, channel)
                ps.setString(2, eventType)
                ps.setString(3, payloadJson)
                ps.executeUpdate()
            }
        }
    }

    override fun drain(limit: Int): List<Pair<Long, RealtimeEnvelope>> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, channel, event_type, payload::text, created_at
            FROM realtime_outbox
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT ?
            """,
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val id = rs.getLong("id")
                        val channel = rs.getString("channel")
                        add(
                            id to RealtimeEnvelope(
                                eventId = "outbox-$id",
                                contestId = channel.removePrefix("contest:"),
                                name = rs.getString("event_type"),
                                payloadJson = rs.getString("payload"),
                                seq = id,
                                createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun markPublished(ids: List<Long>) {
        if (ids.isEmpty()) return
        db.withConnection { c ->
            c.prepareStatement(
                "UPDATE realtime_outbox SET published_at = ? WHERE id = ANY (?)",
            ).use { ps ->
                ps.setTimestamp(1, Timestamp.from(Instant.now()))
                ps.setArray(2, c.createArrayOf("bigint", ids.toTypedArray()))
                ps.executeUpdate()
            }
        }
    }
}

package com.exambase.backend.ai

import com.exambase.backend.Database
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class JdbcAiRepository(private val db: Database) : AiRepository {

    override fun consent(userId: UUID): ConsentRecord? = db.withConnection { c ->
        c.prepareStatement("SELECT version, granted_at, revoked_at FROM ai_consents WHERE user_id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs ->
                if (rs.next()) ConsentRecord(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getTimestamp(3)?.toInstant()) else null
            }
        }
    }

    override fun grantConsent(userId: UUID, version: String, now: Instant) {
        db.withConnection { c ->
            c.prepareStatement(
                """
                INSERT INTO ai_consents (user_id, version, granted_at, revoked_at) VALUES (?, ?, ?, NULL)
                ON CONFLICT (user_id) DO UPDATE SET version = EXCLUDED.version, granted_at = EXCLUDED.granted_at, revoked_at = NULL
                """,
            ).use { ps ->
                ps.setObject(1, userId); ps.setString(2, version); ps.setTimestamp(3, Timestamp.from(now))
                ps.executeUpdate()
            }
        }
    }

    override fun revokeConsent(userId: UUID, now: Instant) {
        db.withConnection { c ->
            c.prepareStatement("UPDATE ai_consents SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL").use { ps ->
                ps.setTimestamp(1, Timestamp.from(now)); ps.setObject(2, userId)
                ps.executeUpdate()
            }
        }
    }

    override fun usageSince(userId: UUID, since: Instant): UsageTotals = db.withConnection { c ->
        c.prepareStatement(
            "SELECT COUNT(*), COALESCE(SUM(prompt_tokens + completion_tokens), 0) FROM ai_usage WHERE user_id = ? AND created_at >= ? AND status IN ('ok', 'blocked')",
        ).use { ps ->
            ps.setObject(1, userId); ps.setTimestamp(2, Timestamp.from(since))
            ps.executeQuery().use { rs -> rs.next(); UsageTotals(rs.getInt(1), rs.getLong(2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        }
    }

    override fun globalTokensSince(since: Instant): Int = db.withConnection { c ->
        c.prepareStatement("SELECT COALESCE(SUM(prompt_tokens + completion_tokens), 0) FROM ai_usage WHERE created_at >= ?").use { ps ->
            ps.setTimestamp(1, Timestamp.from(since))
            ps.executeQuery().use { rs -> rs.next(); rs.getLong(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        }
    }

    override fun blockedSince(userId: UUID, since: Instant): Int = db.withConnection { c ->
        c.prepareStatement("SELECT COUNT(*) FROM ai_usage WHERE user_id = ? AND created_at >= ? AND status = 'blocked'").use { ps ->
            ps.setObject(1, userId); ps.setTimestamp(2, Timestamp.from(since))
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    override fun reserveQuota(
        userId: UUID, dayStart: Instant, requestLimit: Int, userTokenLimit: Int, globalTokenLimit: Int, tokens: Int,
    ): AiReservationResult = db.transaction { c ->
        val budget = tokens.coerceAtLeast(1)
        c.prepareStatement("INSERT INTO ai_global_quota_daily (day_start) VALUES (?) ON CONFLICT (day_start) DO NOTHING").use {
            it.setTimestamp(1, Timestamp.from(dayStart)); it.executeUpdate()
        }
        c.prepareStatement("INSERT INTO ai_user_quota_daily (user_id, day_start) VALUES (?, ?) ON CONFLICT (user_id, day_start) DO NOTHING").use {
            it.setObject(1, userId); it.setTimestamp(2, Timestamp.from(dayStart)); it.executeUpdate()
        }
        // Lock global first, then user: every transaction follows the same order and avoids lock inversion.
        val globalUsed = c.prepareStatement("SELECT tokens_reserved FROM ai_global_quota_daily WHERE day_start = ? FOR UPDATE").use { ps ->
            ps.setTimestamp(1, Timestamp.from(dayStart)); ps.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
        val userUsed = c.prepareStatement("SELECT requests_reserved, tokens_reserved FROM ai_user_quota_daily WHERE user_id = ? AND day_start = ? FOR UPDATE").use { ps ->
            ps.setObject(1, userId); ps.setTimestamp(2, Timestamp.from(dayStart))
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) to rs.getLong(2) }
        }
        when {
            userUsed.first >= requestLimit || userUsed.second + budget > userTokenLimit -> AiReservationResult.USER_LIMIT
            globalUsed + budget > globalTokenLimit -> AiReservationResult.GLOBAL_LIMIT
            else -> {
                c.prepareStatement("UPDATE ai_user_quota_daily SET requests_reserved = requests_reserved + 1, tokens_reserved = tokens_reserved + ? WHERE user_id = ? AND day_start = ?").use { ps ->
                    ps.setInt(1, budget); ps.setObject(2, userId); ps.setTimestamp(3, Timestamp.from(dayStart)); ps.executeUpdate()
                }
                c.prepareStatement("UPDATE ai_global_quota_daily SET tokens_reserved = tokens_reserved + ? WHERE day_start = ?").use { ps ->
                    ps.setInt(1, budget); ps.setTimestamp(2, Timestamp.from(dayStart)); ps.executeUpdate()
                }
                AiReservationResult.RESERVED
            }
        }
    }

    override fun settleQuota(userId: UUID, dayStart: Instant, reservedTokens: Int, actualTokens: Int, countRequest: Boolean) {
        db.transaction { c ->
            c.prepareStatement("SELECT tokens_reserved FROM ai_global_quota_daily WHERE day_start = ? FOR UPDATE").use { ps ->
                ps.setTimestamp(1, Timestamp.from(dayStart)); ps.executeQuery().use { it.next() }
            }
            c.prepareStatement("SELECT requests_reserved, tokens_reserved FROM ai_user_quota_daily WHERE user_id = ? AND day_start = ? FOR UPDATE").use { ps ->
                ps.setObject(1, userId); ps.setTimestamp(2, Timestamp.from(dayStart)); ps.executeQuery().use { it.next() }
            }
            val delta = actualTokens.coerceAtLeast(0) - reservedTokens.coerceAtLeast(0)
            c.prepareStatement("UPDATE ai_user_quota_daily SET requests_reserved = GREATEST(0, requests_reserved - ?), tokens_reserved = GREATEST(0, tokens_reserved + ?) WHERE user_id = ? AND day_start = ?").use { ps ->
                ps.setInt(1, if (countRequest) 0 else 1); ps.setInt(2, delta); ps.setObject(3, userId); ps.setTimestamp(4, Timestamp.from(dayStart)); ps.executeUpdate()
            }
            c.prepareStatement("UPDATE ai_global_quota_daily SET tokens_reserved = GREATEST(0, tokens_reserved + ?) WHERE day_start = ?").use { ps ->
                ps.setInt(1, delta); ps.setTimestamp(2, Timestamp.from(dayStart)); ps.executeUpdate()
            }
        }
    }

    override fun recordUsage(u: UsageRecord) {
        db.withConnection { c ->
            c.prepareStatement(
                """
                INSERT INTO ai_usage (user_id, task, status, input_chars, output_chars, prompt_tokens, completion_tokens, latency_ms, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            ).use { ps ->
                ps.setObject(1, u.userId); ps.setString(2, u.task); ps.setString(3, u.status)
                ps.setInt(4, u.inputChars); ps.setInt(5, u.outputChars); ps.setInt(6, u.promptTokens)
                ps.setInt(7, u.completionTokens); ps.setInt(8, u.latencyMs); ps.setTimestamp(9, Timestamp.from(u.at))
                ps.executeUpdate()
            }
        }
    }

    override fun createConversation(userId: UUID, task: String, title: String, now: Instant): UUID = db.withConnection { c ->
        c.prepareStatement("INSERT INTO ai_conversations (user_id, task, title, created_at, updated_at) VALUES (?, ?, ?, ?, ?) RETURNING id").use { ps ->
            ps.setObject(1, userId); ps.setString(2, task); ps.setString(3, title)
            ps.setTimestamp(4, Timestamp.from(now)); ps.setTimestamp(5, Timestamp.from(now))
            ps.executeQuery().use { rs -> rs.next(); rs.getObject(1, UUID::class.java) }
        }
    }

    override fun conversationTask(userId: UUID, id: UUID): String? = db.withConnection { c ->
        c.prepareStatement("SELECT task FROM ai_conversations WHERE id = ? AND user_id = ?").use { ps ->
            ps.setObject(1, id); ps.setObject(2, userId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    override fun recentMessages(userId: UUID, id: UUID, limit: Int): List<StoredMessage> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT * FROM (
              SELECT m.id, m.role, m.content, m.created_at FROM ai_messages m
              JOIN ai_conversations cv ON cv.id = m.conversation_id
              WHERE m.conversation_id = ? AND cv.user_id = ?
              ORDER BY m.created_at DESC, m.id DESC LIMIT ?
            ) t ORDER BY created_at, id
            """,
        ).use { ps ->
            ps.setObject(1, id); ps.setObject(2, userId); ps.setInt(3, limit)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<StoredMessage>()
                while (rs.next()) out += StoredMessage(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant())
                out
            }
        }
    }

    override fun addMessages(userId: UUID, id: UUID, user: String, assistant: String, now: Instant): Boolean = db.transaction { c ->
        val owned = c.prepareStatement("SELECT 1 FROM ai_conversations WHERE id = ? AND user_id = ?").use { ps ->
            ps.setObject(1, id); ps.setObject(2, userId)
            ps.executeQuery().use { it.next() }
        }
        if (!owned) return@transaction false
        c.prepareStatement("INSERT INTO ai_messages (conversation_id, role, content, created_at) VALUES (?, ?, ?, ?)").use { ps ->
            ps.setObject(1, id); ps.setString(2, "user"); ps.setString(3, user); ps.setTimestamp(4, Timestamp.from(now)); ps.addBatch()
            ps.setObject(1, id); ps.setString(2, "assistant"); ps.setString(3, assistant); ps.setTimestamp(4, Timestamp.from(now.plusMillis(1))); ps.addBatch()
            ps.executeBatch()
        }
        c.prepareStatement("UPDATE ai_conversations SET updated_at = ? WHERE id = ?").use { ps ->
            ps.setTimestamp(1, Timestamp.from(now)); ps.setObject(2, id); ps.executeUpdate()
        }
        true
    }

    override fun listConversations(userId: UUID, limit: Int, offset: Int): Page<AiConversationItem> = db.withConnection { c ->
        c.prepareStatement(
            "SELECT id, task, title, updated_at FROM ai_conversations WHERE user_id = ? ORDER BY updated_at DESC, id LIMIT ? OFFSET ?",
        ).use { ps ->
            ps.setObject(1, userId); ps.setInt(2, limit + 1); ps.setInt(3, offset)
            ps.executeQuery().use { rs ->
                val rows = mutableListOf<AiConversationItem>()
                while (rs.next()) rows += AiConversationItem(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant().toString())
                Paging.page(rows, limit, offset)
            }
        }
    }

    override fun getConversation(userId: UUID, id: UUID): AiConversationDetail? = db.withConnection { c ->
        val head = c.prepareStatement("SELECT task, title FROM ai_conversations WHERE id = ? AND user_id = ?").use { ps ->
            ps.setObject(1, id); ps.setObject(2, userId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) to rs.getString(2) else null }
        } ?: return@withConnection null
        val messages = c.prepareStatement("SELECT id, role, content, created_at FROM ai_messages WHERE conversation_id = ? ORDER BY created_at, id").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<AiMessageDto>()
                while (rs.next()) out += AiMessageDto(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant().toString())
                out
            }
        }
        AiConversationDetail(id.toString(), head.first, head.second, messages)
    }

    override fun deleteConversation(userId: UUID, id: UUID): Boolean = db.withConnection { c ->
        c.prepareStatement("DELETE FROM ai_conversations WHERE id = ? AND user_id = ?").use { ps ->
            ps.setObject(1, id); ps.setObject(2, userId)
            ps.executeUpdate() > 0
        }
    }

    override fun deleteAllConversations(userId: UUID): Int = db.withConnection { c ->
        c.prepareStatement("DELETE FROM ai_conversations WHERE user_id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.executeUpdate()
        }
    }
}

package com.exambase.ai.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents the decision table from [ConflictPolicy] without Room.
 */
class ConflictPolicyTest {
    data class Local(val dirty: Boolean, val baseVersion: Int, val conflict: Boolean = false)
    data class Server(val version: Int)

    private fun decide(local: Local?, server: Server): String = when {
        local == null -> "insert"
        local.dirty && server.version > local.baseVersion -> "conflict"
        !local.dirty -> "apply_server"
        else -> "keep_local_pending_push"
    }

    @Test fun cleanLocalTakesServer() {
        assertEquals("apply_server", decide(Local(dirty = false, baseVersion = 1), Server(2)))
    }

    @Test fun dirtyWithNewerServerIsConflict() {
        assertEquals("conflict", decide(Local(dirty = true, baseVersion = 1), Server(3)))
    }

    @Test fun dirtySameBaseWaitsForPush() {
        assertEquals("keep_local_pending_push", decide(Local(dirty = true, baseVersion = 2), Server(2)))
    }

    @Test fun missingLocalInserts() {
        assertEquals("insert", decide(null, Server(1)))
    }
}

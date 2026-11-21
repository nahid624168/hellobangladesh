package com.exambase.backend.admin

import com.exambase.backend.ApiException
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdminServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC)
    private val repo = InMemoryAdminRepository()
    private val service = AdminService(repo, clock)

    private val adminId = UUID.randomUUID()
    private val modId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val superId = UUID.randomUUID()

    init {
        repo.seedUser(UserRow(adminId, "admin@ex.com", "Admin", "active", Instant.now(), listOf(Roles.ADMIN), Instant.now(), Instant.now()))
        repo.seedUser(UserRow(modId, "mod@ex.com", "Mod", "active", Instant.now(), listOf(Roles.MODERATOR), Instant.now(), Instant.now()))
        repo.seedUser(UserRow(userId, "user@ex.com", "User", "active", Instant.now(), listOf(Roles.USER), Instant.now(), Instant.now()))
        repo.seedUser(UserRow(superId, "super@ex.com", "Super", "active", Instant.now(), listOf(Roles.SUPER_ADMIN), Instant.now(), Instant.now()))
    }

    @Test
    fun `moderator can search users but cannot change status`() {
        val list = service.searchUsers(modId, listOf(Roles.MODERATOR), null, null, null, 20, null)
        assertTrue(list.items.isNotEmpty())

        val ex = assertFailsWith<ApiException> {
            service.updateUserStatus(modId, listOf(Roles.MODERATOR), userId, "suspended", null, null)
        }
        assertEquals(HttpStatusCode.Forbidden, ex.status)
    }

    @Test
    fun `admin can suspend user`() {
        val dto = service.updateUserStatus(adminId, listOf(Roles.ADMIN), userId, "suspended", "spam", null)
        assertEquals("suspended", dto.status)
    }

    @Test
    fun `admin cannot suspend another admin`() {
        val otherAdmin = UUID.randomUUID()
        repo.seedUser(UserRow(otherAdmin, "a2@ex.com", "A2", "active", null, listOf(Roles.ADMIN), Instant.now(), Instant.now()))
        val ex = assertFailsWith<ApiException> {
            service.updateUserStatus(adminId, listOf(Roles.ADMIN), otherAdmin, "suspended", null, null)
        }
        assertEquals(HttpStatusCode.Forbidden, ex.status)
    }

    @Test
    fun `super_admin can suspend admin`() {
        val otherAdmin = UUID.randomUUID()
        repo.seedUser(UserRow(otherAdmin, "a2@ex.com", "A2", "active", null, listOf(Roles.ADMIN), Instant.now(), Instant.now()))
        val dto = service.updateUserStatus(superId, listOf(Roles.SUPER_ADMIN), otherAdmin, "suspended", null, null)
        assertEquals("suspended", dto.status)
    }

    @Test
    fun `only super_admin can upsert feature flags`() {
        val ex = assertFailsWith<ApiException> {
            service.upsertFlag(adminId, listOf(Roles.ADMIN), "ai_assistants", true, null, null)
        }
        assertEquals(HttpStatusCode.Forbidden, ex.status)

        val flag = service.upsertFlag(superId, listOf(Roles.SUPER_ADMIN), "ai_assistants", true, "test", null)
        assertTrue(flag.enabled)
    }

    @Test
    fun `user cannot list abuse reports`() {
        val ex = assertFailsWith<ApiException> {
            service.listReports(listOf(Roles.USER), null, 20, null)
        }
        assertEquals(HttpStatusCode.Forbidden, ex.status)
    }

    @Test
    fun `moderator can list and resolve reports`() {
        val report = service.createReport(userId, "user", userId.toString(), "spam", null)
        val list = service.listReports(listOf(Roles.MODERATOR), "open", 20, null)
        assertTrue(list.items.any { it.id == report.id })

        val resolved = service.resolveReport(modId, listOf(Roles.MODERATOR), UUID.fromString(report.id), "resolved", "ok", null)
        assertEquals("resolved", resolved.status)
    }

    @Test
    fun `cannot change own roles or status`() {
        assertFailsWith<ApiException> {
            service.updateUserStatus(adminId, listOf(Roles.ADMIN), adminId, "suspended", null, null)
        }
        assertFailsWith<ApiException> {
            service.setRoles(adminId, listOf(Roles.ADMIN), adminId, listOf(Roles.USER), null)
        }
    }
}

    @Test
    fun `editor can create subject and question, user cannot`() {
        val editorId = UUID.randomUUID()
        repo.seedUser(UserRow(editorId, "ed@ex.com", "Ed", "active", Instant.now(), listOf(Roles.EDITOR), Instant.now(), Instant.now()))
        val sub = service.createSubject(editorId, listOf(Roles.EDITOR), UpsertSubjectRequest(title = "Physics", status = "draft"), null)
        assertEquals("Physics", sub.title)

        val q = service.createQuestion(
            editorId, listOf(Roles.EDITOR),
            AdminQuestionUpsertRequest(
                lessonId = UUID.randomUUID().toString(),
                type = "single_choice",
                questionText = "What is F?",
                options = listOf(
                    AdminQuestionOptionInput(text = "Force", isCorrect = true),
                    AdminQuestionOptionInput(text = "Mass", isCorrect = false),
                ),
            ),
            null,
        )
        assertEquals("draft", q.status)

        val ex = assertFailsWith<ApiException> {
            service.createSubject(userId, listOf(Roles.USER), UpsertSubjectRequest(title = "X"), null)
        }
        assertEquals(HttpStatusCode.Forbidden, ex.status)
    }

    @Test
    fun `only moderator can publish question`() {
        val editorId = UUID.randomUUID()
        repo.seedUser(UserRow(editorId, "ed2@ex.com", "Ed2", "active", null, listOf(Roles.EDITOR), Instant.now(), Instant.now()))
        val q = service.createQuestion(
            editorId, listOf(Roles.EDITOR),
            AdminQuestionUpsertRequest(
                lessonId = UUID.randomUUID().toString(),
                type = "true_false",
                questionText = "Sky is blue?",
                options = listOf(
                    AdminQuestionOptionInput(text = "True", isCorrect = true),
                    AdminQuestionOptionInput(text = "False", isCorrect = false),
                ),
                status = "review",
            ),
            null,
        )
        // editor can set review
        service.setQuestionStatus(editorId, listOf(Roles.EDITOR), UUID.fromString(q.id), "review", null, null)
        // editor cannot publish
        val ex = assertFailsWith<ApiException> {
            service.setQuestionStatus(editorId, listOf(Roles.EDITOR), UUID.fromString(q.id), "published", null, null)
        }
        assertEquals(HttpStatusCode.Forbidden, ex.status)
        // moderator can publish
        val pub = service.setQuestionStatus(modId, listOf(Roles.MODERATOR), UUID.fromString(q.id), "published", "LGTM", null)
        assertEquals("published", pub.status)
    }

    @Test
    fun `destructive token required flow`() {
        val qid = UUID.randomUUID()
        repo.seedQuestion(
            AdminQuestionRow(qid, UUID.randomUUID(), "short_answer", "Q", null, "", "easy", 1.0, 0.0, "published", 0, null, Instant.now()),
        )
        val tokenDto = service.requestDestructiveToken(adminId, listOf(Roles.ADMIN), "archive_question", "question", qid.toString())
        assertTrue(tokenDto.token.isNotBlank())
        // user cannot request
        assertFailsWith<ApiException> {
            service.requestDestructiveToken(userId, listOf(Roles.USER), "archive_question", "question", qid.toString())
        }
        val msg = service.confirmDestructive(adminId, listOf(Roles.ADMIN), tokenDto.token, null)
        assertTrue(msg.message.contains("archive_question"))
        assertEquals("archived", repo.getQuestionAdmin(qid)!!.status)
    }

    @Test
    fun `admin can change contest status, editor cannot`() {
        val cid = UUID.randomUUID()
        repo.seedContest(AdminContestListRow(cid, "Weekly", "scheduled", Instant.now(), Instant.now().plusSeconds(3600), 0))
        val ex = assertFailsWith<ApiException> {
            service.setContestStatus(modId, listOf(Roles.EDITOR), cid, "cancelled", null)
        }
        assertEquals(HttpStatusCode.Forbidden, ex.status)
        val dto = service.setContestStatus(adminId, listOf(Roles.ADMIN), cid, "cancelled", null)
        assertEquals("cancelled", dto.status)
    }

package com.exambase.backend.exams

import com.exambase.backend.ApiException
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import com.exambase.backend.questions.AnswerKey
import com.exambase.backend.questions.Grader
import com.exambase.backend.questions.QuestionType
import com.exambase.backend.questions.Submission
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.round

/**
 * Examination system (Step 27). Tournaments/teams intentionally omitted pending owner checkpoint.
 */
class ExamService(
    private val repo: ExamRepository,
    private val emailVerified: (UUID) -> Boolean = { true },
    private val clock: Clock = Clock.systemUTC(),
) {
    fun list(userId: UUID, limitRaw: String?, cursor: String?): Page<ExamSummary> {
        val limit = Paging.limit(limitRaw)
        val offset = Paging.decode(cursor)
        val rows = repo.listForLearner(userId, limit + 1, offset)
        val page = rows.take(limit)
        val next = if (rows.size > limit) Paging.encode(offset + limit) else null
        return Page(page.map { (e, used, latest) -> toSummary(e, used, latest) }, next)
    }

    fun get(userId: UUID, idRaw: String?): ExamDetail {
        val id = uuid(idRaw)
        val e = visibleExam(id)
        val used = repo.attemptsUsed(id, userId)
        val latest = repo.latestAttempt(id, userId)
        val sections = repo.listSections(id).map { s ->
            val qCount = repo.listQuestions(id).count { it.sectionId == s.id }
            ExamSectionDto(s.id.toString(), s.position, s.title, s.titleBn, s.instructions, s.timeLimitSeconds, qCount)
        }
        return ExamDetail(
            exam = toSummary(e, used, latest),
            instructions = e.instructions,
            instructionsBn = e.instructionsBn,
            requireEmailVerified = e.requireEmailVerified,
            resultsPolicy = e.resultsPolicy,
            resultsVisible = resultsVisible(e),
            passPercent = e.passPercent,
            sections = sections,
            attempt = latest?.let { toAttemptDto(it, resultsVisible(e)) },
        )
    }

    fun start(userId: UUID, idRaw: String?): StartExamResponse {
        val id = uuid(idRaw)
        val e = visibleExam(id)
        val now = clock.instant()
        ensureOpenWindow(e, now)
        if (e.requireEmailVerified && !emailVerified(userId)) {
            throw ApiException(HttpStatusCode.Forbidden, "email_not_verified", "Verify your email before starting this exam.")
        }
        val latest = repo.latestAttempt(id, userId)
        if (latest != null && latest.status == AttemptStatus.IN_PROGRESS) {
            val live = maybeExpire(latest, now) ?: latest
            if (live.status == AttemptStatus.IN_PROGRESS) {
                return resume(e, live)
            }
        }
        val used = repo.attemptsUsed(id, userId)
        if (used >= e.maxAttempts) {
            throw ApiException(HttpStatusCode.Conflict, "max_attempts", "No attempts remaining.")
        }
        val deadline = minOf(now.plusSeconds(e.durationSeconds.toLong()), e.closesAt)
        val attempt = repo.startAttempt(id, userId, used + 1, now, deadline)
        repo.audit(id, attempt.id, userId, "attempt.started", """{"attemptNumber":${attempt.attemptNumber}}""")
        return resume(e, attempt)
    }

    fun saveAnswer(userId: UUID, attemptIdRaw: String?, req: SaveExamAnswerRequest): SaveExamAnswerResponse {
        val attemptId = uuid(attemptIdRaw)
        val attempt = requireOwnedInProgress(userId, attemptId)
        val now = clock.instant()
        val exam = repo.findExam(attempt.examId) ?: notFound()
        val questions = repo.listQuestions(exam.id)
        val qid = uuid(req.questionId)
        if (questions.none { it.questionId == qid }) {
            throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not in this exam.")
        }
        if (req.clientKey != null && req.clientKey.length > 64) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "clientKey is too long.")
        }
        val updated = repo.upsertAnswer(attemptId, qid, req.selected, req.text, req.clientKey, now)
        return SaveExamAnswerResponse(qid.toString(), updated.toString())
    }

    fun submit(userId: UUID, attemptIdRaw: String?): ExamResultDto {
        val attemptId = uuid(attemptIdRaw)
        val attempt = requireOwnedInProgress(userId, attemptId)
        val exam = repo.findExam(attempt.examId) ?: notFound()
        val now = clock.instant()
        return finalize(exam, attempt, now)
    }

    fun result(userId: UUID, attemptIdRaw: String?): ExamResultDto {
        val attemptId = uuid(attemptIdRaw)
        val attempt = repo.findAttempt(attemptId)
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Attempt not found.")
        if (attempt.userId != userId) {
            throw ApiException(HttpStatusCode.NotFound, "not_found", "Attempt not found.")
        }
        val exam = repo.findExam(attempt.examId) ?: notFound()
        val visible = resultsVisible(exam)
        val answers = if (visible && attempt.status == AttemptStatus.SUBMITTED) {
            repo.listAnswers(attempt.id).map {
                ExamAnswerResultDto(it.questionId.toString(), it.result, it.marksAwarded)
            }
        } else emptyList()
        return ExamResultDto(toAttemptDto(attempt, visible), visible, answers)
    }

    fun create(creatorId: UUID, req: CreateExamRequest): ExamSummary {
        if (req.title.isBlank() || req.title.length > 200) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid title.")
        }
        if (req.durationSeconds !in 60..28800) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid duration.")
        }
        if (req.resultsPolicy !in setOf(ResultsPolicy.IMMEDIATE, ResultsPolicy.AFTER_CLOSE, ResultsPolicy.MANUAL)) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid results policy.")
        }
        val opens = parseInstant(req.opensAt, "opensAt")
        val closes = parseInstant(req.closesAt, "closesAt")
        if (!closes.isAfter(opens)) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "closesAt must be after opensAt.")
        }
        if (req.sections.isEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "At least one section is required.")
        }
        val examId = UUID.randomUUID()
        val record = ExamRecord(
            id = examId,
            title = req.title.trim(),
            titleBn = req.titleBn,
            instructions = req.instructions,
            instructionsBn = req.instructionsBn,
            status = ExamStatus.SCHEDULED,
            visibility = "public",
            opensAt = opens,
            closesAt = closes,
            durationSeconds = req.durationSeconds,
            maxAttempts = req.maxAttempts.coerceIn(1, 20),
            requireEmailVerified = req.requireEmailVerified,
            subjectId = req.subjectId?.let { uuid(it) },
            shuffleQuestions = req.shuffleQuestions,
            shuffleOptions = req.shuffleOptions,
            resultsPolicy = req.resultsPolicy,
            resultsPublishedAt = null,
            passPercent = req.passPercent,
            createdBy = creatorId,
        )
        val sections = req.sections.mapIndexed { idx, s ->
            val sid = UUID.randomUUID()
            SectionRecord(sid, examId, idx + 1, s.title.trim(), s.titleBn, s.instructions, s.timeLimitSeconds) to
                s.questionIds.map { uuid(it) }
        }
        val created = repo.createExam(record, sections)
        repo.audit(examId, null, creatorId, "exam.created", """{"sections":${sections.size}}""")
        return toSummary(created, 0, null)
    }

    // ---- internals ----

    private fun resume(e: ExamRecord, attempt: AttemptRecord): StartExamResponse {
        var questions = repo.listQuestions(e.id)
        if (e.shuffleQuestions) {
            questions = questions.sortedBy { (it.questionId.hashCode().toLong() xor attempt.id.leastSignificantBits) }
        }
        val sections = repo.listSections(e.id).map { s ->
            ExamSectionDto(
                s.id.toString(), s.position, s.title, s.titleBn, s.instructions, s.timeLimitSeconds,
                questions.count { it.sectionId == s.id },
            )
        }
        val qDtos = questions.mapIndexed { i, q ->
            val marks = q.marksOverride ?: q.key.marks
            var options = q.options.map { (oid, t) -> ExamOptionDto(oid.toString(), t.first, t.second) }
            if (e.shuffleOptions) {
                options = options.sortedBy { (it.id.hashCode().toLong() xor attempt.id.mostSignificantBits) }
            }
            ExamQuestionDto(
                id = q.questionId.toString(),
                sectionId = q.sectionId.toString(),
                position = i + 1,
                type = q.key.type,
                questionText = q.questionText,
                questionTextBn = q.questionTextBn,
                marks = marks,
                options = options,
            )
        }
        return StartExamResponse(toAttemptDto(attempt, false), sections, qDtos)
    }

    private fun finalize(exam: ExamRecord, attempt: AttemptRecord, now: Instant): ExamResultDto {
        val questions = repo.listQuestions(exam.id)
        val answers = repo.listAnswers(attempt.id).associateBy { it.questionId }
        var total = 0.0
        var max = 0.0
        val graded = questions.map { q ->
            val marks = q.marksOverride ?: q.key.marks
            val written = q.key.type == QuestionType.WRITTEN
            if (!written) max += marks
            val ans = answers[q.questionId]
            val selectedUuids = ans?.selected?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() } ?: emptyList()
            val sub = ans?.let { Submission(selectedUuids, it.textAnswer) }
            val (result, awarded) = when {
                sub == null || (sub.selected.isEmpty() && sub.text.isNullOrBlank()) -> "unanswered" to 0.0
                written -> "ungraded" to 0.0
                else -> {
                    val key = if (q.marksOverride != null) {
                        AnswerKey(
                            q.key.questionId, q.key.lessonId, q.key.type, q.key.version,
                            q.marksOverride, q.key.negativeMarks, q.key.optionIds, q.key.correctOptionIds,
                            q.key.acceptedAnswers, q.key.explanation, q.key.explanationBn, q.key.explanationImages,
                        )
                    } else q.key
                    val g = Grader.grade(key, sub)
                    g.result to g.awardedMarks
                }
            }
            total += awarded
            ExamAnswerRow(q.questionId, ans?.selected ?: emptyList(), ans?.textAnswer, ans?.clientKey, result, awarded)
        }
        val score = round2(maxOf(0.0, total))
        val maxScore = round2(max)
        val percent = if (maxScore > 0) round2(score / maxScore * 100.0) else 0.0
        val passed = exam.passPercent?.let { if (maxScore > 0) percent >= it else null }
        val updated = repo.submitAttempt(
            attempt.id, AttemptStatus.SUBMITTED, now, score, maxScore, percent, passed, graded,
        )
        repo.audit(exam.id, attempt.id, attempt.userId, "attempt.submitted", """{"score":$score,"percent":$percent}""")
        val visible = resultsVisible(exam)
        val perQ = if (visible) {
            graded.map { ExamAnswerResultDto(it.questionId.toString(), it.result, it.marksAwarded) }
        } else emptyList()
        return ExamResultDto(toAttemptDto(updated, visible), visible, perQ)
    }

    private fun requireOwnedInProgress(userId: UUID, attemptId: UUID): AttemptRecord {
        val attempt = repo.findAttempt(attemptId)
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Attempt not found.")
        if (attempt.userId != userId) {
            throw ApiException(HttpStatusCode.NotFound, "not_found", "Attempt not found.")
        }
        val now = clock.instant()
        val live = maybeExpire(attempt, now) ?: attempt
        if (live.status == AttemptStatus.EXPIRED) {
            throw ApiException(HttpStatusCode.Conflict, "attempt_expired", "Your attempt has expired.")
        }
        if (live.status != AttemptStatus.IN_PROGRESS) {
            throw ApiException(HttpStatusCode.Conflict, "not_in_progress", "This attempt is not active.")
        }
        if (!now.isBefore(live.deadlineAt)) {
            maybeExpire(live, now)
            throw ApiException(HttpStatusCode.Conflict, "attempt_expired", "Your attempt has expired.")
        }
        return live
    }

    private fun maybeExpire(attempt: AttemptRecord, now: Instant): AttemptRecord? {
        if (attempt.status != AttemptStatus.IN_PROGRESS) return null
        if (now.isBefore(attempt.deadlineAt)) return null
        val expired = repo.expireIfNeeded(attempt.id, now) ?: return null
        repo.audit(attempt.examId, attempt.id, attempt.userId, "attempt.expired", "{}")
        return expired
    }

    private fun visibleExam(id: UUID): ExamRecord {
        val e = repo.findExam(id) ?: notFound()
        if (e.status == ExamStatus.DRAFT || e.status == ExamStatus.ARCHIVED) notFound()
        val now = clock.instant()
        val derived = when {
            now.isBefore(e.opensAt) -> ExamStatus.SCHEDULED
            !now.isBefore(e.closesAt) -> ExamStatus.CLOSED
            else -> ExamStatus.OPEN
        }
        return e.copy(status = derived)
    }

    private fun ensureOpenWindow(e: ExamRecord, now: Instant) {
        if (now.isBefore(e.opensAt)) {
            throw ApiException(HttpStatusCode.Conflict, "not_open", "This exam is not open yet.")
        }
        if (!now.isBefore(e.closesAt)) {
            throw ApiException(HttpStatusCode.Conflict, "exam_closed", "This exam is closed.")
        }
    }

    private fun resultsVisible(e: ExamRecord): Boolean {
        val now = clock.instant()
        return when (e.resultsPolicy) {
            ResultsPolicy.IMMEDIATE -> true
            ResultsPolicy.AFTER_CLOSE -> !now.isBefore(e.closesAt)
            ResultsPolicy.MANUAL -> e.resultsPublishedAt != null && !now.isBefore(e.resultsPublishedAt)
            else -> false
        }
    }

    private fun toSummary(e: ExamRecord, used: Int, latest: AttemptRecord?) = ExamSummary(
        id = e.id.toString(),
        title = e.title,
        titleBn = e.titleBn,
        status = e.status,
        opensAt = e.opensAt.toString(),
        closesAt = e.closesAt.toString(),
        durationSeconds = e.durationSeconds,
        maxAttempts = e.maxAttempts,
        attemptsUsed = used,
        myStatus = latest?.status,
    )

    private fun toAttemptDto(a: AttemptRecord, includeScores: Boolean) = ExamAttemptDto(
        id = a.id.toString(),
        status = a.status,
        attemptNumber = a.attemptNumber,
        startedAt = a.startedAt.toString(),
        deadlineAt = a.deadlineAt.toString(),
        submittedAt = a.submittedAt?.toString(),
        score = if (includeScores) a.score else null,
        maxScore = if (includeScores) a.maxScore else null,
        percent = if (includeScores) a.percent else null,
        passed = if (includeScores) a.passed else null,
    )

    private fun round2(v: Double) = round(v * 100.0) / 100.0

    private fun parseInstant(raw: String, field: String): Instant =
        runCatching { Instant.parse(raw) }.getOrElse {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid timestamp.", mapOf(field to "Use ISO-8601."))
        }

    private fun uuid(raw: String?): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: notFound()

    private fun notFound(): Nothing =
        throw ApiException(HttpStatusCode.NotFound, "not_found", "Exam not found.")
}

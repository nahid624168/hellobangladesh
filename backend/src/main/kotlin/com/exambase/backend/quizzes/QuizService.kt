package com.exambase.backend.quizzes

import com.exambase.backend.ApiException
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import com.exambase.backend.auth.RateLimiter
import com.exambase.backend.questions.AnswerKey
import com.exambase.backend.questions.CheckAnswerRequest
import com.exambase.backend.questions.Grader
import com.exambase.backend.questions.QuestionRepository
import com.exambase.backend.questions.QuestionType
import com.exambase.backend.questions.Submission
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class QuizService(
    private val repo: QuizRepository,
    private val bank: QuestionRepository,
    private val limiter: RateLimiter,
    private val clock: Clock = Clock.systemUTC(),
    private val random: Random = Random.Default,
) {
    companion object {
        const val MAX_QUESTIONS = 100

        /** Network slack: a submit that arrives up to this long after the deadline is still accepted. */
        val GRACE: Duration = Duration.ofSeconds(10)
    }

    // ---- Quiz sets

    fun create(userId: UUID, req: CreateQuizSetRequest): QuizSetDto {
        val draft = QuizSetDraft(
            title = title(req.title),
            description = description(req.description),
            lessonId = lesson(req.lessonId),
            status = status(req.status ?: QuizStatus.DRAFT),
            settings = settings(req.settings ?: QuizSettingsDto()),
        )
        return repo.create(userId, draft, clock.instant())
    }

    fun list(userId: UUID, q: String?, status: String?, lessonId: String?, sort: String?, limitRaw: String?, cursor: String?): Page<QuizSetListItem> {
        val limit = Paging.limit(limitRaw)
        val offset = Paging.decode(cursor)
        val st = status?.takeIf { it.isNotBlank() }?.also { if (it !in QuizStatus.all) bad("status", "Unknown status.") }
        val so = sort?.takeIf { it.isNotBlank() } ?: QuizSetFilter.SORT_RECENT
        if (so !in QuizSetFilter.sorts) bad("sort", "Unknown sort.")
        val search = q?.trim()?.takeIf { it.isNotEmpty() }
        if (search != null && search.length > 100) bad("q", "At most 100 characters.")
        val lesson = lessonId?.takeIf { it.isNotBlank() }?.let { uuidField("lessonId", it) }
        return repo.list(userId, QuizSetFilter(search, st, lesson, so), limit, offset)
    }

    fun get(userId: UUID, rawId: String?): QuizSetDetail {
        val id = setId(rawId)
        val set = repo.find(userId, id) ?: setNotFound()
        return QuizSetDetail(set, repo.questionIds(id).map { it.toString() })
    }

    fun update(userId: UUID, rawId: String?, req: UpdateQuizSetRequest): QuizSetDto {
        val id = setId(rawId)
        val cur = repo.find(userId, id) ?: setNotFound()
        val draft = QuizSetDraft(
            title = req.title?.let(::title) ?: cur.title,
            description = if (req.description != null) description(req.description) else cur.description,
            lessonId = when {
                req.clearLesson -> null
                req.lessonId != null -> lesson(req.lessonId)
                else -> cur.lessonId?.let(UUID::fromString)
            },
            status = req.status?.let(::status) ?: cur.status,
            settings = req.settings?.let(::settings) ?: cur.settings,
        )
        return repo.update(userId, id, draft, clock.instant()) ?: setNotFound()
    }

    fun delete(userId: UUID, rawId: String?) {
        if (!repo.softDelete(userId, setId(rawId), clock.instant())) setNotFound()
    }

    fun duplicate(userId: UUID, rawId: String?): QuizSetDto {
        val id = setId(rawId)
        val cur = repo.find(userId, id) ?: setNotFound()
        return repo.duplicate(userId, id, "Copy of ${cur.title}".take(120), clock.instant()) ?: setNotFound()
    }

    fun setQuestions(userId: UUID, rawId: String?, req: SetQuestionsRequest): QuizSetDetail {
        val id = setId(rawId)
        repo.find(userId, id) ?: setNotFound()
        val ids = req.questionIds.map { uuidField("questionIds", it) }
        if (ids.size > MAX_QUESTIONS) bad("questionIds", "At most $MAX_QUESTIONS questions.")
        if (ids.toSet().size != ids.size) bad("questionIds", "Do not repeat a question.")
        // getQuestion applies the bank's visibility rule, so draft, archived or hidden questions cannot be added.
        if (ids.any { bank.getQuestion(it) == null }) bad("questionIds", "A question is not available.")
        if (!repo.setQuestions(userId, id, ids, clock.instant())) setNotFound()
        return get(userId, rawId)
    }

    // ---- Attempts

    /** Starts an attempt, or resumes the one already running for this set (same attempt id, same deadline). */
    fun start(userId: UUID, rawSetId: String?): QuizAttemptView {
        val setId = setId(rawSetId)
        rateLimit(userId)
        val now = clock.instant()
        val set = repo.find(userId, setId) ?: setNotFound()

        repo.inProgress(userId, setId)?.let { running ->
            val current = expireIfLate(running, now)
            if (current.status == AttemptStatus.IN_PROGRESS) return view(current, now)
        }
        if (set.status != QuizStatus.PRIVATE) {
            throw conflict("not_playable", "Set this quiz to Private to play it.")
        }
        val max = set.settings.maxAttempts
        if (max != null && repo.finishedAttempts(userId, setId) >= max) {
            throw conflict("attempt_limit_reached", "You have used all $max attempts for this quiz.")
        }
        val ids = repo.questionIds(setId)
        if (ids.isEmpty()) throw conflict("empty_quiz", "Add at least one question first.")
        var play = ids.mapNotNull { bank.getQuestion(it) }.map { QuizPlayQuestion(it.question, it.options, it.images) }
        if (play.isEmpty()) throw conflict("quiz_unavailable", "The questions in this quiz are no longer available.")
        if (set.settings.shuffleQuestions) play = play.shuffled(random)
        if (set.settings.shuffleOptions) {
            play = play.map { if (it.question.type == QuestionType.TRUE_FALSE) it else it.copy(options = it.options.shuffled(random)) }
        }
        val record = repo.startAttempt(
            AttemptDraft(
                userId = userId,
                quizSetId = setId,
                startedAt = now,
                deadlineAt = set.settings.timeLimitSeconds?.let { now.plusSeconds(it.toLong()) },
                passingPercent = set.settings.passingPercent,
                showExplanations = set.settings.showExplanations,
                questions = play,
            ),
        )
        return view(record, now)
    }

    fun getAttempt(userId: UUID, rawId: String?): QuizAttemptView {
        val now = clock.instant()
        val rec = repo.findAttempt(userId, attemptId(rawId)) ?: attemptNotFound()
        return view(expireIfLate(rec, now), now)
    }

    /**
     * Idempotent: submitting an attempt that is already finished returns the stored result and changes nothing.
     * Late submissions (beyond the deadline plus [GRACE]) are rejected with 409 and the attempt is closed as expired with score 0.
     */
    fun submit(userId: UUID, rawId: String?, req: SubmitAttemptRequest): QuizResultDto {
        val id = attemptId(rawId)
        rateLimit(userId)
        val now = clock.instant()
        var rec = repo.findAttempt(userId, id) ?: attemptNotFound()
        if (rec.status == AttemptStatus.IN_PROGRESS) {
            if (isLate(rec, now)) {
                expire(rec, now)
                throw conflict("attempt_expired", "Time is up. This attempt was closed without a score.")
            }
            val keys = repo.answerKeys(questionIds(rec))
            val submissions = validateAnswers(rec, keys, req)
            finish(rec, AttemptStatus.SUBMITTED, keys, submissions, now)
            rec = repo.findAttempt(userId, id) ?: attemptNotFound()
        }
        return result(rec)
    }

    fun attempts(userId: UUID, rawSetId: String?, limitRaw: String?, cursor: String?): Page<QuizAttemptSummaryDto> {
        val setId = setId(rawSetId)
        repo.find(userId, setId) ?: setNotFound()
        return repo.attempts(userId, setId, Paging.limit(limitRaw), Paging.decode(cursor))
    }

    // ---- Internals

    private fun questionIds(rec: AttemptRecord): List<UUID> = rec.questions.map { UUID.fromString(it.question.id) }

    private fun isLate(rec: AttemptRecord, now: Instant): Boolean = rec.deadlineAt != null && now.isAfter(rec.deadlineAt.plus(GRACE))

    private fun expireIfLate(rec: AttemptRecord, now: Instant): AttemptRecord {
        if (rec.status != AttemptStatus.IN_PROGRESS || !isLate(rec, now)) return rec
        expire(rec, now)
        return repo.findAttempt(rec.userId, rec.id) ?: rec
    }

    private fun expire(rec: AttemptRecord, now: Instant) =
        finish(rec, AttemptStatus.EXPIRED, repo.answerKeys(questionIds(rec)), emptyMap(), now)

    private fun finish(rec: AttemptRecord, status: String, keys: Map<UUID, AnswerKey>, subs: Map<UUID, Submission>, now: Instant) {
        val lines = questionIds(rec).mapIndexedNotNull { i, qid -> keys[qid]?.let { QuizScoring.Line(i + 1, it, subs[qid]) } }
        val s = QuizScoring.score(lines, rec.passingPercent)
        // False means a concurrent request finished it first; the caller re-reads the stored attempt.
        repo.finishAttempt(rec.id, AttemptOutcome(status, now, s.score, s.maxScore, s.percent, s.passed, s.rows))
    }

    private fun validateAnswers(rec: AttemptRecord, keys: Map<UUID, AnswerKey>, req: SubmitAttemptRequest): Map<UUID, Submission> {
        val allowed = questionIds(rec).toSet()
        val seen = mutableSetOf<UUID>()
        val out = mutableMapOf<UUID, Submission>()
        req.answers.forEachIndexed { i, a ->
            val f = "answers[$i]"
            val qid = runCatching { UUID.fromString(a.questionId.trim()) }.getOrNull() ?: bad("$f.questionId", "Must be a valid id.")
            if (qid !in allowed) bad("$f.questionId", "This question is not part of the attempt.")
            if (!seen.add(qid)) bad("$f.questionId", "Answered twice.")
            if (a.selectedOptionIds.isEmpty() && a.answerText.isNullOrBlank()) return@forEachIndexed // left blank
            val key = keys[qid] ?: bad("$f.questionId", "This question is no longer available.")
            out[qid] = try {
                Grader.validate(key, CheckAnswerRequest(a.selectedOptionIds, a.answerText))
            } catch (e: ApiException) {
                throw ApiException(e.status, e.code, e.message ?: "Invalid answer.", e.fields?.mapKeys { "$f.${it.key}" })
            }
        }
        return out
    }

    private fun view(rec: AttemptRecord, now: Instant): QuizAttemptView {
        val running = rec.status == AttemptStatus.IN_PROGRESS
        return QuizAttemptView(
            attemptId = rec.id.toString(),
            quizSetId = rec.quizSetId.toString(),
            status = rec.status,
            startedAt = rec.startedAt.toString(),
            deadlineAt = rec.deadlineAt?.toString(),
            serverNow = now.toString(),
            questions = if (running) rec.questions else emptyList(),
            result = if (running) null else result(rec),
        )
    }

    private fun result(rec: AttemptRecord): QuizResultDto {
        val answers = repo.attemptAnswers(rec.id)
        // Correct answers and explanations are read only for a finished attempt, and only if the set allows them.
        val keys = if (rec.showExplanations) repo.answerKeys(answers.map { it.questionId }) else emptyMap()
        val byId = rec.questions.associateBy { it.question.id }
        val rows = answers.sortedBy { it.position }.mapNotNull { a ->
            val q = byId[a.questionId.toString()]?.question ?: return@mapNotNull null
            QuizQuestionResult(
                position = a.position,
                questionId = q.id,
                type = q.type,
                text = q.text,
                textBn = q.textBn,
                result = a.result,
                awardedMarks = a.awardedMarks,
                selectedOptionIds = a.selected.map { it.toString() },
                answerText = a.text,
                review = keys[a.questionId]?.let {
                    QuizReview(
                        it.correctOptionIds.map(UUID::toString).sorted(),
                        if (it.type == QuestionType.SHORT_ANSWER) it.acceptedAnswers else emptyList(),
                        it.explanation, it.explanationBn, it.explanationImages,
                    )
                },
            )
        }
        return QuizResultDto(
            attemptId = rec.id.toString(),
            quizSetId = rec.quizSetId.toString(),
            status = rec.status,
            startedAt = rec.startedAt.toString(),
            submittedAt = rec.submittedAt?.toString(),
            score = rec.score ?: 0.0,
            maxScore = rec.maxScore ?: 0.0,
            percent = rec.percent ?: 0.0,
            passed = rec.passed,
            questions = rows,
        )
    }

    private fun rateLimit(userId: UUID) {
        if (!limiter.tryAcquire("quiz:$userId")) {
            throw ApiException(HttpStatusCode.TooManyRequests, "rate_limited", "Too many requests. Please wait a moment.")
        }
    }

    // ---- Validation

    private fun title(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) bad("title", "Give the quiz a title.")
        if (t.length > 120) bad("title", "At most 120 characters.")
        return t
    }

    private fun description(raw: String?): String? {
        val t = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (t.length > 1000) bad("description", "At most 1000 characters.")
        return t
    }

    private fun status(raw: String): String {
        if (raw !in QuizStatus.all) bad("status", "Choose draft, private or archived.")
        return raw
    }

    private fun lesson(raw: String?): UUID? {
        if (raw.isNullOrBlank()) return null
        val id = uuidField("lessonId", raw)
        if (!repo.lessonVisible(id)) bad("lessonId", "Lesson not found.")
        return id
    }

    private fun settings(s: QuizSettingsDto): QuizSettingsDto {
        s.timeLimitSeconds?.let { if (it !in 30..14400) bad("settings.timeLimitSeconds", "Between 30 seconds and 4 hours.") }
        s.passingPercent?.let { if (it !in 1..100) bad("settings.passingPercent", "Between 1 and 100.") }
        s.maxAttempts?.let { if (it !in 1..100) bad("settings.maxAttempts", "Between 1 and 100.") }
        return s
    }

    private fun uuidField(field: String, raw: String): UUID =
        runCatching { UUID.fromString(raw.trim()) }.getOrNull() ?: bad(field, "Must be a valid id.")

    private fun setId(raw: String?): UUID = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: setNotFound()
    private fun attemptId(raw: String?): UUID = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: attemptNotFound()

    private fun bad(field: String, message: String): Nothing =
        throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid request.", mapOf(field to message))

    private fun conflict(code: String, message: String) = ApiException(HttpStatusCode.Conflict, code, message)
    private fun setNotFound(): Nothing = throw ApiException(HttpStatusCode.NotFound, "not_found", "Quiz set not found.")
    private fun attemptNotFound(): Nothing = throw ApiException(HttpStatusCode.NotFound, "not_found", "Attempt not found.")
}

package com.exambase.backend.contests

import com.exambase.backend.ApiException
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import com.exambase.backend.auth.RateLimiter
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

class ContestService(
    private val repo: ContestRepository,
    private val joinLimiter: RateLimiter = RateLimiter(maxRequests = 30, window = Duration.ofMinutes(1)),
    private val clock: Clock = Clock.systemUTC(),
    /** Optional realtime fan-out (Step 25). Null in unit tests that do not need WS. */
    private val realtime: ((contestId: UUID, name: String, payload: Map<String, String>) -> Unit)? = null,
) {
    private fun emit(contestId: UUID, name: String, payload: Map<String, String> = emptyMap()) {
        runCatching { realtime?.invoke(contestId, name, payload) }
    }

    fun list(userId: UUID, status: String?, limitRaw: String?, cursor: String?): Page<ContestSummary> {
        val limit = Paging.limit(limitRaw)
        val offset = Paging.decode(cursor)
        val rows = repo.listSummaries(userId, status, limit + 1, offset)
        val page = rows.take(limit)
        val next = if (rows.size > limit) Paging.encode(offset + limit) else null
        return Page(page.map { (c, count, p) -> toSummary(c, count, p) }, next)
    }

    fun get(userId: UUID, idRaw: String?): ContestDetail {
        val id = uuid(idRaw)
        val c = visibleContest(id)
        val count = repo.participantCount(id)
        val p = repo.latestParticipant(id, userId)
        val showQuestions = p != null && p.status in setOf(
            ParticipantStatus.IN_PROGRESS, ParticipantStatus.SUBMITTED, ParticipantStatus.EXPIRED,
        ) && (p.status != ParticipantStatus.IN_PROGRESS || resultsVisible(c).not() || true)
        // Questions only after start; answers withheld until results visible
        val questions = if (p != null && p.startedAt != null) {
            loadQuestionsForPlayer(c, p)
        } else emptyList()
        return ContestDetail(
            contest = toSummary(c, count, p),
            joinOpensAt = c.joinOpensAt?.toString(),
            joinClosesAt = c.joinClosesAt?.toString(),
            shuffleQuestions = c.shuffleQuestions,
            showLeaderboard = c.showLeaderboard,
            showResultsAt = c.showResultsAt?.toString(),
            tieBreak = c.tieBreak,
            maxAttempts = c.maxAttempts,
            subjectId = c.subjectId?.toString(),
            questions = questions,
            participant = p?.let { toParticipantDto(it, if (resultsVisible(c)) repo.rankForUser(id, userId, c.tieBreak) else null) },
        )
    }

    fun join(userId: UUID, idRaw: String?, clientKey: String? = null): JoinContestResponse {
        if (!joinLimiter.tryAcquire(userId.toString())) {
            throw ApiException(HttpStatusCode.TooManyRequests, "rate_limited", "Too many join attempts. Try again shortly.")
        }
        val id = uuid(idRaw)
        val c = visibleContest(id)
        val now = clock.instant()
        ensureJoinWindow(c, now)
        if (c.status == ContestStatus.ENDED || c.status == ContestStatus.CANCELLED) {
            throw ApiException(HttpStatusCode.Conflict, "contest_closed", "This contest is no longer open to join.")
        }
        val capacity = c.capacity
        if (capacity != null && repo.participantCount(id) >= capacity) {
            throw ApiException(HttpStatusCode.Conflict, "contest_full", "This contest is full.")
        }
        val latest = repo.latestParticipant(id, userId)
        if (latest != null) {
            if (latest.status == ParticipantStatus.DISQUALIFIED) {
                throw ApiException(HttpStatusCode.Forbidden, "disqualified", "You are disqualified from this contest.")
            }
            if (latest.attemptNumber >= c.maxAttempts && latest.status in setOf(
                    ParticipantStatus.SUBMITTED, ParticipantStatus.EXPIRED, ParticipantStatus.IN_PROGRESS, ParticipantStatus.JOINED,
                )
            ) {
                // Allow re-join only when under max attempts and previous finished
                if (latest.status in setOf(ParticipantStatus.JOINED, ParticipantStatus.IN_PROGRESS)) {
                    return JoinContestResponse(
                        toParticipantDto(latest),
                        toSummary(c, repo.participantCount(id), latest),
                    )
                }
                if (latest.attemptNumber >= c.maxAttempts) {
                    throw ApiException(HttpStatusCode.Conflict, "max_attempts", "No attempts remaining.")
                }
            }
        }
        val attempt = (latest?.attemptNumber ?: 0) + 1
        if (attempt > c.maxAttempts) {
            throw ApiException(HttpStatusCode.Conflict, "max_attempts", "No attempts remaining.")
        }
        val p = repo.join(id, userId, attempt, now)
        emit(
            id,
            "participant.update",
            mapOf(
                "userId" to userId.toString(),
                "status" to p.status,
                "participantCount" to repo.participantCount(id).toString(),
            ),
        )
        return JoinContestResponse(toParticipantDto(p), toSummary(c, repo.participantCount(id), p))
    }

    fun leave(userId: UUID, idRaw: String?) {
        val id = uuid(idRaw)
        val c = visibleContest(id)
        val now = clock.instant()
        if (!now.isBefore(c.startsAt)) {
            throw ApiException(HttpStatusCode.Conflict, "contest_started", "Cannot leave after the contest has started.")
        }
        val p = repo.latestParticipant(id, userId)
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "You have not joined this contest.")
        if (p.status != ParticipantStatus.JOINED) {
            throw ApiException(HttpStatusCode.Conflict, "already_started", "Cannot leave after starting.")
        }
        if (!repo.leave(id, userId)) {
            throw ApiException(HttpStatusCode.NotFound, "not_found", "You have not joined this contest.")
        }
    }

    fun start(userId: UUID, idRaw: String?): StartContestResponse {
        val id = uuid(idRaw)
        val c = visibleContest(id)
        val now = clock.instant()
        if (now.isBefore(c.startsAt)) {
            throw ApiException(HttpStatusCode.Conflict, "not_started", "The contest has not started yet.")
        }
        if (!now.isBefore(c.endsAt) || c.status == ContestStatus.ENDED || c.status == ContestStatus.CANCELLED) {
            throw ApiException(HttpStatusCode.Conflict, "contest_ended", "The contest has ended.")
        }
        val p0 = repo.latestParticipant(id, userId)
            ?: throw ApiException(HttpStatusCode.Conflict, "not_joined", "Join the contest first.")
        if (p0.status == ParticipantStatus.DISQUALIFIED) {
            throw ApiException(HttpStatusCode.Forbidden, "disqualified", "You are disqualified from this contest.")
        }
        if (p0.status == ParticipantStatus.SUBMITTED || p0.status == ParticipantStatus.EXPIRED) {
            throw ApiException(HttpStatusCode.Conflict, "already_finished", "This attempt is already finished.")
        }
        val p = if (p0.status == ParticipantStatus.IN_PROGRESS && p0.startedAt != null) {
            // Resume
            maybeExpire(p0, now) ?: p0
        } else {
            val deadline = minOf(now.plusSeconds(c.durationSeconds.toLong()), c.endsAt)
            repo.start(p0.id, now, deadline)
        }
        if (p.status == ParticipantStatus.EXPIRED) {
            throw ApiException(HttpStatusCode.Conflict, "attempt_expired", "Your attempt has expired.")
        }
        emit(
            id,
            "participant.update",
            mapOf("userId" to userId.toString(), "status" to p.status, "deadlineAt" to (p.deadlineAt?.toString() ?: "")),
        )
        return StartContestResponse(toParticipantDto(p), loadQuestionsForPlayer(c, p))
    }

    fun saveAnswer(userId: UUID, idRaw: String?, req: SaveAnswerRequest): SaveAnswerResponse {
        val id = uuid(idRaw)
        val c = visibleContest(id)
        val now = clock.instant()
        val p = requireInProgress(userId, id, now)
        val qid = uuid(req.questionId)
        val questions = repo.listQuestions(id)
        if (questions.none { it.questionId == qid }) {
            throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not in this contest.")
        }
        if (req.clientKey != null && req.clientKey.length > 64) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "clientKey is too long.", mapOf("clientKey" to "At most 64 characters."))
        }
        val updated = repo.upsertAnswer(p.id, qid, req.selected, req.text, req.clientKey, now)
        return SaveAnswerResponse(qid.toString(), updated.toString())
    }

    fun submit(userId: UUID, idRaw: String?): SubmitContestResponse {
        val id = uuid(idRaw)
        val c = visibleContest(id)
        val now = clock.instant()
        val p = requireInProgress(userId, id, now)
        return finalize(c, p, now, userId)
    }

    fun leaderboard(userId: UUID, idRaw: String?, limitRaw: String?, cursor: String?): LeaderboardPage {
        val id = uuid(idRaw)
        val c = visibleContest(id)
        if (!c.showLeaderboard) {
            throw ApiException(HttpStatusCode.NotFound, "not_found", "Leaderboard is not available.")
        }
        // Live partial leaderboard allowed when showLeaderboard; full ranks after submitters only
        val limit = Paging.limit(limitRaw)
        val offset = Paging.decode(cursor)
        val rows = repo.leaderboard(id, c.tieBreak, limit, offset)
        val total = repo.leaderboardTotal(id)
        val entries = rows.mapIndexed { i, r ->
            LeaderboardEntry(
                rank = offset + i + 1,
                userId = r.userId.toString(),
                displayName = r.displayName,
                score = r.score,
                percent = r.percent,
                submittedAt = r.submittedAt?.toString(),
                timeTakenMs = r.timeTakenMs,
                isMe = r.userId == userId,
            )
        }
        val meRank = repo.rankForUser(id, userId, c.tieBreak)
        val meRow = repo.latestParticipant(id, userId)?.takeIf { it.status == ParticipantStatus.SUBMITTED }
        val me = if (meRank != null && meRow != null) {
            LeaderboardEntry(
                rank = meRank,
                userId = userId.toString(),
                displayName = "You",
                score = meRow.score ?: 0.0,
                percent = meRow.percent ?: 0.0,
                submittedAt = meRow.submittedAt?.toString(),
                timeTakenMs = meRow.timeTakenMs,
                isMe = true,
            )
        } else null
        return LeaderboardPage(id.toString(), entries, me, total)
    }

    /** Admin/test helper: create a scheduled contest with questions. */
    fun create(creatorId: UUID, req: CreateContestRequest): ContestSummary {
        if (req.title.isBlank() || req.title.length > 200) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid title.", mapOf("title" to "1–200 characters."))
        }
        if (req.durationSeconds !in 30..14400) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid duration.", mapOf("durationSeconds" to "30–14400."))
        }
        if (req.tieBreak !in setOf(TieBreak.EARLIEST_SUBMIT, TieBreak.FASTEST_TIME, TieBreak.NONE)) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid tie break.")
        }
        val starts = parseInstant(req.startsAt, "startsAt")
        val ends = parseInstant(req.endsAt, "endsAt")
        if (!ends.isAfter(starts)) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "endsAt must be after startsAt.")
        }
        val status = if (req.status == ContestStatus.DRAFT) ContestStatus.DRAFT else ContestStatus.SCHEDULED
        val qids = req.questionIds.map { uuid(it) }
        if (qids.isEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "At least one question is required.", mapOf("questionIds" to "Required."))
        }
        val record = ContestRecord(
            id = UUID.randomUUID(),
            title = req.title.trim(),
            titleBn = req.titleBn,
            description = req.description,
            descriptionBn = req.descriptionBn,
            status = status,
            visibility = "public",
            startsAt = starts,
            endsAt = ends,
            joinOpensAt = req.joinOpensAt?.let { parseInstant(it, "joinOpensAt") },
            joinClosesAt = req.joinClosesAt?.let { parseInstant(it, "joinClosesAt") },
            capacity = req.capacity,
            durationSeconds = req.durationSeconds,
            shuffleQuestions = req.shuffleQuestions,
            shuffleOptions = req.shuffleOptions,
            showLeaderboard = req.showLeaderboard,
            showResultsAt = req.showResultsAt?.let { parseInstant(it, "showResultsAt") },
            tieBreak = req.tieBreak,
            maxAttempts = req.maxAttempts.coerceIn(1, 10),
            subjectId = req.subjectId?.let { uuid(it) },
            createdBy = creatorId,
        )
        val created = repo.createContest(record, qids)
        return toSummary(created, 0, null)
    }

    // ---- internals ----

    private fun finalize(c: ContestRecord, p: ParticipantRecord, now: Instant, userId: UUID): SubmitContestResponse {
        val questions = repo.listQuestions(c.id)
        val answers = repo.listAnswers(p.id).associateBy { it.questionId }
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
                sub == null || (sub.selected.isEmpty() && sub.text.isNullOrBlank()) ->
                    "unanswered" to 0.0
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
            AnswerRow(q.questionId, ans?.selected ?: emptyList(), ans?.textAnswer, ans?.clientKey, result, awarded)
        }
        val score = round2(maxOf(0.0, total))
        val maxScore = round2(max)
        val percent = if (maxScore > 0) round2(score / maxScore * 100.0) else 0.0
        val started = p.startedAt ?: now
        val timeTaken = Duration.between(started, now).toMillis().coerceAtLeast(0)
        val updated = repo.submit(p.id, ParticipantStatus.SUBMITTED, now, score, maxScore, percent, timeTaken, graded)
        emit(
            c.id,
            "leaderboard.tick",
            mapOf(
                "userId" to userId.toString(),
                "score" to score.toString(),
                "percent" to percent.toString(),
                "submitted" to "true",
            ),
        )
        val board = if (c.showLeaderboard) leaderboard(userId, c.id.toString(), "20", null) else null
        return SubmitContestResponse(toParticipantDto(updated, repo.rankForUser(c.id, userId, c.tieBreak)), board)
    }

    private fun requireInProgress(userId: UUID, contestId: UUID, now: Instant): ParticipantRecord {
        val p0 = repo.latestParticipant(contestId, userId)
            ?: throw ApiException(HttpStatusCode.Conflict, "not_joined", "Join and start the contest first.")
        val p = maybeExpire(p0, now) ?: p0
        if (p.status == ParticipantStatus.EXPIRED) {
            throw ApiException(HttpStatusCode.Conflict, "attempt_expired", "Your attempt has expired.")
        }
        if (p.status != ParticipantStatus.IN_PROGRESS) {
            throw ApiException(HttpStatusCode.Conflict, "not_in_progress", "Start the contest before answering.")
        }
        if (p.deadlineAt != null && !now.isBefore(p.deadlineAt)) {
            maybeExpire(p, now)
            throw ApiException(HttpStatusCode.Conflict, "attempt_expired", "Your attempt has expired.")
        }
        return p
    }

    private fun maybeExpire(p: ParticipantRecord, now: Instant): ParticipantRecord? {
        if (p.status != ParticipantStatus.IN_PROGRESS) return null
        val deadline = p.deadlineAt ?: return null
        if (now.isBefore(deadline)) return null
        return repo.expireIfNeeded(p.id, now)
    }

    private fun loadQuestionsForPlayer(c: ContestRecord, p: ParticipantRecord): List<ContestQuestionDto> {
        var rows = repo.listQuestions(c.id)
        if (c.shuffleQuestions) {
            // Deterministic shuffle per participant so resume is stable
            rows = rows.sortedBy { (it.questionId.hashCode().toLong() xor p.id.leastSignificantBits) }
                .mapIndexed { i, q -> q.copy(position = i + 1) }
        }
        return rows.map { q ->
            val marks = q.marksOverride ?: q.key.marks
            var options = q.options.map { (oid, texts) -> ContestOptionDto(oid.toString(), texts.first, texts.second) }
            if (c.shuffleOptions) {
                options = options.sortedBy { (it.id.hashCode().toLong() xor p.id.mostSignificantBits) }
            }
            ContestQuestionDto(
                id = q.questionId.toString(),
                position = q.position,
                type = q.key.type,
                questionText = q.questionText,
                questionTextBn = q.questionTextBn,
                marks = marks,
                options = options,
            )
        }
    }

    private fun visibleContest(id: UUID): ContestRecord {
        val c = repo.findContest(id) ?: notFound()
        if (c.status == ContestStatus.DRAFT || c.status == ContestStatus.CANCELLED) notFound()
        // Derive live/ended from clock for learners
        val now = clock.instant()
        val derived = when {
            now.isBefore(c.startsAt) -> ContestStatus.SCHEDULED
            !now.isBefore(c.endsAt) -> ContestStatus.ENDED
            else -> ContestStatus.LIVE
        }
        return if (c.status == ContestStatus.SCHEDULED || c.status == ContestStatus.LIVE || c.status == ContestStatus.ENDED) {
            c.copy(status = derived)
        } else c
    }

    private fun ensureJoinWindow(c: ContestRecord, now: Instant) {
        val opens = c.joinOpensAt ?: Instant.EPOCH
        val closes = c.joinClosesAt ?: c.startsAt
        if (now.isBefore(opens) || !now.isBefore(closes)) {
            // Allow join during live if joinClosesAt null and still before ends? Spec: join until starts unless joinClosesAt set later
            if (c.joinClosesAt == null && !now.isBefore(c.startsAt) && now.isBefore(c.endsAt)) {
                // late join during live window allowed when joinClosesAt not set
                return
            }
            if (now.isBefore(opens) || (!now.isBefore(closes) && c.joinClosesAt != null)) {
                throw ApiException(HttpStatusCode.Conflict, "join_closed", "Joining is not open right now.")
            }
        }
    }

    private fun resultsVisible(c: ContestRecord): Boolean {
        val now = clock.instant()
        val at = c.showResultsAt ?: c.endsAt
        return !now.isBefore(at)
    }

    private fun toSummary(c: ContestRecord, count: Int, p: ParticipantRecord?) = ContestSummary(
        id = c.id.toString(),
        title = c.title,
        titleBn = c.titleBn,
        description = c.description,
        status = c.status,
        startsAt = c.startsAt.toString(),
        endsAt = c.endsAt.toString(),
        durationSeconds = c.durationSeconds,
        capacity = c.capacity,
        participantCount = count,
        joined = p != null,
        myStatus = p?.status,
    )

    private fun toParticipantDto(p: ParticipantRecord, rank: Int? = null) = ParticipantDto(
        id = p.id.toString(),
        status = p.status,
        attemptNumber = p.attemptNumber,
        joinedAt = p.joinedAt.toString(),
        startedAt = p.startedAt?.toString(),
        deadlineAt = p.deadlineAt?.toString(),
        submittedAt = p.submittedAt?.toString(),
        score = p.score,
        maxScore = p.maxScore,
        percent = p.percent,
        timeTakenMs = p.timeTakenMs,
        rank = rank,
    )

    private fun round2(v: Double) = round(v * 100.0) / 100.0

    private fun parseInstant(raw: String, field: String): Instant =
        runCatching { Instant.parse(raw) }.getOrElse {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid timestamp.", mapOf(field to "Use ISO-8601."))
        }

    private fun uuid(raw: String?): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: notFound()

    private fun notFound(): Nothing =
        throw ApiException(HttpStatusCode.NotFound, "not_found", "Contest not found.")
}

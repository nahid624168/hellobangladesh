package com.exambase.ai.quizzes

import com.exambase.core.network.ApiResult
import com.exambase.core.network.AttemptStatus
import com.exambase.core.network.QuizAttemptSummary
import com.exambase.core.network.QuizSet
import com.exambase.core.network.QuizSetDetail
import com.exambase.core.network.QuizSettings
import com.exambase.core.network.QuizStatus

/** Why a quiz can or cannot be started right now. The server enforces every one of these again on start. */
enum class Startability { READY, RESUME, DRAFT, ARCHIVED, EMPTY, LIMIT_REACHED }

/** Everything the quiz detail / instructions screen shows. */
data class QuizDetailData(
    val detail: QuizSetDetail,
    /** Newest first, as the server returns them. */
    val attempts: List<QuizAttemptSummary>,
    /** True when the history was longer than we load; counts below are then lower bounds. */
    val attemptsTruncated: Boolean = false,
) {
    val set: QuizSet get() = detail.quizSet
    val running: QuizAttemptSummary? get() = attempts.firstOrNull { it.status == AttemptStatus.IN_PROGRESS }
    val finished: List<QuizAttemptSummary> get() = attempts.filter { it.status != AttemptStatus.IN_PROGRESS }
    val finishedCount: Int get() = finished.size
    val bestPercent: Double? get() = finished.mapNotNull { it.percent }.maxOrNull()
    val startability: Startability get() = QuizRules.startability(set, running != null, finishedCount)
    val attemptsLeft: Int? get() = QuizRules.attemptsLeft(set.settings, finishedCount)
}

object QuizRules {
    /** Order matters: a set that cannot be played is never startable, even with a running attempt left over. */
    fun startability(set: QuizSet, hasRunningAttempt: Boolean, finishedCount: Int): Startability {
        // Copied to a local: the property lives in another module, so the compiler cannot smart-cast it.
        val maxAttempts = set.settings.maxAttempts
        return when {
            set.status == QuizStatus.ARCHIVED -> Startability.ARCHIVED
            set.status != QuizStatus.PRIVATE -> Startability.DRAFT
            set.questionCount <= 0 -> Startability.EMPTY
            hasRunningAttempt -> Startability.RESUME
            maxAttempts != null && finishedCount >= maxAttempts -> Startability.LIMIT_REACHED
            else -> Startability.READY
        }
    }

    /** Finished attempts left before the limit, or null when the set has no limit. Never negative. */
    fun attemptsLeft(settings: QuizSettings, finishedCount: Int): Int? =
        settings.maxAttempts?.let { (it - finishedCount).coerceAtLeast(0) }

    /** "10:00", "1:30:00". Locale-neutral on purpose: digits and colons only. Negative input shows as zero. */
    fun formatClock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, h, m, sec) else "%02d:%02d".format(java.util.Locale.ROOT, m, sec)
    }

    /** "80%" for whole numbers, "66.7%" otherwise (one decimal). */
    fun formatPercent(percent: Double): String {
        val rounded = Math.round(percent * 10.0) / 10.0
        return if (rounded == Math.floor(rounded)) "${rounded.toLong()}%" else "$rounded%"
    }

    /** First ten characters of an ISO timestamp ("2026-11-21", UTC). No time-zone or locale logic on purpose. */
    fun shortDate(iso: String): String = iso.take(10)

    /** Loads at most this many history pages for the detail screen (150 attempts). */
    const val MAX_HISTORY_PAGES = 3
}

/**
 * Loads the quiz set and its attempt history for the detail screen. Fails as a whole when either part fails,
 * so the screen never shows "Start" with a wrong attempt count.
 */
suspend fun QuizSource.loadDetail(id: String): ApiResult<QuizDetailData> {
    val detail = when (val r = quizSet(id)) {
        is ApiResult.Failure -> return r
        is ApiResult.Success -> r.value
    }
    val history = ArrayList<QuizAttemptSummary>()
    var cursor: String? = null
    var pages = 0
    while (pages < QuizRules.MAX_HISTORY_PAGES) {
        when (val r = attempts(id, cursor)) {
            is ApiResult.Failure -> return r
            is ApiResult.Success -> {
                history += r.value.items
                cursor = r.value.nextCursor
            }
        }
        pages++
        if (cursor == null) return ApiResult.Success(QuizDetailData(detail, history.distinctBy { it.attemptId }))
    }
    return ApiResult.Success(QuizDetailData(detail, history.distinctBy { it.attemptId }, attemptsTruncated = true))
}

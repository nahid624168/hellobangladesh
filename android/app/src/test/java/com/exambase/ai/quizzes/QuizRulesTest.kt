package com.exambase.ai.quizzes

import com.exambase.core.network.QuizSet
import com.exambase.core.network.QuizSettings
import com.exambase.core.network.QuizStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizRulesTest {
    private fun set(status: String = QuizStatus.PRIVATE, questions: Int = 5, maxAttempts: Int? = null) =
        QuizSet(id = "s1", title = "Algebra", status = status, questionCount = questions, settings = QuizSettings(maxAttempts = maxAttempts))

    @Test fun privateSetWithQuestionsIsReady() {
        assertEquals(Startability.READY, QuizRules.startability(set(), hasRunningAttempt = false, finishedCount = 0))
    }

    @Test fun draftAndArchivedCannotBeStartedEvenWithARunningAttempt() {
        assertEquals(Startability.DRAFT, QuizRules.startability(set(QuizStatus.DRAFT), false, 0))
        assertEquals(Startability.DRAFT, QuizRules.startability(set(QuizStatus.DRAFT), true, 0))
        assertEquals(Startability.ARCHIVED, QuizRules.startability(set(QuizStatus.ARCHIVED), true, 0))
    }

    @Test fun emptySetCannotBeStarted() {
        assertEquals(Startability.EMPTY, QuizRules.startability(set(questions = 0), false, 0))
    }

    @Test fun runningAttemptResumesEvenWhenTheLimitIsUsedUp() {
        assertEquals(Startability.RESUME, QuizRules.startability(set(maxAttempts = 2), true, 2))
    }

    @Test fun limitIsReachedWhenFinishedAttemptsEqualTheMaximum() {
        assertEquals(Startability.READY, QuizRules.startability(set(maxAttempts = 2), false, 1))
        assertEquals(Startability.LIMIT_REACHED, QuizRules.startability(set(maxAttempts = 2), false, 2))
        assertEquals(Startability.LIMIT_REACHED, QuizRules.startability(set(maxAttempts = 2), false, 3))
    }

    @Test fun attemptsLeftIsNullWithoutALimitAndNeverNegative() {
        assertNull(QuizRules.attemptsLeft(QuizSettings(), 4))
        assertEquals(1, QuizRules.attemptsLeft(QuizSettings(maxAttempts = 3), 2))
        assertEquals(0, QuizRules.attemptsLeft(QuizSettings(maxAttempts = 3), 5))
    }

    @Test fun clockShowsMinutesAndHours() {
        assertEquals("10:00", QuizRules.formatClock(600))
        assertEquals("00:05", QuizRules.formatClock(5))
        assertEquals("1:30:00", QuizRules.formatClock(5400))
        assertEquals("00:00", QuizRules.formatClock(-12))
    }

    @Test fun percentDropsTheDecimalOnWholeNumbers() {
        assertEquals("80%", QuizRules.formatPercent(80.0))
        assertEquals("66.7%", QuizRules.formatPercent(66.666))
        assertEquals("0%", QuizRules.formatPercent(0.0))
        assertEquals("100%", QuizRules.formatPercent(99.96))
    }

    @Test fun shortDateKeepsTheDayOnly() {
        assertEquals("2026-11-21", QuizRules.shortDate("2026-11-21T08:30:00Z"))
        assertEquals("bad", QuizRules.shortDate("bad"))
    }
}

package com.exambase.backend.quizzes

import com.exambase.backend.questions.AnswerKey
import com.exambase.backend.questions.CheckResult
import com.exambase.backend.questions.QuestionType
import com.exambase.backend.questions.Submission
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuizScoringTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    private fun choiceKey(marks: Double = 1.0, neg: Double = 0.0, type: String = QuestionType.SINGLE_CHOICE) =
        AnswerKey(UUID.randomUUID(), UUID.randomUUID(), type, 1, marks, neg, setOf(a, b), setOf(a), emptyList(), "", null, emptyList())

    private fun writtenKey() =
        AnswerKey(UUID.randomUUID(), UUID.randomUUID(), QuestionType.WRITTEN, 1, 5.0, 0.0, emptySet(), emptySet(), emptyList(), "", null, emptyList())

    private fun pick(id: UUID) = Submission(listOf(id), null)

    @Test fun correctWrongAndBlankAreScoredSeparately() {
        val s = QuizScoring.score(
            listOf(
                QuizScoring.Line(1, choiceKey(2.0, 0.5), pick(a)),   // +2
                QuizScoring.Line(2, choiceKey(2.0, 0.5), pick(b)),   // -0.5
                QuizScoring.Line(3, choiceKey(2.0, 0.5), null),      // 0, no penalty
            ),
            passingPercent = null,
        )
        assertEquals(listOf(CheckResult.CORRECT, CheckResult.INCORRECT, AnswerResult.UNANSWERED), s.rows.map { it.result })
        assertEquals(1.5, s.score)
        assertEquals(6.0, s.maxScore)
        assertEquals(25.0, s.percent)
        assertNull(s.passed)
    }

    @Test fun totalNeverGoesBelowZero() {
        val s = QuizScoring.score(listOf(QuizScoring.Line(1, choiceKey(1.0, 2.0), pick(b))), null)
        assertEquals(0.0, s.score)
        assertEquals(0.0, s.percent)
    }

    @Test fun writtenQuestionsAreUngradedAndExcludedFromMax() {
        val s = QuizScoring.score(
            listOf(
                QuizScoring.Line(1, choiceKey(), pick(a)),
                QuizScoring.Line(2, writtenKey(), Submission(emptyList(), "my essay")),
            ),
            passingPercent = 50,
        )
        assertEquals(CheckResult.UNGRADED, s.rows[1].result)
        assertEquals(1.0, s.maxScore)
        assertEquals(100.0, s.percent)
        assertEquals(true, s.passed)
    }

    @Test fun passMarkIsInclusive() {
        val lines = listOf(QuizScoring.Line(1, choiceKey(), pick(a)), QuizScoring.Line(2, choiceKey(), pick(b)))
        assertEquals(true, QuizScoring.score(lines, 50).passed)
        assertEquals(false, QuizScoring.score(lines, 51).passed)
    }

    @Test fun nothingGradableMeansNoPassVerdict() {
        val s = QuizScoring.score(listOf(QuizScoring.Line(1, writtenKey(), null)), 50)
        assertEquals(0.0, s.maxScore)
        assertNull(s.passed)
        assertTrue(s.percent == 0.0)
    }
}

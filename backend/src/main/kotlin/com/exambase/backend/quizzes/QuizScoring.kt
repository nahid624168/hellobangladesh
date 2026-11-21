package com.exambase.backend.quizzes

import com.exambase.backend.questions.AnswerKey
import com.exambase.backend.questions.CheckResult
import com.exambase.backend.questions.Grader
import com.exambase.backend.questions.QuestionType
import com.exambase.backend.questions.Submission

/**
 * Pure scoring for one quiz attempt. Reuses [Grader.grade], so a question is marked the same way in practice and in a quiz.
 *
 * Rules:
 * - Blank answers score 0 and are never penalised.
 * - Written questions are never auto-graded and do not count toward the maximum score.
 * - The total never goes below 0.
 * - percent = score / maxScore * 100 (0 when nothing is gradable); passed is null when there is no passing score or nothing gradable.
 */
object QuizScoring {
    class Line(val position: Int, val key: AnswerKey, val submission: Submission?)

    class Scored(val rows: List<AttemptAnswerRow>, val score: Double, val maxScore: Double, val percent: Double, val passed: Boolean?)

    fun score(lines: List<Line>, passingPercent: Int?): Scored {
        var total = 0.0
        var max = 0.0
        val rows = lines.map { l ->
            val sub = l.submission
            val written = l.key.type == QuestionType.WRITTEN
            if (!written) max += l.key.marks
            val (result, marks) = when {
                sub == null -> AnswerResult.UNANSWERED to 0.0
                written -> CheckResult.UNGRADED to 0.0
                else -> Grader.grade(l.key, sub).let { it.result to it.awardedMarks }
            }
            total += marks
            AttemptAnswerRow(l.position, l.key.questionId, sub?.selected ?: emptyList(), sub?.text, result, marks)
        }
        val score = round2(maxOf(0.0, total))
        val maxScore = round2(max)
        val percent = if (maxScore > 0) round2(score / maxScore * 100.0) else 0.0
        val passed = if (passingPercent != null && maxScore > 0) percent >= passingPercent else null
        return Scored(rows, score, maxScore, percent, passed)
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0 + 0.0
}

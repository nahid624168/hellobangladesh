package com.exambase.backend.exams

import com.exambase.backend.questions.AnswerKey
import java.time.Instant
import java.util.UUID

data class ExamRecord(
    val id: UUID,
    val title: String,
    val titleBn: String?,
    val instructions: String,
    val instructionsBn: String?,
    val status: String,
    val visibility: String,
    val opensAt: Instant,
    val closesAt: Instant,
    val durationSeconds: Int,
    val maxAttempts: Int,
    val requireEmailVerified: Boolean,
    val subjectId: UUID?,
    val shuffleQuestions: Boolean,
    val shuffleOptions: Boolean,
    val resultsPolicy: String,
    val resultsPublishedAt: Instant?,
    val passPercent: Int?,
    val createdBy: UUID?,
)

data class SectionRecord(
    val id: UUID,
    val examId: UUID,
    val position: Int,
    val title: String,
    val titleBn: String?,
    val instructions: String,
    val timeLimitSeconds: Int?,
)

data class SectionQuestionRow(
    val sectionId: UUID,
    val questionId: UUID,
    val position: Int,
    val marksOverride: Double?,
    val key: AnswerKey,
    val questionText: String,
    val questionTextBn: String?,
    val options: List<Pair<UUID, Pair<String, String?>>>,
)

data class AttemptRecord(
    val id: UUID,
    val examId: UUID,
    val userId: UUID,
    val attemptNumber: Int,
    val status: String,
    val startedAt: Instant,
    val deadlineAt: Instant,
    val submittedAt: Instant?,
    val score: Double?,
    val maxScore: Double?,
    val percent: Double?,
    val passed: Boolean?,
)

data class ExamAnswerRow(
    val questionId: UUID,
    val selected: List<String>,
    val textAnswer: String?,
    val clientKey: String?,
    val result: String?,
    val marksAwarded: Double?,
)

interface ExamRepository {
    fun listForLearner(userId: UUID, limit: Int, offset: Int): List<Triple<ExamRecord, Int /*attemptsUsed*/, AttemptRecord?>>
    fun findExam(id: UUID): ExamRecord?
    fun listSections(examId: UUID): List<SectionRecord>
    fun listQuestions(examId: UUID): List<SectionQuestionRow>
    fun attemptsUsed(examId: UUID, userId: UUID): Int
    fun latestAttempt(examId: UUID, userId: UUID): AttemptRecord?
    fun findAttempt(id: UUID): AttemptRecord?

    fun createExam(record: ExamRecord, sections: List<Pair<SectionRecord, List<UUID>>>): ExamRecord

    fun startAttempt(examId: UUID, userId: UUID, attemptNumber: Int, startedAt: Instant, deadlineAt: Instant): AttemptRecord
    fun upsertAnswer(attemptId: UUID, questionId: UUID, selected: List<String>, text: String?, clientKey: String?, now: Instant): Instant
    fun listAnswers(attemptId: UUID): List<ExamAnswerRow>
    fun submitAttempt(
        attemptId: UUID,
        status: String,
        submittedAt: Instant,
        score: Double,
        maxScore: Double,
        percent: Double,
        passed: Boolean?,
        graded: List<ExamAnswerRow>,
    ): AttemptRecord
    fun expireIfNeeded(attemptId: UUID, now: Instant): AttemptRecord?

    fun audit(examId: UUID, attemptId: UUID?, userId: UUID?, eventType: String, detailJson: String)
    fun listAudit(examId: UUID, limit: Int): List<Triple<Long, String, Pair<String, Instant>>>
}

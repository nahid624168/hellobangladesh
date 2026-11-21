package com.exambase.backend.questions

import com.exambase.backend.academic.Page
import java.util.UUID

/**
 * Learner-facing read side of the question bank.
 *
 * Visibility rule (every implementation): a question is visible only when its status is 'published' AND its
 * lesson, subcategory, category and subject are all published. Hidden questions behave exactly like missing ones.
 * Nothing returned here may contain a correct answer, an answer key or an explanation; Step 14 adds a separate
 * "check answer" method for that.
 */
interface QuestionRepository {
    fun listQuestions(filter: QuestionFilter, limit: Int, offset: Int): Page<QuestionSummary>
    fun getQuestion(id: UUID): QuestionDetail?

    /** Tags that have at least one visible question, with the number of visible questions. */
    fun listTags(): List<TagDto>

    // Step 14b
    fun setBookmark(userId: UUID, questionId: UUID, bookmarked: Boolean): Boolean
    fun listBookmarks(userId: UUID, limit: Int, offset: Int): Page<QuestionBookmarkItem>
    fun isBookmarked(userId: UUID, questionId: UUID): Boolean
    fun createReport(userId: UUID, questionId: UUID, reason: String, details: String): QuestionReportDto?
}

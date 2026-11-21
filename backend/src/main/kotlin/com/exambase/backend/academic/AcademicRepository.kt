package com.exambase.backend.academic

import java.util.UUID

/**
 * Read side of the academic hierarchy plus the learner's own bookmarks and progress.
 *
 * Visibility rule (implemented by every implementation): a node is visible only when it AND all of
 * its ancestors are published. Hidden nodes behave exactly like missing ones (null / false).
 * Lists return at most [limit] + 1 rows' worth of information via [Page.nextCursor]; callers pass offset.
 */
interface AcademicRepository {
    fun listSubjects(query: SubjectQuery, limit: Int, offset: Int): Page<SubjectSummary>
    fun getSubject(id: UUID): SubjectDetail?
    fun getCategory(id: UUID): CategoryDetail?
    fun getSubcategory(id: UUID, userId: UUID): SubcategoryDetail?
    fun getLesson(id: UUID, userId: UUID): LessonDetail?

    /** Returns false when the lesson does not exist or is hidden. */
    fun recordView(userId: UUID, lessonId: UUID): Boolean
    fun setBookmark(userId: UUID, lessonId: UUID, bookmarked: Boolean): Boolean
    fun setProgress(userId: UUID, lessonId: UUID, status: String): Boolean

    fun listBookmarks(userId: UUID, limit: Int, offset: Int): Page<LessonSummary>
    fun listRecent(userId: UUID, limit: Int): List<LessonSummary>
}

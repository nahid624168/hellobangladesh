package com.exambase.backend.academic

import java.util.UUID

/** Test double that follows the same visibility and ordering rules as the JDBC implementation. */
class InMemoryAcademicRepository : AcademicRepository {
    class S(val id: UUID, val title: String, var status: String, val featured: Boolean, val order: Int, val description: String, val seq: Int)
    class C(val id: UUID, val subjectId: UUID, val title: String, var status: String, val order: Int)
    class Sc(val id: UUID, val categoryId: UUID, val title: String, var status: String, val order: Int)
    class L(val id: UUID, val subcategoryId: UUID, val title: String, var status: String, val order: Int, val content: String)

    val subjects = mutableListOf<S>()
    val categories = mutableListOf<C>()
    val subcategories = mutableListOf<Sc>()
    val lessons = mutableListOf<L>()
    private val bookmarks = mutableListOf<Pair<UUID, UUID>>()          // oldest first
    private val progress = mutableMapOf<Pair<UUID, UUID>, String>()
    private val viewLog = mutableListOf<Pair<UUID, UUID>>()            // oldest first, no duplicates

    fun subject(title: String, status: String = "published", featured: Boolean = false, order: Int = 0, description: String = "") =
        S(UUID.randomUUID(), title, status, featured, order, description, subjects.size).also { subjects += it }
    fun category(s: S, title: String, status: String = "published", order: Int = 0) =
        C(UUID.randomUUID(), s.id, title, status, order).also { categories += it }
    fun subcategory(c: C, title: String, status: String = "published", order: Int = 0) =
        Sc(UUID.randomUUID(), c.id, title, status, order).also { subcategories += it }
    fun lesson(sc: Sc, title: String, status: String = "published", order: Int = 0, content: String = "Body of $title") =
        L(UUID.randomUUID(), sc.id, title, status, order, content).also { lessons += it }

    private fun sOf(c: C) = subjects.first { it.id == c.subjectId }
    private fun cOf(sc: Sc) = categories.first { it.id == sc.categoryId }
    private fun scOf(l: L) = subcategories.first { it.id == l.subcategoryId }
    private fun visible(l: L): Boolean {
        val sc = scOf(l); val c = cOf(sc); val s = sOf(c)
        return l.status == "published" && sc.status == "published" && c.status == "published" && s.status == "published"
    }
    private fun visibleLessons(sc: Sc) = lessons.filter { it.subcategoryId == sc.id && it.status == "published" }
        .sortedWith(compareBy({ it.order }, { it.title }))

    private fun summary(l: L, user: UUID) = LessonSummary(
        l.id.toString(), l.subcategoryId.toString(), l.title, null, "", null, null,
        progress[user to l.id] ?: ProgressStatus.NOT_STARTED, (user to l.id) in bookmarks,
    )

    private fun subjectSummary(s: S): SubjectSummary {
        val cats = categories.filter { it.subjectId == s.id && it.status == "published" }
        val lessonCount = cats.sumOf { c -> subcategories.filter { it.categoryId == c.id && it.status == "published" }.sumOf { visibleLessons(it).size } }
        return SubjectSummary(s.id.toString(), s.title, null, s.description, null, null, null, s.featured, cats.size, lessonCount)
    }

    override fun listSubjects(query: SubjectQuery, limit: Int, offset: Int): Page<SubjectSummary> {
        var rows = subjects.filter { it.status == "published" }
        if (query.featuredOnly) rows = rows.filter { it.featured }
        query.search?.let { q -> rows = rows.filter { it.title.contains(q, true) || it.description.contains(q, true) } }
        rows = when (query.sort) {
            SubjectQuery.SORT_TITLE -> rows.sortedBy { it.title }
            SubjectQuery.SORT_NEWEST -> rows.sortedByDescending { it.seq }
            else -> rows.sortedWith(compareBy({ !it.featured }, { it.order }, { it.title }))
        }
        return Paging.page(rows.drop(offset).take(limit + 1).map(::subjectSummary), limit, offset)
    }

    override fun getSubject(id: UUID): SubjectDetail? {
        val s = subjects.firstOrNull { it.id == id && it.status == "published" } ?: return null
        val cats = categories.filter { it.subjectId == id && it.status == "published" }.sortedWith(compareBy({ it.order }, { it.title }))
        return SubjectDetail(subjectSummary(s), cats.map(::categorySummary))
    }

    private fun categorySummary(c: C) = CategorySummary(
        c.id.toString(), c.subjectId.toString(), c.title, null, "", null, null,
        subcategories.count { it.categoryId == c.id && it.status == "published" },
    )

    override fun getCategory(id: UUID): CategoryDetail? {
        val c = categories.firstOrNull { it.id == id && it.status == "published" } ?: return null
        val s = sOf(c).takeIf { it.status == "published" } ?: return null
        val subs = subcategories.filter { it.categoryId == id && it.status == "published" }.sortedWith(compareBy({ it.order }, { it.title }))
        return CategoryDetail(
            categorySummary(c), listOf(Crumb("subject", s.id.toString(), s.title)),
            subs.map { SubcategorySummary(it.id.toString(), id.toString(), it.title, null, "", null, visibleLessons(it).size) },
        )
    }

    override fun getSubcategory(id: UUID, userId: UUID): SubcategoryDetail? {
        val sc = subcategories.firstOrNull { it.id == id && it.status == "published" } ?: return null
        val c = cOf(sc).takeIf { it.status == "published" } ?: return null
        val s = sOf(c).takeIf { it.status == "published" } ?: return null
        return SubcategoryDetail(
            SubcategorySummary(sc.id.toString(), c.id.toString(), sc.title, null, "", null, visibleLessons(sc).size),
            listOf(Crumb("subject", s.id.toString(), s.title), Crumb("category", c.id.toString(), c.title)),
            visibleLessons(sc).map { summary(it, userId) },
        )
    }

    override fun getLesson(id: UUID, userId: UUID): LessonDetail? {
        val l = lessons.firstOrNull { it.id == id }?.takeIf(::visible) ?: return null
        val sc = scOf(l); val c = cOf(sc); val s = sOf(c)
        val siblings = visibleLessons(sc).map { it.id }
        val i = siblings.indexOf(l.id)
        return LessonDetail(
            summary(l, userId), l.content, null, listOf("Objective for ${l.title}"), emptyList(),
            listOf(Crumb("subject", s.id.toString(), s.title), Crumb("category", c.id.toString(), c.title), Crumb("subcategory", sc.id.toString(), sc.title)),
            previousLessonId = if (i > 0) siblings[i - 1].toString() else null,
            nextLessonId = siblings.getOrNull(i + 1)?.toString(),
        )
    }

    private fun visibleLesson(id: UUID) = lessons.firstOrNull { it.id == id }?.takeIf(::visible)

    override fun recordView(userId: UUID, lessonId: UUID): Boolean {
        visibleLesson(lessonId) ?: return false
        progress.putIfAbsent(userId to lessonId, ProgressStatus.IN_PROGRESS)
        viewLog.remove(userId to lessonId)
        viewLog += userId to lessonId
        return true
    }

    override fun setBookmark(userId: UUID, lessonId: UUID, bookmarked: Boolean): Boolean {
        visibleLesson(lessonId) ?: return false
        if (bookmarked) { if ((userId to lessonId) !in bookmarks) bookmarks += userId to lessonId } else bookmarks.remove(userId to lessonId)
        return true
    }

    override fun setProgress(userId: UUID, lessonId: UUID, status: String): Boolean {
        visibleLesson(lessonId) ?: return false
        progress[userId to lessonId] = status
        viewLog.remove(userId to lessonId)
        viewLog += userId to lessonId
        return true
    }

    override fun listBookmarks(userId: UUID, limit: Int, offset: Int): Page<LessonSummary> {
        val rows = bookmarks.filter { it.first == userId }.reversed().mapNotNull { visibleLesson(it.second) }
        return Paging.page(rows.drop(offset).take(limit + 1).map { summary(it, userId) }, limit, offset)
    }

    override fun listRecent(userId: UUID, limit: Int): List<LessonSummary> =
        viewLog.filter { it.first == userId }.reversed().mapNotNull { visibleLesson(it.second) }.take(limit).map { summary(it, userId) }
}

package com.exambase.backend.questions

import com.exambase.backend.academic.Crumb
import com.exambase.backend.academic.InMemoryAcademicRepository
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import java.util.UUID

/**
 * Test double that follows the same visibility, filter and ordering rules as JdbcQuestionRepository.
 * It stores correct answers, answer keys and explanations so tests can prove they never reach a response.
 */
class InMemoryQuestionRepository(private val academic: InMemoryAcademicRepository) : QuestionRepository {
    class Opt(val id: UUID, val text: String, val correct: Boolean)
    class Img(val id: UUID, val kind: String, val placement: String, val url: String)
    class Q(
        val id: UUID, val lessonId: UUID, val type: String, val text: String, val difficulty: String,
        var status: String, val order: Int, val seq: Int, val explanation: String, val tags: List<String>,
        val options: List<Opt>, val answerKeys: List<String>, val images: List<Img>, val marks: Double, val negative: Double,
    )

    val questions = mutableListOf<Q>()
    private val tagNames = mutableMapOf<String, String>()
    private val bookmarks = mutableMapOf<UUID, MutableSet<UUID>>() // user -> question ids
    private val reports = mutableListOf<QuestionReportDto>()

    fun tagName(slug: String, name: String) { tagNames[slug] = name }

    @Suppress("LongParameterList")
    fun question(
        lesson: InMemoryAcademicRepository.L, text: String, type: String = QuestionType.SINGLE_CHOICE,
        difficulty: String = Difficulty.MEDIUM, status: String = "published", order: Int = 0,
        explanation: String = "SECRET-EXPLANATION", tags: List<String> = emptyList(),
        options: List<Pair<String, Boolean>> = defaultOptions(type), answerKeys: List<String> = emptyList(),
        images: List<Img> = emptyList(), marks: Double = 1.0, negative: Double = 0.0,
    ) = Q(
        UUID.randomUUID(), lesson.id, type, text, difficulty, status, order, questions.size, explanation, tags,
        options.map { Opt(UUID.randomUUID(), it.first, it.second) }, answerKeys, images, marks, negative,
    ).also { questions += it }

    private fun defaultOptions(type: String): List<Pair<String, Boolean>> = when (type) {
        QuestionType.TRUE_FALSE -> listOf("True" to true, "False" to false)
        QuestionType.SHORT_ANSWER, QuestionType.WRITTEN -> emptyList()
        QuestionType.MULTIPLE_SELECT -> listOf("A" to true, "B" to true, "C" to false)
        else -> listOf("A" to false, "B" to true, "C" to false, "D" to false)
    }

    private fun lessonOf(q: Q) = academic.lessons.first { it.id == q.lessonId }
    private fun subOf(l: InMemoryAcademicRepository.L) = academic.subcategories.first { it.id == l.subcategoryId }
    private fun catOf(sc: InMemoryAcademicRepository.Sc) = academic.categories.first { it.id == sc.categoryId }
    private fun subjectOf(c: InMemoryAcademicRepository.C) = academic.subjects.first { it.id == c.subjectId }

    fun visible(q: Q): Boolean {
        val l = lessonOf(q); val sc = subOf(l); val c = catOf(sc); val s = subjectOf(c)
        return q.status == "published" && l.status == "published" && sc.status == "published" &&
            c.status == "published" && s.status == "published"
    }

    private fun summary(q: Q) = QuestionSummary(
        q.id.toString(), q.lessonId.toString(), q.type, q.text, null, q.difficulty, q.marks, q.negative,
        q.tags.sorted(), q.images.any { it.placement == "question" },
    )

    override fun listQuestions(filter: QuestionFilter, limit: Int, offset: Int): Page<QuestionSummary> {
        var rows = questions.filter(::visible)
        filter.subjectId?.let { id -> rows = rows.filter { subjectOf(catOf(subOf(lessonOf(it)))).id == id } }
        filter.categoryId?.let { id -> rows = rows.filter { catOf(subOf(lessonOf(it))).id == id } }
        filter.subcategoryId?.let { id -> rows = rows.filter { subOf(lessonOf(it)).id == id } }
        filter.lessonId?.let { id -> rows = rows.filter { it.lessonId == id } }
        filter.type?.let { t -> rows = rows.filter { it.type == t } }
        filter.difficulty?.let { d -> rows = rows.filter { it.difficulty == d } }
        filter.tag?.let { t -> rows = rows.filter { t in it.tags } }
        filter.search?.let { s -> rows = rows.filter { it.text.contains(s, ignoreCase = true) } }
        rows = when (filter.sort) {
            QuestionFilter.SORT_NEWEST -> rows.sortedByDescending { it.seq }
            QuestionFilter.SORT_DIFFICULTY -> rows.sortedWith(compareBy({ rank(it.difficulty) }, { it.order }, { it.seq }))
            else -> rows.sortedWith(compareBy({ it.order }, { it.seq }))
        }
        return Paging.page(rows.drop(offset).take(limit + 1).map(::summary), limit, offset)
    }

    private fun rank(d: String) = when (d) { Difficulty.EASY -> 1; Difficulty.MEDIUM -> 2; else -> 3 }

    override fun getQuestion(id: UUID): QuestionDetail? {
        val q = questions.firstOrNull { it.id == id }?.takeIf(::visible) ?: return null
        val l = lessonOf(q); val sc = subOf(l); val c = catOf(sc); val s = subjectOf(c)
        return QuestionDetail(
            question = summary(q),
            options = q.options.map { OptionDto(it.id.toString(), it.text) },
            images = q.images.filter { it.placement == "question" }.map { QuestionImageDto(it.id.toString(), it.kind, it.url) },
            breadcrumb = listOf(
                Crumb("subject", s.id.toString(), s.title), Crumb("category", c.id.toString(), c.title),
                Crumb("subcategory", sc.id.toString(), sc.title), Crumb("lesson", l.id.toString(), l.title),
            ),
            version = 1,
        )
    }

    override fun listTags(): List<TagDto> =
        questions.filter(::visible).flatMap { q -> q.tags.map { it to q.id } }
            .groupBy({ it.first }, { it.second }).toSortedMap()
            .map { (slug, ids) -> TagDto(slug, tagNames[slug] ?: slug, null, ids.distinct().size) }
}

    override fun setBookmark(userId: UUID, questionId: UUID, bookmarked: Boolean): Boolean {
        val q = questions.firstOrNull { it.id == questionId }?.takeIf(::visible) ?: return false
        val set = bookmarks.getOrPut(userId) { mutableSetOf() }
        if (bookmarked) set += questionId else set -= questionId
        return true
    }

    override fun isBookmarked(userId: UUID, questionId: UUID): Boolean =
        bookmarks[userId]?.contains(questionId) == true

    override fun listBookmarks(userId: UUID, limit: Int, offset: Int): Page<QuestionBookmarkItem> {
        val ids = bookmarks[userId]?.toList() ?: emptyList()
        val visible = ids.mapNotNull { id -> questions.firstOrNull { it.id == id }?.takeIf(::visible) }
        val slice = visible.drop(offset).take(limit)
        val items = slice.map { QuestionBookmarkItem(summary(it), "2026-11-21T00:00:00Z") }
        return Page(items, Paging.encode(offset, limit, visible.size))
    }

    override fun createReport(userId: UUID, questionId: UUID, reason: String, details: String): QuestionReportDto? {
        if (questions.none { it.id == questionId && visible(it) }) return null
        val existing = reports.firstOrNull { it.questionId == questionId.toString() && it.status == "open" }
        if (existing != null) return existing
        val dto = QuestionReportDto(
            id = UUID.randomUUID().toString(),
            questionId = questionId.toString(),
            reason = reason,
            details = details,
            status = "open",
            createdAt = "2026-11-21T00:00:00Z",
        )
        reports += dto
        return dto
    }
}

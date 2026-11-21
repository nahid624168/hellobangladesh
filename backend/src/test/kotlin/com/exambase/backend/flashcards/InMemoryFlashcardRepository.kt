package com.exambase.backend.flashcards

import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryFlashcardRepository : FlashcardRepository {
    data class SetRow(
        val id: UUID, val ownerId: UUID, var title: String, var description: String?,
        var lessonId: UUID?, var status: String, var deleted: Boolean = false,
        val createdAt: Instant = Instant.parse("2026-11-21T00:00:00Z"),
        var updatedAt: Instant = Instant.parse("2026-11-21T00:00:00Z"),
    )
    data class CardRow(
        val id: UUID, val setId: UUID, var position: Int,
        var front: String, var frontBn: String?, var back: String, var backBn: String?,
        var frontImg: String?, var backImg: String?, var difficulty: String, var tags: List<String>,
    )

    private val sets = ConcurrentHashMap<UUID, SetRow>()
    private val cards = ConcurrentHashMap<UUID, CardRow>()
    private val srs = ConcurrentHashMap<Pair<UUID, UUID>, SpacedRepetition.State>()
    private val reviews = mutableListOf<ReviewResultDto>()

    private fun toDto(s: SetRow) = FlashcardSetDto(
        s.id.toString(), s.title, s.description, s.lessonId?.toString(), s.status,
        cards.values.count { it.setId == s.id }, s.createdAt.toString(), s.updatedAt.toString(),
    )
    private fun toCard(c: CardRow) = FlashcardDto(
        c.id.toString(), c.setId.toString(), c.position, c.front, c.frontBn, c.back, c.backBn,
        c.frontImg, c.backImg, c.difficulty, c.tags,
    )

    override fun createSet(ownerId: UUID, title: String, description: String?, lessonId: UUID?, status: String): FlashcardSetDto {
        val id = UUID.randomUUID()
        val row = SetRow(id, ownerId, title, description, lessonId, status)
        sets[id] = row
        return toDto(row)
    }

    override fun listSets(ownerId: UUID, q: String?, status: String?, sort: String, limit: Int, offset: Int): Page<FlashcardSetListItem> {
        var list = sets.values.filter { it.ownerId == ownerId && !it.deleted }
        if (status != null) list = list.filter { it.status == status } else list = list.filter { it.status != FlashcardSetStatus.ARCHIVED }
        if (!q.isNullOrBlank()) list = list.filter { it.title.contains(q, true) || (it.description?.contains(q, true) == true) }
        list = when (sort) {
            "title" -> list.sortedBy { it.title }
            "cards" -> list.sortedByDescending { cards.values.count { c -> c.setId == it.id } }
            else -> list.sortedByDescending { it.updatedAt }
        }
        val total = list.size
        val slice = list.drop(offset).take(limit).map { s ->
            val due = cards.values.filter { it.setId == s.id }.count { c ->
                val st = srs[s.ownerId to c.id]
                st == null || !st.dueAt.isAfter(Instant.parse("2026-11-21T12:00:00Z"))
            }
            FlashcardSetListItem(toDto(s), due, null)
        }
        return Page(slice, Paging.encode(offset, limit, total))
    }

    override fun getSet(ownerId: UUID, setId: UUID): FlashcardSetDetail? {
        val s = sets[setId]?.takeIf { it.ownerId == ownerId && !it.deleted } ?: return null
        val cs = cards.values.filter { it.setId == setId }.sortedBy { it.position }.map { toCard(it) }
        return FlashcardSetDetail(toDto(s), cs)
    }

    override fun updateSet(ownerId: UUID, setId: UUID, title: String?, description: String?, lessonId: UUID?, clearLesson: Boolean, status: String?): FlashcardSetDto? {
        val s = sets[setId]?.takeIf { it.ownerId == ownerId && !it.deleted } ?: return null
        title?.let { s.title = it }
        if (description != null) s.description = description
        if (clearLesson) s.lessonId = null else lessonId?.let { s.lessonId = it }
        status?.let { s.status = it }
        s.updatedAt = Instant.now()
        return toDto(s)
    }

    override fun deleteSet(ownerId: UUID, setId: UUID): Boolean {
        val s = sets[setId]?.takeIf { it.ownerId == ownerId && !it.deleted } ?: return false
        s.deleted = true
        return true
    }

    override fun duplicateSet(ownerId: UUID, setId: UUID): FlashcardSetDto? {
        val detail = getSet(ownerId, setId) ?: return null
        val copy = createSet(ownerId, detail.set.title + " (copy)", detail.set.description, detail.set.lessonId?.let(UUID::fromString), FlashcardSetStatus.DRAFT)
        detail.cards.forEach { addCard(ownerId, UUID.fromString(copy.id), CreateFlashcardRequest(it.frontText, it.frontTextBn, it.backText, it.backTextBn, it.frontImageUrl, it.backImageUrl, it.difficulty, it.tags)) }
        return getSet(ownerId, UUID.fromString(copy.id))!!.set
    }

    override fun addCard(ownerId: UUID, setId: UUID, req: CreateFlashcardRequest): FlashcardDto? {
        sets[setId]?.takeIf { it.ownerId == ownerId && !it.deleted } ?: return null
        val pos = cards.values.filter { it.setId == setId }.maxOfOrNull { it.position }?.plus(1) ?: 1
        val id = UUID.randomUUID()
        val row = CardRow(id, setId, pos, req.frontText.trim(), req.frontTextBn, req.backText.trim(), req.backTextBn, req.frontImageUrl, req.backImageUrl, req.difficulty ?: "medium", req.tags)
        cards[id] = row
        return toCard(row)
    }

    override fun updateCard(ownerId: UUID, cardId: UUID, req: UpdateFlashcardRequest): FlashcardDto? {
        val c = cards[cardId] ?: return null
        sets[c.setId]?.takeIf { it.ownerId == ownerId && !it.deleted } ?: return null
        req.frontText?.let { c.front = it.trim() }
        req.backText?.let { c.back = it.trim() }
        req.difficulty?.let { c.difficulty = it }
        req.tags?.let { c.tags = it }
        return toCard(c)
    }

    override fun deleteCard(ownerId: UUID, cardId: UUID): Boolean {
        val c = cards[cardId] ?: return false
        sets[c.setId]?.takeIf { it.ownerId == ownerId && !it.deleted } ?: return false
        cards.remove(cardId)
        return true
    }

    override fun reorderCards(ownerId: UUID, setId: UUID, cardIds: List<UUID>): Boolean {
        sets[setId]?.takeIf { it.ownerId == ownerId && !it.deleted } ?: return false
        cardIds.forEachIndexed { i, id -> cards[id]?.takeIf { it.setId == setId }?.position = i + 1 }
        return true
    }

    override fun dueCards(ownerId: UUID, setId: UUID, limit: Int, now: Instant): List<StudyCardDto> {
        sets[setId]?.takeIf { it.ownerId == ownerId && !it.deleted } ?: return emptyList()
        return cards.values.filter { it.setId == setId }.map { c ->
            val st = srs[ownerId to c.id]
            StudyCardDto(toCard(c), st?.let { SrsStateDto(it.easeFactor, it.intervalDays, it.repetitions, it.dueAt.toString(), it.lastReviewedAt?.toString()) })
        }.filter { it.srs == null || !Instant.parse(it.srs!!.dueAt).isAfter(now) }.take(limit)
    }

    override fun review(ownerId: UUID, cardId: UUID, rating: String, now: Instant): ReviewResultDto? {
        val c = cards[cardId] ?: return null
        sets[c.setId]?.takeIf { it.ownerId == ownerId && !it.deleted } ?: return null
        val cur = srs[ownerId to cardId] ?: SpacedRepetition.initial(now)
        val next = SpacedRepetition.apply(cur, rating, now)
        srs[ownerId to cardId] = next
        val result = ReviewResultDto(cardId.toString(), rating, SrsStateDto(next.easeFactor, next.intervalDays, next.repetitions, next.dueAt.toString(), next.lastReviewedAt?.toString()))
        reviews += result
        return result
    }

    override fun analytics(ownerId: UUID, setId: UUID, now: Instant): SetAnalyticsDto? {
        sets[setId]?.takeIf { it.ownerId == ownerId && !it.deleted } ?: return null
        val cs = cards.values.filter { it.setId == setId }
        val due = dueCards(ownerId, setId, 1000, now).size
        val reviewed = cs.count { srs[ownerId to it.id]?.lastReviewedAt != null }
        val avg = cs.mapNotNull { srs[ownerId to it.id]?.easeFactor }.average().takeIf { !it.isNaN() }
        return SetAnalyticsDto(setId.toString(), cs.size, due, reviewed, avg)
    }
}

package com.exambase.backend.flashcards

import com.exambase.backend.academic.Page
import java.time.Instant
import java.util.UUID

interface FlashcardRepository {
    fun createSet(ownerId: UUID, title: String, description: String?, lessonId: UUID?, status: String): FlashcardSetDto
    fun listSets(ownerId: UUID, q: String?, status: String?, sort: String, limit: Int, offset: Int): Page<FlashcardSetListItem>
    fun getSet(ownerId: UUID, setId: UUID): FlashcardSetDetail?
    fun updateSet(ownerId: UUID, setId: UUID, title: String?, description: String?, lessonId: UUID?, clearLesson: Boolean, status: String?): FlashcardSetDto?
    fun deleteSet(ownerId: UUID, setId: UUID): Boolean
    fun duplicateSet(ownerId: UUID, setId: UUID): FlashcardSetDto?

    fun addCard(ownerId: UUID, setId: UUID, req: CreateFlashcardRequest): FlashcardDto?
    fun updateCard(ownerId: UUID, cardId: UUID, req: UpdateFlashcardRequest): FlashcardDto?
    fun deleteCard(ownerId: UUID, cardId: UUID): Boolean
    fun reorderCards(ownerId: UUID, setId: UUID, cardIds: List<UUID>): Boolean

    fun dueCards(ownerId: UUID, setId: UUID, limit: Int, now: Instant): List<StudyCardDto>
    fun review(ownerId: UUID, cardId: UUID, rating: String, now: Instant): ReviewResultDto?
    fun analytics(ownerId: UUID, setId: UUID, now: Instant): SetAnalyticsDto?
}

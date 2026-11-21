package com.exambase.ai.flashcards

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.ApiResult
import com.exambase.core.network.CreateFlashcardRequest
import com.exambase.core.network.CreateFlashcardSetRequest
import com.exambase.core.network.Flashcard
import com.exambase.core.network.FlashcardApi
import com.exambase.core.network.FlashcardSet
import com.exambase.core.network.FlashcardSetAnalytics
import com.exambase.core.network.FlashcardSetDetail
import com.exambase.core.network.FlashcardSetListItem
import com.exambase.core.network.PageResponse
import com.exambase.core.network.ReviewCardRequest
import com.exambase.core.network.ReviewResult
import com.exambase.core.network.StudyCard
import com.exambase.core.network.UpdateFlashcardRequest
import com.exambase.core.network.UpdateFlashcardSetRequest

interface FlashcardSource {
    suspend fun sets(sort: String, cursor: String?, limit: Int = 50, q: String? = null, status: String? = null): ApiResult<PageResponse<FlashcardSetListItem>>
    suspend fun set(id: String): ApiResult<FlashcardSetDetail>
    suspend fun createSet(req: CreateFlashcardSetRequest): ApiResult<FlashcardSet>
    suspend fun updateSet(id: String, req: UpdateFlashcardSetRequest): ApiResult<FlashcardSet>
    suspend fun deleteSet(id: String): ApiResult<Unit>
    suspend fun duplicateSet(id: String): ApiResult<FlashcardSet>
    suspend fun addCard(setId: String, req: CreateFlashcardRequest): ApiResult<Flashcard>
    suspend fun updateCard(cardId: String, req: UpdateFlashcardRequest): ApiResult<Flashcard>
    suspend fun deleteCard(cardId: String): ApiResult<Unit>
    suspend fun due(setId: String, limit: Int = 20): ApiResult<List<StudyCard>>
    suspend fun review(cardId: String, rating: String): ApiResult<ReviewResult>
    suspend fun analytics(setId: String): ApiResult<FlashcardSetAnalytics>
}

class FlashcardRepository(private val api: FlashcardApi, private val auth: AuthRepository) : FlashcardSource {
    override suspend fun sets(sort: String, cursor: String?, limit: Int, q: String?, status: String?) =
        auth.authed { api.flashcardSets(it, sort, limit, cursor, q, status) }
    override suspend fun set(id: String) = auth.authed { api.flashcardSet(it, id) }
    override suspend fun createSet(req: CreateFlashcardSetRequest) = auth.authed { api.createFlashcardSet(it, req) }
    override suspend fun updateSet(id: String, req: UpdateFlashcardSetRequest) = auth.authed { api.updateFlashcardSet(it, id, req) }
    override suspend fun deleteSet(id: String) = auth.authed { api.deleteFlashcardSet(it, id) }
    override suspend fun duplicateSet(id: String) = auth.authed { api.duplicateFlashcardSet(it, id) }
    override suspend fun addCard(setId: String, req: CreateFlashcardRequest) = auth.authed { api.addFlashcard(it, setId, req) }
    override suspend fun updateCard(cardId: String, req: UpdateFlashcardRequest) = auth.authed { api.updateFlashcard(it, cardId, req) }
    override suspend fun deleteCard(cardId: String) = auth.authed { api.deleteFlashcard(it, cardId) }
    override suspend fun due(setId: String, limit: Int) = auth.authed { api.dueFlashcards(it, setId, limit) }
    override suspend fun review(cardId: String, rating: String) = auth.authed { api.reviewFlashcard(it, cardId, ReviewCardRequest(rating)) }
    override suspend fun analytics(setId: String) = auth.authed { api.flashcardAnalytics(it, setId) }
}

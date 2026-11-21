package com.exambase.ai.ai

import com.exambase.ai.flashcards.FlashcardSource
import com.exambase.core.network.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiGeneratedFlashcardSaverTest {
    @Test fun createsPrivateSetAndSavesCleanUniqueCards() = runBlocking {
        val source = FakeFlashcards()
        val result = AiGeneratedFlashcardSaver(source).save(
            "  Biology  ", null,
            listOf(
                GeneratedFlashcardDraft(" Cell ", " Basic unit "),
                GeneratedFlashcardDraft("cell", "duplicate"),
                GeneratedFlashcardDraft("", "blank"),
            ),
        )
        assertTrue(result is ApiResult.Success)
        assertEquals("Biology", source.createdRequest?.title)
        assertEquals("private", source.createdRequest?.status)
        assertEquals(listOf("Cell"), source.saved.map { it.frontText })
        assertTrue(source.deletedId == null)
    }

    @Test fun rollsBackSetWhenCardSaveFails() = runBlocking {
        val source = FakeFlashcards(failCard = true)
        val result = AiGeneratedFlashcardSaver(source).save(
            "Biology", null, listOf(GeneratedFlashcardDraft("Cell", "Unit")),
        )
        assertTrue(result is ApiResult.Failure)
        assertEquals("set-1", source.deletedId)
    }

    private class FakeFlashcards(private val failCard: Boolean = false) : FlashcardSource {
        var createdRequest: CreateFlashcardSetRequest? = null
        var deletedId: String? = null
        val saved = mutableListOf<CreateFlashcardRequest>()
        private val set = FlashcardSet("set-1", "Test", null, null, "private", 0, "", "")
        override suspend fun sets(sort: String, cursor: String?, limit: Int, q: String?, status: String?) = unsupported<PageResponse<FlashcardSetListItem>>()
        override suspend fun set(id: String) = unsupported<FlashcardSetDetail>()
        override suspend fun createSet(req: CreateFlashcardSetRequest): ApiResult<FlashcardSet> {
            createdRequest = req
            return ApiResult.Success(set)
        }
        override suspend fun updateSet(id: String, req: UpdateFlashcardSetRequest) = unsupported<FlashcardSet>()
        override suspend fun deleteSet(id: String): ApiResult<Unit> { deletedId = id; return ApiResult.Success(Unit) }
        override suspend fun duplicateSet(id: String) = unsupported<FlashcardSet>()
        override suspend fun addCard(setId: String, req: CreateFlashcardRequest): ApiResult<Flashcard> {
            if (failCard) return ApiResult.Failure(ApiFailure(FailureKind.OTHER, "save_failed", "Failed"))
            saved += req
            return ApiResult.Success(Flashcard("card-${saved.size}", setId, saved.size, req.frontText, req.frontTextBn, req.backText, req.backTextBn, req.frontImageUrl, req.backImageUrl, req.difficulty ?: "medium", req.tags))
        }
        override suspend fun updateCard(cardId: String, req: UpdateFlashcardRequest) = unsupported<Flashcard>()
        override suspend fun deleteCard(cardId: String) = ApiResult.Success(Unit)
        override suspend fun due(setId: String, limit: Int) = unsupported<List<StudyCard>>()
        override suspend fun review(cardId: String, rating: String) = unsupported<ReviewResult>()
        override suspend fun analytics(setId: String) = unsupported<FlashcardSetAnalytics>()
        private fun <T> unsupported(): ApiResult<T> = ApiResult.Failure(ApiFailure(FailureKind.OTHER, "unused", "Unused"))
    }
}

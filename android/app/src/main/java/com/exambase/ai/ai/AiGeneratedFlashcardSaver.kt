package com.exambase.ai.ai

import com.exambase.ai.flashcards.FlashcardSource
import com.exambase.core.network.ApiResult
import com.exambase.core.network.CreateFlashcardRequest
import com.exambase.core.network.CreateFlashcardSetRequest
import com.exambase.core.network.FlashcardSet

/**
 * Persists a reviewed AI flashcard draft into the user's separate My Flashcard Sets collection.
 * The caller must pass the user's final edited drafts; this class never publishes to the question bank.
 * If any card fails to save, the newly-created set is deleted to avoid leaving a partial collection.
 */
class AiGeneratedFlashcardSaver(private val flashcards: FlashcardSource) {
    suspend fun save(
        title: String,
        description: String?,
        drafts: List<GeneratedFlashcardDraft>,
    ): ApiResult<FlashcardSet> {
        val cleanTitle = title.trim()
        val cleanDrafts = drafts.map { GeneratedFlashcardDraft(it.front.trim(), it.back.trim()) }
            .filter { it.front.isNotBlank() && it.back.isNotBlank() }
            .distinctBy { it.front.lowercase() }
            .take(10)
        if (cleanTitle.isBlank() || cleanDrafts.isEmpty()) {
            return ApiResult.Failure(
                com.exambase.core.network.ApiFailure(
                    kind = com.exambase.core.network.FailureKind.VALIDATION,
                    code = "invalid_generated_content",
                    message = "Add a title and at least one valid flashcard.",
                ),
            )
        }

        val created = flashcards.createSet(
            CreateFlashcardSetRequest(
                title = cleanTitle,
                description = description?.trim()?.takeIf(String::isNotEmpty),
                status = "private",
            ),
        )
        if (created !is ApiResult.Success) return created

        for (draft in cleanDrafts) {
            val result = flashcards.addCard(
                created.value.id,
                CreateFlashcardRequest(frontText = draft.front, backText = draft.back),
            )
            if (result is ApiResult.Failure) {
                // Best-effort rollback; preserve the original card-save failure for the UI.
                runCatching { flashcards.deleteSet(created.value.id) }
                return result
            }
        }
        return created
    }
}

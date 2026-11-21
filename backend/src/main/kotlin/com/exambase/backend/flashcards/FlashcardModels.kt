package com.exambase.backend.flashcards

import kotlinx.serialization.Serializable

object FlashcardSetStatus {
    const val DRAFT = "draft"
    const val PRIVATE = "private"
    const val ARCHIVED = "archived"
    val all = setOf(DRAFT, PRIVATE, ARCHIVED)
}

object ReviewRating {
    const val AGAIN = "again"
    const val HARD = "hard"
    const val GOOD = "good"
    const val EASY = "easy"
    val all = setOf(AGAIN, HARD, GOOD, EASY)
}

@Serializable
data class FlashcardSettingsDto(
    /** Reserved for future set-level options; always present in JSON. */
    val shuffle: Boolean = false,
)

@Serializable
data class CreateFlashcardSetRequest(
    val title: String,
    val description: String? = null,
    val lessonId: String? = null,
    val status: String? = null,
)

@Serializable
data class UpdateFlashcardSetRequest(
    val title: String? = null,
    val description: String? = null,
    val lessonId: String? = null,
    val clearLesson: Boolean = false,
    val status: String? = null,
)

@Serializable
data class CreateFlashcardRequest(
    val frontText: String,
    val frontTextBn: String? = null,
    val backText: String,
    val backTextBn: String? = null,
    val frontImageUrl: String? = null,
    val backImageUrl: String? = null,
    val difficulty: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class UpdateFlashcardRequest(
    val frontText: String? = null,
    val frontTextBn: String? = null,
    val backText: String? = null,
    val backTextBn: String? = null,
    val frontImageUrl: String? = null,
    val backImageUrl: String? = null,
    val clearFrontImage: Boolean = false,
    val clearBackImage: Boolean = false,
    val difficulty: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class ReorderCardsRequest(val cardIds: List<String> = emptyList())

@Serializable
data class ReviewCardRequest(val rating: String)

@Serializable
data class FlashcardSetDto(
    val id: String,
    val title: String,
    val description: String?,
    val lessonId: String?,
    val status: String,
    val cardCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class FlashcardSetListItem(
    val set: FlashcardSetDto,
    val dueCount: Int,
    val lastReviewedAt: String?,
)

@Serializable
data class FlashcardDto(
    val id: String,
    val setId: String,
    val position: Int,
    val frontText: String,
    val frontTextBn: String?,
    val backText: String,
    val backTextBn: String?,
    val frontImageUrl: String?,
    val backImageUrl: String?,
    val difficulty: String,
    val tags: List<String>,
)

@Serializable
data class FlashcardSetDetail(
    val set: FlashcardSetDto,
    val cards: List<FlashcardDto>,
)

@Serializable
data class SrsStateDto(
    val easeFactor: Double,
    val intervalDays: Int,
    val repetitions: Int,
    val dueAt: String,
    val lastReviewedAt: String?,
)

@Serializable
data class StudyCardDto(
    val card: FlashcardDto,
    val srs: SrsStateDto?,
)

@Serializable
data class ReviewResultDto(
    val cardId: String,
    val rating: String,
    val srs: SrsStateDto,
)

@Serializable
data class SetAnalyticsDto(
    val setId: String,
    val cardCount: Int,
    val dueCount: Int,
    val reviewedCount: Int,
    val averageEase: Double?,
)

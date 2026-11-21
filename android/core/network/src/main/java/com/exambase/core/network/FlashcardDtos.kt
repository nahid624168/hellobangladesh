package com.exambase.core.network

import kotlinx.serialization.Serializable

object FlashcardSetStatus {
    const val DRAFT = "draft"
    const val PRIVATE = "private"
    const val ARCHIVED = "archived"
}

object ReviewRating {
    const val AGAIN = "again"
    const val HARD = "hard"
    const val GOOD = "good"
    const val EASY = "easy"
}

object FlashcardSort {
    const val RECENT = "recent"
    const val TITLE = "title"
    const val CARDS = "cards"
}

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
data class ReviewCardRequest(val rating: String)

@Serializable
data class FlashcardSet(
    val id: String,
    val title: String,
    val description: String? = null,
    val lessonId: String? = null,
    val status: String = FlashcardSetStatus.DRAFT,
    val cardCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class FlashcardSetListItem(
    val set: FlashcardSet,
    val dueCount: Int = 0,
    val lastReviewedAt: String? = null,
)

@Serializable
data class Flashcard(
    val id: String,
    val setId: String,
    val position: Int = 0,
    val frontText: String,
    val frontTextBn: String? = null,
    val backText: String,
    val backTextBn: String? = null,
    val frontImageUrl: String? = null,
    val backImageUrl: String? = null,
    val difficulty: String = "medium",
    val tags: List<String> = emptyList(),
)

@Serializable
data class FlashcardSetDetail(
    val set: FlashcardSet,
    val cards: List<Flashcard> = emptyList(),
)

@Serializable
data class SrsState(
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val dueAt: String = "",
    val lastReviewedAt: String? = null,
)

@Serializable
data class StudyCard(
    val card: Flashcard,
    val srs: SrsState? = null,
)

@Serializable
data class ReviewResult(
    val cardId: String,
    val rating: String,
    val srs: SrsState,
)

@Serializable
data class FlashcardSetAnalytics(
    val setId: String,
    val cardCount: Int = 0,
    val dueCount: Int = 0,
    val reviewedCount: Int = 0,
    val averageEase: Double? = null,
)

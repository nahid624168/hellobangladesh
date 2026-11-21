package com.exambase.backend.search

import kotlinx.serialization.Serializable

object SearchType {
    const val SUBJECT = "subject"
    const val LESSON = "lesson"
    const val QUESTION = "question"
    const val QUIZ = "quiz"
    const val FLASHCARD = "flashcard"
    const val NOTE = "note"
    val all = setOf(SUBJECT, LESSON, QUESTION, QUIZ, FLASHCARD, NOTE)
}

@Serializable
data class SearchHit(
    val type: String,
    val id: String,
    val title: String,
    val titleBn: String? = null,
    val snippet: String? = null,
    /** True when the hit is only visible because the caller owns it. */
    val privateOwned: Boolean = false,
)

@Serializable
data class SearchResponse(
    val query: String,
    val items: List<SearchHit>,
    val nextCursor: String? = null,
    val types: List<String> = emptyList(),
)

@Serializable
data class RecentSearchesResponse(
    val items: List<String>,
)

@Serializable
data class SuggestedSearchesResponse(
    val items: List<String>,
)

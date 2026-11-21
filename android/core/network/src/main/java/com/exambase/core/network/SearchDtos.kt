package com.exambase.core.network

import kotlinx.serialization.Serializable

@Serializable
data class SearchHit(
    val type: String,
    val id: String,
    val title: String,
    val titleBn: String? = null,
    val snippet: String? = null,
    val privateOwned: Boolean = false,
)

@Serializable
data class SearchResponse(
    val query: String,
    val items: List<SearchHit> = emptyList(),
    val nextCursor: String? = null,
    val types: List<String> = emptyList(),
)

@Serializable
data class RecentSearchesResponse(val items: List<String> = emptyList())

@Serializable
data class SuggestedSearchesResponse(val items: List<String> = emptyList())

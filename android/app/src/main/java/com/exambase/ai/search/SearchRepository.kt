package com.exambase.ai.search

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.ApiResult
import com.exambase.core.network.RecentSearchesResponse
import com.exambase.core.network.SearchApi
import com.exambase.core.network.SearchResponse
import com.exambase.core.network.SuggestedSearchesResponse

interface SearchSource {
    suspend fun search(q: String, types: String? = null, cursor: String? = null, limit: Int = 20): ApiResult<SearchResponse>
    suspend fun recent(): ApiResult<RecentSearchesResponse>
    suspend fun suggested(): ApiResult<SuggestedSearchesResponse>
}

class SearchRepository(
    private val api: SearchApi,
    private val auth: AuthRepository,
) : SearchSource {
    override suspend fun search(q: String, types: String?, cursor: String?, limit: Int) =
        auth.authed { api.search(it, q, types, limit, cursor) }

    override suspend fun recent() = auth.authed { api.searchRecent(it) }

    override suspend fun suggested() = auth.authed { api.searchSuggested(it) }
}

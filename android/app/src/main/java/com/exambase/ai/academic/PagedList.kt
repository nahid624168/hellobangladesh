package com.exambase.ai.academic

import com.exambase.core.network.PageResponse

/** Accumulates pages from a cursor-paginated endpoint. Pure, so it is unit-tested without Android. */
data class PagedList<T>(val items: List<T> = emptyList(), val nextCursor: String? = null) {
    val hasMore: Boolean get() = nextCursor != null

    /**
     * Cursors are offset-based on the server, so content changing between requests can repeat a row;
     * [key] lets us drop repeats instead of showing duplicates.
     */
    fun append(page: PageResponse<T>, key: (T) -> String): PagedList<T> {
        val seen = items.mapTo(HashSet(), key)
        return PagedList(items + page.items.filter { seen.add(key(it)) }, page.nextCursor)
    }

    companion object {
        fun <T> first(page: PageResponse<T>) = PagedList(page.items, page.nextCursor)
    }
}

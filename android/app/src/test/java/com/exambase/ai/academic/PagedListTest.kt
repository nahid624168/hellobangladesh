package com.exambase.ai.academic

import com.exambase.core.network.PageResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedListTest {
    private val key: (String) -> String = { it }

    @Test fun firstPageKeepsCursor() {
        val list = PagedList.first(PageResponse(listOf("a", "b"), "c1"))
        assertEquals(listOf("a", "b"), list.items)
        assertTrue(list.hasMore)
    }

    @Test fun appendAddsItemsAndMovesCursor() {
        val list = PagedList.first(PageResponse(listOf("a"), "c1")).append(PageResponse(listOf("b", "c"), null), key)
        assertEquals(listOf("a", "b", "c"), list.items)
        assertNull(list.nextCursor)
        assertFalse(list.hasMore)
    }

    @Test fun appendDropsRowsAlreadyShown() {
        val list = PagedList.first(PageResponse(listOf("a", "b"), "c1")).append(PageResponse(listOf("b", "c"), null), key)
        assertEquals(listOf("a", "b", "c"), list.items)
    }

    @Test fun emptyListHasNoMore() = assertFalse(PagedList<String>().hasMore)
}

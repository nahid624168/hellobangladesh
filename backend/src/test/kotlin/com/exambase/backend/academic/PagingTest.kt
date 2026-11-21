package com.exambase.backend.academic

import com.exambase.backend.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PagingTest {
    @Test fun missingCursorMeansStart() {
        assertEquals(0, Paging.decode(null))
        assertEquals(0, Paging.decode(""))
    }

    @Test fun cursorRoundTrips() = assertEquals(40, Paging.decode(Paging.encode(40)))

    @Test fun malformedCursorsAreRejected() {
        listOf("%%%", java.util.Base64.getUrlEncoder().encodeToString("x".toByteArray()), java.util.Base64.getUrlEncoder().encodeToString("o:abc".toByteArray()), java.util.Base64.getUrlEncoder().encodeToString("o:-5".toByteArray()))
            .forEach { bad -> assertFailsWith<ApiException> { Paging.decode(bad) } }
    }

    @Test fun limitDefaultsAndBounds() {
        assertEquals(20, Paging.limit(null))
        assertEquals(50, Paging.limit("50"))
        assertFailsWith<ApiException> { Paging.limit("0") }
        assertFailsWith<ApiException> { Paging.limit("51") }
        assertFailsWith<ApiException> { Paging.limit("ten") }
    }

    @Test fun pageTrimsTheExtraRowAndSetsCursorOnlyWhenMoreExist() {
        val more = Paging.page(listOf(1, 2, 3), limit = 2, offset = 4)
        assertEquals(listOf(1, 2), more.items)
        assertEquals(6, Paging.decode(more.nextCursor))
        assertNull(Paging.page(listOf(1, 2), limit = 2, offset = 0).nextCursor)
        assertNotNull(Paging.page(listOf(1, 2, 3), limit = 2, offset = 0).nextCursor)
    }

    @Test fun likeWildcardsAreEscaped() = assertEquals("100\\% a\\_b \\\\", escapeLike("100% a_b \\"))
}

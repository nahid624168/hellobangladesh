package com.exambase.backend.academic

import com.exambase.backend.ApiException
import io.ktor.http.HttpStatusCode
import java.util.Base64

/**
 * Cursors are opaque to clients. Today a cursor only wraps an offset, which is fine for catalogue-sized
 * lists. If a list ever becomes large or highly volatile, switch the inside to keyset paging; clients do not change.
 */
object Paging {
    const val DEFAULT_LIMIT = 20
    const val MAX_LIMIT = 50

    fun limit(raw: String?): Int {
        if (raw == null) return DEFAULT_LIMIT
        val n = raw.toIntOrNull() ?: throw invalid("limit", "Limit must be a number.")
        if (n < 1 || n > MAX_LIMIT) throw invalid("limit", "Limit must be between 1 and $MAX_LIMIT.")
        return n
    }

    fun decode(cursor: String?): Int {
        if (cursor.isNullOrEmpty()) return 0
        val text = runCatching { String(Base64.getUrlDecoder().decode(cursor)) }.getOrNull()
        val offset = if (text != null && text.startsWith("o:")) text.substring(2).toIntOrNull() else null
        if (offset == null || offset < 0) throw invalid("cursor", "Invalid cursor.")
        return offset
    }

    fun encode(offset: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString("o:$offset".toByteArray())

    /** Next-page cursor when the total row count is already known; null on the last page. */
    fun encode(offset: Int, limit: Int, total: Int): String? =
        if (offset + limit < total) encode(offset + limit) else null

    /** Fetch limit + 1 rows, then trim: the extra row only proves another page exists. */
    fun <T> page(rowsFetchedWithOneExtra: List<T>, limit: Int, offset: Int): Page<T> {
        val hasMore = rowsFetchedWithOneExtra.size > limit
        return Page(rowsFetchedWithOneExtra.take(limit), if (hasMore) encode(offset + limit) else null)
    }

    private fun invalid(field: String, message: String) =
        ApiException(HttpStatusCode.BadRequest, "invalid_request", message, mapOf(field to message))
}

/** Escapes LIKE wildcards so user text is matched literally. */
fun escapeLike(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

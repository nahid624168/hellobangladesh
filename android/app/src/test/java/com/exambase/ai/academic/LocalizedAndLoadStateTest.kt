package com.exambase.ai.academic

import com.exambase.ai.screens.isSafeWebUrl
import com.exambase.core.network.ApiFailure
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedAndLoadStateTest {
    @Test fun banglaIsUsedOnlyForBanglaDevicesWhenPresent() {
        assertEquals("বাংলা", pickText("Bangla", "বাংলা", "bn"))
        assertEquals("Bangla", pickText("Bangla", "বাংলা", "en"))
        assertEquals("Bangla", pickText("Bangla", null, "bn"))
        assertEquals("Bangla", pickText("Bangla", "  ", "bn"))
    }

    @Test fun successBecomesReady() {
        assertEquals(LoadState.Ready(5), ApiResult.Success(5).toLoadState())
    }

    @Test fun unauthorizedBecomesSignedOut() {
        val failure = ApiResult.Failure(ApiFailure(FailureKind.UNAUTHORIZED, "unauthorized", "Please sign in again."))
        assertEquals(LoadState.SignedOut, failure.toLoadState())
    }

    @Test fun networkFailureIsFlaggedAndHidesTechnicalMessage() {
        val state = ApiResult.Failure(ApiFailure(FailureKind.NETWORK, "network", "No connection")).toLoadState() as LoadState.Error
        assertTrue(state.error.network)
        assertNull(state.error.message)
    }

    @Test fun otherFailureKeepsServerMessage() {
        val state = ApiResult.Failure(ApiFailure(FailureKind.OTHER, "not_found", "Content not found.")).toLoadState() as LoadState.Error
        assertFalse(state.error.network)
        assertEquals("Content not found.", state.error.message)
    }

    @Test fun transformIsAppliedOnSuccessOnly() {
        assertEquals(LoadState.Ready("5"), ApiResult.Success(5).toLoadState { it.toString() })
    }

    @Test fun onlyWebLinksAreOpenable() {
        assertTrue(isSafeWebUrl("https://example.com/a.pdf"))
        assertTrue(isSafeWebUrl("HTTP://example.com"))
        assertFalse(isSafeWebUrl("file:///sdcard/secret"))
        assertFalse(isSafeWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isSafeWebUrl("javascript:alert(1)"))
        assertFalse(isSafeWebUrl("not a url"))
        assertFalse(isSafeWebUrl(""))
    }
}

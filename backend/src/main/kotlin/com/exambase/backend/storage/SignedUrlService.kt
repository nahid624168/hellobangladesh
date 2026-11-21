package com.exambase.backend.storage

import com.exambase.backend.AppConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Produces short-lived HMAC-SHA256 signed URLs for lesson attachments.
 *
 * Storage keys (paths that do not start with http/https) are rewritten to:
 *   {publicBaseUrl}/media/{key}?exp={epochSeconds}&sig={base64url}
 *
 * Absolute http(s) URLs are left unchanged (no signing) so existing public links keep working.
 * When no signing secret is configured the original value is returned unchanged.
 */
class SignedUrlService(private val config: AppConfig) {

    data class Signed(val url: String, val expiresAtMillis: Long?)

    fun sign(rawUrlOrKey: String, ttlMinutes: Long = config.signedUrlTtlMinutes): Signed {
        val secret = config.signedUrlSecret
        if (secret.isNullOrBlank() || isAbsoluteHttp(rawUrlOrKey)) {
            return Signed(rawUrlOrKey, null)
        }
        val expSeconds = (System.currentTimeMillis() / 1000) + (ttlMinutes * 60)
        val payload = "$rawUrlOrKey:$expSeconds"
        val sig = hmacSha256Base64Url(secret, payload)
        val encodedKey = URLEncoder.encode(rawUrlOrKey, StandardCharsets.UTF_8)
        val url = "${config.publicBaseUrl.trimEnd('/')}/media/$encodedKey?exp=$expSeconds&sig=$sig"
        return Signed(url, expSeconds * 1000)
    }

    /** Returns true when the signature is valid and not expired. */
    fun verify(key: String, expSeconds: Long, sig: String): Boolean {
        val secret = config.signedUrlSecret ?: return false
        if (expSeconds < System.currentTimeMillis() / 1000) return false
        val expected = hmacSha256Base64Url(secret, "$key:$expSeconds")
        return constantTimeEquals(expected, sig)
    }

    private fun isAbsoluteHttp(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

    private fun hmacSha256Base64Url(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}

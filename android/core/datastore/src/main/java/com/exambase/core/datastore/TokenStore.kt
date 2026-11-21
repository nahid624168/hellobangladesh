package com.exambase.core.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenStore {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun save(accessToken: String, refreshToken: String)
    fun clear()

    /** Guest sessions only: epoch seconds when the guest time limit ends; null for normal accounts. */
    fun guestExpiresAt(): Long? = null
    fun saveGuestExpiresAt(epochSeconds: Long?) {}
}

/**
 * Stores tokens encrypted with an AES-GCM key that lives in the Android Keystore (never in the APK).
 * If decryption fails (e.g. key lost after a restore) the value is treated as absent and the user signs in again.
 */
class SecureTokenStore(context: Context) : TokenStore {
    private val prefs = context.getSharedPreferences("secure_tokens", Context.MODE_PRIVATE)

    override fun accessToken(): String? = read(KEY_ACCESS)
    override fun refreshToken(): String? = read(KEY_REFRESH)

    override fun save(accessToken: String, refreshToken: String) {
        prefs.edit().putString(KEY_ACCESS, encrypt(accessToken)).putString(KEY_REFRESH, encrypt(refreshToken)).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun guestExpiresAt(): Long? = prefs.getLong(KEY_GUEST_EXPIRES, 0L).takeIf { it > 0L }

    override fun saveGuestExpiresAt(epochSeconds: Long?) {
        prefs.edit().apply { if (epochSeconds == null) remove(KEY_GUEST_EXPIRES) else putLong(KEY_GUEST_EXPIRES, epochSeconds) }.apply()
    }

    private fun read(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        }
        return String(cipher.doFinal(bytes.copyOfRange(GCM_IV_BYTES, bytes.size)))
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "exambase_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEY_GUEST_EXPIRES = "guest_expires_at"
    }
}

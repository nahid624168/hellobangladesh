package com.exambase.ai.offline

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Sync watermark and last-sync timestamp stored with EncryptedSharedPreferences
 * (AES-256 GCM via Android Keystore master key).
 */
class EncryptedSyncPrefs(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "exambase_sync_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var lastSyncAt: String?
        get() = prefs.getString(KEY_LAST_SYNC, null)
        set(value) { prefs.edit().putString(KEY_LAST_SYNC, value).apply() }

    var notesWatermark: String?
        get() = prefs.getString(KEY_NOTES_WM, null)
        set(value) { prefs.edit().putString(KEY_NOTES_WM, value).apply() }

    var lastSyncStatus: String?
        get() = prefs.getString(KEY_STATUS, null)
        set(value) { prefs.edit().putString(KEY_STATUS, value).apply() }

    private companion object {
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_NOTES_WM = "notes_watermark"
        const val KEY_STATUS = "last_sync_status"
    }
}

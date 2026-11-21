package com.exambase.ai.offline

/**
 * Documented conflict policy for offline ↔ server sync (Step 22).
 *
 * ## Notes
 * - Each note carries a monotonic [serverVersion] from the API (`Note.version`).
 * - Local edits bump [localDirty] and store [baseVersion] = version last known from server.
 * - On push: `PATCH` with `baseVersion`. Server responds:
 *   - 200: accept, clear dirty, set version from response
 *   - 409 `version_conflict`: do **not** overwrite local body; mark [ConflictState.NEEDS_USER]
 * - On pull (`/notes/sync`): if local dirty and server version > baseVersion → NEEDS_USER;
 *   if not dirty → apply server snapshot (including tombstones → local deleted flag).
 * - User recovery: Keep mine (force push by omitting baseVersion only after explicit confirm —
 *   default is re-fetch and show both), or Take server (discard local dirty fields).
 *
 * ## Folders
 * Same versioning via `updatedAt` watermark; soft-deleted folders arrive as tombstones.
 *
 * ## Practice / quiz attempts
 * Not cached for offline submit in this step (server-authoritative scoring). Only read caches later.
 *
 * ## Private data
 * Note bodies are stored in Room. The database file is placed in the app sandbox;
 * [EncryptedSyncPrefs] holds the sync watermark with EncryptedSharedPreferences (AES-256 via Android Keystore).
 */
enum class ConflictState {
    NONE,
    NEEDS_USER,
}

enum class ConflictResolution {
    KEEP_LOCAL,
    TAKE_SERVER,
}

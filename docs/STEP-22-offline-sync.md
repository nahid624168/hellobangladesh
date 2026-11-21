# Step 22: Offline mode and synchronization

**Status: PARTIAL (source added; not compiled / not run).**

## Scope
In:
- **Room** schema: `offline_notes`, `offline_folders`, `sync_meta` (`ExamBaseDatabase` v1)
- **Conflict policy** documented in `ConflictPolicy.kt` (version-based; 409 → user resolution)
- **NotesSyncEngine**: push dirty notes with `baseVersion`, pull `/notes/sync` watermark, mark conflicts
- **WorkManager** `NotesSyncWorker` with network constraint + exponential backoff
- **EncryptedSharedPreferences** for last-sync time, watermark, status (`EncryptedSyncPrefs`)
- Notes list: Sync button + status line (last sync, conflict count)
- Unit tests for conflict decision table
- Dependencies: Room, KSP, WorkManager, security-crypto

Out / later:
- Full offline editor write-through to Room on every keystroke (editor still hits network autosave first)
- Offline quiz/practice attempt queues
- SQLCipher full-DB encryption (sandbox + encrypted prefs for watermark in this step)
- Periodic periodic WorkManager schedule (one-shot unique work on demand)

## Conflict recovery
1. Keep local: re-read server version, push local body with that baseVersion
2. Take server: replace local entity with server snapshot, clear dirty/conflict

## Acceptance criteria
1. Dirty local + newer server version → conflict, not silent overwrite.
2. Sync worker retries on hard failure with backoff.
3. Watermark stored encrypted; last-sync visible in UI.
4. Decision table unit tests pass when compiled.

## Not verified
Nothing compiled or run (no Android SDK / KSP execution here).

## Run it
```bash
cd android && ./gradlew :app:testDebugUnitTest
# My Notes -> Sync; edit offline scenario requires device.
```
